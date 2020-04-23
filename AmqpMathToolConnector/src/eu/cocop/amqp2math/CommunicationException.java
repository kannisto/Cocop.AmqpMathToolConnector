//
// Please make sure to read and understand the files README.md and LICENSE.txt.
// 
// This file was prepared in the research project COCOP (Coordinating
// Optimisation of Complex Industrial Processes).
// https://cocop-spire.eu/
//
// Author: Petri Kannisto, Tampere University, Finland
// File created: 2/2018
// Last modified: 3/2020

package eu.cocop.amqp2math;

/**
 * Represents a communication failure.
 * @author Petri Kannisto
 */
public class CommunicationException extends Exception
{
	private static final long serialVersionUID = 4333744147294145239L;
	
	
	/**
	 * Constructor.
	 * @param msg Error message.
	 * @param ie Inner exception.
	 */
	CommunicationException(String msg, Exception ie)
	{
		super(msg, ie);
		
		// Otherwise, empty ctor body
	}
}
