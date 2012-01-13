package dml.test.integration.functions.data;

import org.junit.Test;

import dml.test.integration.AutomatedTestBase;
import dml.test.integration.TestConfiguration;
import dml.test.utils.TestUtils;


/**
 * <p><b>Positive tests:</b></p>
 * <ul>
 * 	<li>decrease block size</li>
 * </ul>
 * <p><b>Negative tests:</b></p>
 * 
 * @author schnetter
 */
public class ReblockTest extends AutomatedTestBase {

	@Override
	public void setUp() {
		baseDirectory = SCRIPT_DIR + "functions/data/";
		
		// positive tests
		availableTestConfigurations.put("ReblockTest", new TestConfiguration("ReblockTest",
				new String[] { "a" }));
		
		// negative tests
	}
	
	@Test
	public void testReblock() {
		loadTestConfiguration("ReblockTest");
		
		double[][] a = getRandomMatrix(10, 10, 0, 1, 1, -1);
		TestUtils.writeBinaryTestMatrixBlocks(baseDirectory + INPUT_DIR + "a/in", a, 2, 2, false);
		inputDirectories.add(baseDirectory + INPUT_DIR + "a");
		
		writeExpectedMatrix("a", a);
		
		runTest();
		
		compareResults();
	}

}
