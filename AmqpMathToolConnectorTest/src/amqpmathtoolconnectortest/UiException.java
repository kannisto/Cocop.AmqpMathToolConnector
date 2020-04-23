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
 * Thrown if the UI fails.
 * @author Petri Kannisto
 */
public class UiException extends RuntimeException
{
	private static final long serialVersionUID = 4893901861484756168L;
	

	/**
	 * Constructor.
	 * @param cause Cause.
	 */
	public UiException(Exception cause)
	{
		super(cause.getMessage(), cause);
		
		// Otherwise, empty ctor body
	}
}
