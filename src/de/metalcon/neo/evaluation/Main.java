/**
 * main class which just reads out the configuration file and controlls the program
 * 
 *
 * @author Jonas Kunze, Rene Pickhardt
 * 
 */

package de.metalcon.neo.evaluation;

import java.io.IOException;
import java.sql.SQLException;

import de.metalcon.neo.evaluation.dumps.DegreeReader;
import de.metalcon.neo.evaluation.dumps.SnapshotGenerator;
import de.metalcon.neo.evaluation.dumps.SnapshotIDListGenerator;
import de.metalcon.neo.evaluation.utils.Configs;
import de.metalcon.neo.evaluation.utils.DumpPreparer;
import de.metalcon.neo.evaluation.utils.H;

public class Main {

	/**
	 * @param args
	 * @throws IOException
	 * @throws InterruptedException
	 * @throws ClassNotFoundException
	 * @throws SQLException
	 */
	public static void main(String[] args) throws IOException,
			InterruptedException, ClassNotFoundException, SQLException {
		H.strongLog("\n");
		H.strongLog("!!!!!!!!!!!! starting a new test !!!!!!!");
		H.strongLog("cache_type = " + Configs.get().cache_type + "\n\n\n");

		if (Configs.get().MetalconRun) {
			H.strongLog("Running in Metalcon-Mode!!!" + "\n\n\n");
		}

		if (Configs.get().IgnoreSmallDegreeNodes) {
			H.strongLog("Ignoring small Nodes with degree lower than "
					+ Configs.get().MinimumNodeDegree + "\n\n\n");
		}

		if (Configs.get().SortWikiTransactions) {
			H.log("Start to sorted Wiki transactions from file: "
					+ Configs.get().wikiTransactionsFile + " to file: "
					+ Configs.get().SortedWikiTransactionsFile);

			Runtime.getRuntime().exec(
					"cat " + Configs.get().wikiTransactionsFile
							+ " | sort -n > "
							+ Configs.get().SortedWikiTransactionsFile);

			H.log("Successfully sorted Wiki transactions from file: "
					+ Configs.get().wikiTransactionsFile + " to file: "
					+ Configs.get().SortedWikiTransactionsFile);
		}

		if (Configs.get().CleanWikiTransactions) {
			H.log("Start to remove Corrupted Relations"
					+ Configs.get().SortedWikiTransactionsFile
					+ " saving file to "
					+ Configs.get().CleanWikiTransactionsFile);

			DumpPreparer dp = new DumpPreparer(
					Configs.get().SortedWikiTransactionsFile);
			dp.run();

			H.log("Successfully removed Corrupted Relations from "
					+ Configs.get().SortedWikiTransactionsFile
					+ " saved file to "
					+ Configs.get().CleanWikiTransactionsFile);
		}

		long[] starSnapshotTimestamps = Configs.get().StarSnapshotTimestamps;

		if (Configs.get().SplitCleanDumps) {
			H.log("Split Cleaned sorted wiki transaction Dumps");
			SnapshotGenerator sg = new SnapshotGenerator(
					Configs.get().CleanWikiTransactionsFile,
					starSnapshotTimestamps,
					Configs.get().CleanWikiSnapshotUpdatePrefix,
					Configs.get().CleanWikiSnapshotFriendPrefix);
			sg.run();
			H.log("Successfully Cleaned sorted wiki transaction Dumps");
		}

		if (Configs.get().GenerateSnapshotIDLists) {
			SnapshotIDListGenerator generator = new SnapshotIDListGenerator();
			generator.run();
		}

		if (Configs.get().CreateStarDBs) {
			H.log("Create Friendship graphs");
			FriendshipFromSnapshotGenerator generator = new FriendshipFromSnapshotGenerator(
					Configs.get().StarDBDirPrefix, starSnapshotTimestamps,
					Configs.get().CleanWikiSnapshotFriendPrefix,
					Configs.get().CleanWikiSnapshotUpdatePrefix);
			generator.run();
			H.log("successfully created Friendship graphs");
		}

		if (Configs.get().MetalconRun && Configs.get().CreateMetalconFiles) {
			MetalconBuilder mb = new MetalconBuilder();
			mb.createMetalconFiles();
		}

		if (Configs.get().GenerateDegreeList) {
			DegreeReader r = new DegreeReader();
			r.generateDegreeMaps();
		}

		if (Configs.get().GenerateDegreeSamples) {
			DegreeReader r = new DegreeReader();
			r.generateDegreeSamples();
		}

		if (Configs.get().FlatFileUpdateEvaluatorEvaluate
				|| Configs.get().FlatFileUpdateEvaluatorInsertUpdates) {
			H.log("Evaluating with FlatFile");
			FlatFileUpdateEvaluator ffue = new FlatFileUpdateEvaluator();
			ffue.run();
			H.log("successfully evaluated FlatFile");
		}

		if (Configs.get().BlouUpdateEvaluatorInsertUpdates) {
			H.log("Building Blou");
			BlouUpdateEvaluator blue = new BlouUpdateEvaluator();
			blue.run(true, false);
			H.log("successfully built Blou");
		}

		if (Configs.get().BlouUpdateEvaluatorInsertUpdates
				|| Configs.get().BlouUpdateEvaluatorEvaluate
				|| Configs.get().BlouUpdateEvaluatorSimulate) {
			H.log("Evaluating with Blou");
			BlouUpdateEvaluator blue = new BlouUpdateEvaluator();
			blue.run(false, false);
			H.log("successfully evaluated Blou");
		}

		if (Configs.get().BaseLineUpdateEvaluatorEvaluate
				|| Configs.get().BaseLineUpdateEvaluatorInsertUpdates) {
			H.log("Evaluating with BaseLine");
			BSUpdateEvaluator evaluator = new BSUpdateEvaluator();
			evaluator.run();
			H.log("successfully evaluated BaseLine");
		}

		if (Configs.get().BlouUpdateEvaluatorEvaluateDegree) {
			H.log("Building Blou with degree sample");
			BlouUpdateEvaluator blue = new BlouUpdateEvaluator();
			blue.run(false, true);
			H.log("successfully built Blou with degree sample");
		}

		if (Configs.get().SimulateGraphity || Configs.get().BuildGraphity
				|| Configs.get().ReadGraphityStreamsDegree
				|| Configs.get().ReadGraphityStreams) {
			H.log("Build Graphity on on top of baseLine graphs with degree sample");
			GraphityBuilder gb = new GraphityBuilder();
			gb.run();
			H.log("successfully build Graphity on top of baseline graphs with degree sample");
		}

		/**
		 * dvide wiki dump to snapshots create 1 file with friend relations for
		 * each snapshotdate 1 file with all updates until the snapshot date 1
		 * file with all transaction between two snapshots
		 */
	}
}
