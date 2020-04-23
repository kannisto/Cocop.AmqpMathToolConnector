//
// Please make sure to read and understand the files README.md and LICENSE.txt.
// 
// This file was prepared in the research project COCOP (Coordinating
// Optimisation of Complex Industrial Processes).
// https://cocop-spire.eu/
//
// Author: Petri Kannisto, Tampere University, Finland
// File created: 3/2020
// Last modified: 3/2020

package amqpmathtoolconnectortest;

/**
 * Entry point for the test program.
 * @author Petri Kannisto
 */
public class TestProgram
{
	// ###############################################
	//  See "test_instructions.txt" for instructions!
	// ###############################################
	
	
	public static void main(String[] args)
	{
		Ui ui = null;
		TestLogic testLogic = null;
		
		try
		{
			// Create objects
			ui = new Ui();
			testLogic = new TestLogic(ui);
			
			// Run test logic
			testLogic.run();
		}
		catch (Exception e)
		{
			e.printStackTrace(System.err);
		}
		finally
		{
			// Perform cleanup
			if (testLogic != null)
			{
				testLogic.close();
				testLogic = null;
			}
			if (ui != null)
			{
				ui.close();
				ui = null;
			}
		}
	}
}
