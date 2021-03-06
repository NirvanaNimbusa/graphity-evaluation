/**
 * some public helper functions that should help to write shorter code 
 *
 * @author Jonas Kunze, Rene Pickhardt
 * 
 */

package de.metalcon.neo.evaluation.neo;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.channels.NonWritableChannelException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeSet;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.NotFoundException;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.kernel.AbstractGraphDatabase;
import org.neo4j.kernel.EmbeddedGraphDatabase;
import org.neo4j.kernel.EmbeddedReadOnlyGraphDatabase;
import org.neo4j.kernel.impl.batchinsert.BatchInserter;
import org.neo4j.kernel.impl.batchinsert.BatchInserterImpl;

import de.metalcon.neo.evaluation.utils.Configs;
import de.metalcon.neo.evaluation.utils.CopyDirectory;
import de.metalcon.neo.evaluation.utils.H;

public class NeoUtils {

	private static boolean needShutDown = false;

	public static BatchInserter getBatchInserter(String DbPath) {
		return new BatchInserterImpl(DbPath,
				BatchInserterImpl.loadProperties(DbPath
						+ "/neostore.propertystore.db"));
	}

	public static AbstractGraphDatabase getAbstractGraphDatabase(String DbPath) {
		return getAbstractGraphDatabase(DbPath, false);
	}

	private static void WarmUpCache(AbstractGraphDatabase db) {
		long l = 0;
		H.strongLog("Starting DB-Warmup");
		for (Node n : db.getAllNodes()) {
			for (Relationship rel : n.getRelationships(Relations.FOLLOWS)) {
				l++;
				if (l % 100000 == 0) {
					H.log("Warmup: " + l + " so far");
				}
			}
		}
		H.strongLog(" Warmup-Grade: " + l);
	}

	public static AbstractGraphDatabase getAbstractGraphDatabase(String DbPath,
			boolean readOnly) {
		if (!readOnly && needShutDown) {
			throw new Error("Don't forget to shutdown your DBs!");
		}

		final Map<String, String> graphConfig;
		graphConfig = new HashMap<String, String>();
		graphConfig.put("cache_type", Configs.get().cache_type);
		graphConfig.put("use_memory_mapped_buffers",
				Configs.get().use_memory_mapped_buffers);

		if (Configs.get().RunOnMemory && !readOnly) {
			String tmpDBPath = Configs.get().MemDir + "/tmpDB";

			File tmpFile = new File(tmpDBPath);
			deleteDir(tmpFile);

			new CopyDirectory(DbPath, tmpDBPath);
			DbPath = tmpDBPath;
		}

		H.pln("Opening DB" + DbPath);
		if (!readOnly) {
			needShutDown = true;
		}
		AbstractGraphDatabase db;
		if (readOnly) {
			db = new EmbeddedReadOnlyGraphDatabase(DbPath, graphConfig);
		} else {
			db = new EmbeddedGraphDatabase(DbPath, graphConfig);
		}

		if (Configs.get().WarmumpDB) {
			WarmUpCache(db);
		}
		return db;
	}

	public static boolean deleteDir(File dir) {
		if (dir.isDirectory()) {
			String[] children = dir.list();
			for (int i = 0; i < children.length; i++) {
				boolean success = deleteDir(new File(dir, children[i]));
				if (!success) {
					return false;
				}
			}
		}

		// The directory is now empty so delete it
		return dir.delete();
	}

	public static void ShutdownDB(AbstractGraphDatabase db, String DbPath,
			boolean copyBack) {
		needShutDown = false;
		db.shutdown();
		if (Configs.get().RunOnMemory
				&& !(db instanceof EmbeddedReadOnlyGraphDatabase) && copyBack) {
			H.pln("Copying DB from" + db.getStoreDir() + " to " + DbPath);
			new CopyDirectory(db.getStoreDir(), DbPath);
		}
	}

