package heart.beat.exam;

import java.util.HashSet;
import java.util.Set;

import heart.beat.exam.abstrat.IdGenerator;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Unit test for simple App.
 */
public class AppTest extends TestCase {
	/**
	 * Create the test case
	 *
	 * @param testName
	 *            name of the test case
	 */
	public AppTest(String testName) {
		super(testName);
	}

	/**
	 * @return the suite of tests being tested
	 */
	public static Test suite() {
		return new TestSuite(AppTest.class);
	}

	/**
	 * Rigourous Test :-)
	 */
	public void testApp() {
		long Id;
		Set<Long> set = new HashSet<Long>();
		
		while(true){
			Id = IdGenerator.getPesudoId();
			if(!set.contains(Long.valueOf(Id))){
				set.add(Long.valueOf(Id));
				System.out.println(Id);
			}else{
				System.out.println("duplication");
			}
			
		}
		//assertTrue(Id > 0);
	}
}
