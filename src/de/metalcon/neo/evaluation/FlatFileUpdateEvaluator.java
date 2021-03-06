/**
 * this file simulates the behaviour of a fully redundant news stream for each user saved in a flat file 
 * with simulate is meant that in this case for comparison reasons the flat file is saved to the graph data base which usually would not make sense 
 *
 * @author Jonas Kunze, Rene Pickhardt
 * 
 */

package de.metalcon.neo.evaluation;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.LinkedList;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.NotFoundException;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.ReturnableEvaluator;
import org.neo4j.graphdb.StopEvaluator;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.TraversalPosition;
import org.neo4j.graphdb.Traverser;
import org.neo4j.graphdb.Traverser.Order;
import org.neo4j.kernel.AbstractGraphDatabase;

import de.metalcon.neo.evaluation.neo.NeoUtils;
import de.metalcon.neo.evaluation.neo.Relations;
import de.metalcon.neo.evaluation.utils.Configs;
import de.metalcon.neo.evaluation.utils.CopyDirectory;
import de.metalcon.neo.evaluation.utils.H;
import de.metalcon.neo.evaluation.utils.StopWatch;

public class FlatFileUpdateEvaluator {

	public long[] timestamps;
	public String FFDBDirPrefix;

	public FlatFileUpdateEvaluator() {
		FFDBDirPrefix = Configs.get().FFDBDirPrefix;
	}

	public void run() {
		if (Configs.get().FlatFileUpdateEvaluatorInsertUpdates) {
			InsertUpdates();
		}

		if (Configs.get().FlatFileUpdateEvaluatorEvaluate) {
			Evaluate();
		}
	}

	class InsertUpdates extends Thread {
		private AbstractGraphDatabase ff;
		private long ts;

		public int lineCount;
		public int nodeCnt;
		public int notFound = 0;

		public InsertUpdates(AbstractGraphDatabase ff, long ts) {
			this.ff = ff;
			this.ts = ts;
			lineCount = 0;
			nodeCnt = 0;
		}