	public static void ShutdownDB(AbstractGraphDatabase db, String DbPath) {
		ShutdownDB(db, DbPath, true);
	}

	public static LinkedList<String[]> GetSimulateEvents(int k, long timestamp) {
		LinkedList<String[]> allIDs = new LinkedList<String[]>();

		try {
			BufferedReader in = H
					.openReadFile(Configs.get().SimulateEventsPrefix
							+ timestamp);
			String strLine;
			String[] values;
			int lineCount = 0;
			while ((strLine = in.readLine()) != null && lineCount < k) {
				values = strLine.split("\t");
				if (values.length < 3 || values.length > 4)
					continue;
				allIDs.add(values);
				if (lineCount++ % 5000 == 0) {
					H.pln("Reading transactions: " + lineCount + " done so far");
				}
			}

			in.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return allIDs;
	}

	private static HashMap<Long, LinkedList<Long>> allIDsCache = new HashMap<Long, LinkedList<Long>>();

	public static LinkedList<Long> GetAllNodeIds(long timestamp) {
		if (allIDsCache.containsKey(timestamp)) {
			return allIDsCache.get(timestamp);
		}
		LinkedList<Long> allIDs = new LinkedList<Long>();

		try {
			BufferedReader in;
			if (Configs.get().MetalconRun) {
				in = H.openReadFile(Configs.get().MetalconFullIDList);
			} else {
				if (!Configs.get().IgnoreSmallDegreeNodes) {
					in = H.openReadFile(Configs.get().SnapshotIDListPrefix
							+ timestamp);
				} else {
					in = H.openReadFile(Configs.get().LargeDegreeNodesPrefix
							+ timestamp);
				}
			}

			String line;
			String[] values;
			int lineCount = 0;
			while ((line = in.readLine()) != null) {
				values = line.split("\t");
				allIDs.add(Long.valueOf(values[0]));
				if (lineCount++ % 500000 == 0) {
					H.pln("Reading all IDs: " + lineCount + " done so far");
				}
			}

			in.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		allIDsCache.put(timestamp, allIDs);
		return allIDs;
	}

	public static LinkedList<Long> GetAllNodeIds(int startDegree) {
		LinkedList<Long> allIDs = new LinkedList<Long>();

		try {
			BufferedReader in = H.openReadFile(Configs.get().SamplePrefix
					+ Configs.get().SampleTimestamp + "_" + startDegree);
			String strLine;
			int lineCount = 0;
			while ((strLine = in.readLine()) != null) {
				allIDs.add(Long.valueOf(strLine));
				if (lineCount++ % 500000 == 0) {
					H.pln("Reading all IDs: " + lineCount + " done so far");
				}
			}

			in.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return allIDs;
	}

	public static LinkedList<Node> GetAllNodes(AbstractGraphDatabase db,
			LinkedList<Long> allIds) {
		LinkedList<Node> allNodes = new LinkedList<Node>();

		H.log("creating linked list of nodes to by processed");
		int lineCount = 0;
		for (long id : allIds) {
			try {
				Node n = db.getNodeById(id);
				allNodes.add(n);
				if (lineCount++ % 500000 == 0) {
					H.pln("pushing nodes to linked list: " + lineCount
							+ " done so far");
				}

			} catch (NotFoundException e) {
				e.printStackTrace();
				continue;
			}
		}
		H.log("finnished to create linked list of nodes");

		return allNodes;
	}

	/**
	 * 
	 * @param node
	 * @param relationshipType
	 * @return
	 */
	public static Node getNextSingleNode(Node node,
			RelationshipType relationshipType) {
		Relationship rel = null;
		try {
			rel = node.getSingleRelationship(relationshipType,
					Direction.OUTGOING);
		} catch (NonWritableChannelException e) {
		}
		if (rel == null)
			return null;
		return rel.getEndNode();
	}

	/**
	 * 
	 * @param node
	 * @param relationshipType
	 * @return
	 */
	public static Node getPrevSingleNode(Node node,
			RelationshipType relationshipType) {
		Relationship rel = node.getSingleRelationship(relationshipType,
				Direction.INCOMING);
		if (rel == null)
			return null;
		return rel.getStartNode();
	}

	/**
	 * inserts a content items to an entity as an ordered list according to the
	 * relationship type does not do any error checking and especially does not
	 * do any transaction saving. so this function needs to be capsuled into a
	 * transaction
	 * 
	 * Runtime: O(1)
	 * 
	 * @param db
	 * @param entity
	 * @param timestamp
	 */
	public static void InsertContentItemAsOrderedList(AbstractGraphDatabase db,
			Node entity, long timestamp, RelationshipType relType) {
		Node ci = db.createNode();
		ci.setProperty(Properties.timestamp, timestamp);
		// ci.setProperty("owner", owner);
		Relationship rel = entity.getSingleRelationship(relType,
				Direction.OUTGOING);
		if (rel != null) {
			Node tmp = rel.getEndNode();
			rel.delete();
			entity.createRelationshipTo(ci, relType);
			ci.createRelationshipTo(tmp, relType);
		} else {
			entity.createRelationshipTo(ci, relType);
		}
	}

	/**
	 * 
	 * @param db
	 * @param entity
	 * @param timeStamps
	 * @param ciListType
	 *            DynamicRelationshipType update =
	 *            DynamicRelationshipType.withName("ci:"+ key);
	 */

	public static void CreateListTopologyI(AbstractGraphDatabase db,
			Node entity, TreeSet<Long> timeStamps, RelationshipType ciListType) {

		if (getNextSingleNode(entity, ciListType) != null)
			return;

		Iterator<Long> it = timeStamps.descendingIterator();
		while (it.hasNext()) {
			Node ci = db.createNode();
			ci.setProperty("timestamp", it.next());
			// InsertListTopoCI(n, ci, update);
		}
	}

	/**
	 * 
	 * @param entity
	 *            node from which content items shall be deleted
	 * @param deleteNodes
	 *            should be <strong>true</strong> if Content Items in star
	 *            topology should be deleted
	 * @return returns a TreeSet<Long> with all timestamps that have been found
	 *         in the star toplogy
	 */
	public static TreeSet<Long> getAllStarUpdates(Node entity,
			boolean deleteNodes) {
		TreeSet<Long> s = new TreeSet<Long>();

		for (Relationship rel : entity.getRelationships(Relations.UPDATE,
				Direction.OUTGOING)) {
			Node tmp = rel.getEndNode();
			s.add((Long) tmp.getProperty("timestamp"));
			if (deleteNodes) {
				rel.delete();
				tmp.delete();
			}
		}
		return s;
	}

	/**
	 * returns an treeset containing all timestamps of all content items
	 * directly attached to and entity
	 * 
	 * @param entity
	 * @return
	 */
	public static TreeSet<Long> getAllStarUpdates(Node entity) {
		return getAllStarUpdates(entity, false);
	}

	public static void FillNodesInGraphitySimulation(String writeDBname,
			String readDBName, long ts) {
		AbstractGraphDatabase oldestDB = getAbstractGraphDatabase(
				Configs.get().CleanFriendDBPrefix + "1296514800", true);

		BatchInserter inserter = new BatchInserterImpl(writeDBname,
				BatchInserterImpl.loadProperties(wrwo					+ "/neostore.propertystore.db"));

		for (Node n : oldestDB.getAllNodes()) {
			if (inserter.nodeExists(n.getId())) {
				Map<String, Object> properties = new HashMap<String, Object>();
				for (String key : n.getPropertyKeys()) {
					properties.put(key, n.getProperty(key));
				}
				inserter.createNode(n.getId(), properties);
			}
		}
		oldestDB.shutdown();
		inserter.shutdown();
	}
}