		@Override
		public void run() {

			BufferedReader in;
			if (Configs.get().MetalconRun) {
				in = H.openReadFile(Configs.get().MetalconUpdatesSorted);
			} else {
				in = H.openReadFile(Configs.get().CleanWikiSnapshotUpdatePrefix
						+ ts);
			}

			String strLine;
			String[] values;

			try {
				Transaction tx = ff.beginTx();
				try {
					while ((strLine = in.readLine()) != null) {
						if (lineCount % 1000 == 0) {
							tx.success();
							tx.finish();
							tx = ff.beginTx();
						}
						values = strLine.split("\t");
						lineCount++;
						if (values.length != 2)
							continue;
						try {
							Node n = ff.getNodeById(Long.parseLong(values[1]));
							InputSingleUpdate(n, Long.parseLong(values[0]));
						} catch (NotFoundException e) {
							notFound++;
							continue;
						}
					}
					tx.success();
				} finally {
					tx.finish();
				}
			} catch (NumberFormatException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		private void InputSingleUpdate(Node n, long timestamp) {
			String owner = "" + n.getId();
			for (Relationship rel : n.getRelationships(Direction.INCOMING,
					Relations.FOLLOWS)) {
				Node otherNode = rel.getStartNode();
				InsertContentItem(otherNode, timestamp, owner);
			}
		}

		private void InsertContentItem(Node node, long timestamp, String owner) {
			nodeCnt++;
			Node ci = ff.createNode();
			ci.setProperty("timestamp", timestamp);
			ci.setProperty("owner", owner);
			Relationship rel = node.getSingleRelationship(
					Relations.FRIENDUPDATE, Direction.OUTGOING);
			if (rel != null) {
				Node tmp = rel.getEndNode();
				rel.delete();
				node.createRelationshipTo(ci, Relations.FRIENDUPDATE);
				ci.createRelationshipTo(tmp, Relations.FRIENDUPDATE);
			} else {
				node.createRelationshipTo(ci, Relations.FRIENDUPDATE);
			}
		}
	};

	private void InsertUpdates() {
		for (long ts : Configs.get().StarSnapshotTimestamps) {

			String dbPath;
			if (!Configs.get().MetalconRun) {
				dbPath = Configs.get().FFDBDirPrefix + ts;
				H.deleteDirectory(dbPath);
				new CopyDirectory(Configs.get().CleanFriendDBPrefix + ts,
						dbPath);
			} else {
				dbPath = Configs.get().MetalconDB + "-ff";
				H.deleteDirectory(dbPath);
				new CopyDirectory(Configs.get().MetalconDB, dbPath);
			}

			AbstractGraphDatabase ff = NeoUtils
					.getAbstractGraphDatabase(dbPath);
			InsertUpdates iu = new InsertUpdates(ff, ts);
			iu.start();
			try {

				StopWatch watch = new StopWatch();
				while (iu.isAlive()) {
					Thread.sleep(2000);
					watch.updateRate(iu.lineCount);
					H.log("FF-Evaluating " + ts + " Added " + iu.lineCount
							+ " lines and " + iu.nodeCnt + " nodes ("
							+ iu.nodeCnt / iu.lineCount + ") so far: "
							+ iu.notFound + " not found; " + watch.getRate(10)
							+ "ps : " + watch.getTotalRate() + "ps total");

				}
				H.strongLog("FF-Evaluating " + ts + " Added " + iu.lineCount
						+ " lines and " + iu.nodeCnt + " nodes (" + iu.nodeCnt
						/ iu.lineCount + ") so far: " + watch.getTotalRate()
						+ "ps total");
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			NeoUtils.ShutdownDB(ff, dbPath);
		}
	}

	class Evaluate extends Thread {
		public int lineCount;
		public int nodeCnt;
		private LinkedList<Node> allNodes;

		public Evaluate(AbstractGraphDatabase ff, long ts) {
			lineCount = 0;
			nodeCnt = 0;

			LinkedList<Long> allIds;
			allIds = NeoUtils.GetAllNodeIds(ts);
			allNodes = NeoUtils.GetAllNodes(ff, allIds);
		}

		public void run() {
			for (int i = 0; i < Configs.get().SampleRepeatRuns; i++) {
				for (Node n : allNodes) {
					GenerateStream(n, Configs.get().k);
					lineCount++;

				}
			}
		}

		private void GenerateStream(Node n, final int k) {

			Traverser t = n.traverse(Order.DEPTH_FIRST, new StopEvaluator() {

				@Override
				public boolean isStopNode(TraversalPosition position) {
					if (position.depth() > k) {
						return true;
					} else {
						return false;
					}
				}
			}, ReturnableEvaluator.ALL_BUT_START_NODE, Relations.FRIENDUPDATE,
					Direction.OUTGOING);
			String stream = "";
			for (Node ci : t) {
				nodeCnt++;
				stream = "at " + (Long) ci.getProperty("timestamp") + " by "
						+ (String) ci.getProperty("owner");
			}
			H.pln(stream);
		}

	};

	private void Evaluate() {
		for (long ts : Configs.get().StarSnapshotTimestamps) {

			String dbPath;
			if (!Configs.get().MetalconRun) {
				dbPath = Configs.get().FFDBDirPrefix + ts;
			} else {
				dbPath = Configs.get().MetalconDB + "-ff";
			}

			AbstractGraphDatabase ff = NeoUtils
					.getAbstractGraphDatabase(dbPath);

			Evaluate e = new Evaluate(ff, ts);
			StopWatch watch = new StopWatch();
			e.start();
			try {
				while (e.isAlive()) {
					Thread.sleep(2000);
					watch.updateRate(e.lineCount);
					H.log("FF-Evaluating " + ts + " Created " + e.lineCount
							+ " streams and processed " + e.nodeCnt
							+ " nodes (" + ((float) e.nodeCnt) / e.lineCount
							+ " average nodes per stream) so far: "
							+ watch.getRate(10) + "ps : "
							+ watch.getTotalRate() + "ps total");

				}
				H.strongLog("FF-Evaluating " + ts + " finished creating "
						+ e.lineCount + " streams and processed " + e.nodeCnt
						+ " nodes (" + ((float) e.nodeCnt) / e.lineCount
						+ " average nodes per stream): " + watch.getTotalRate()
						+ "ps total");
			} catch (InterruptedException ex) {
				ex.printStackTrace();
			}

			NeoUtils.ShutdownDB(ff, dbPath);
		}
	}
}
