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
 * Holds common data and operations.
 * @author Petri Kannisto
 */
public class Common
{
	private Common()
	{
		// Private ctor -> "static" class
		
		// Empty ctor body
	}
	
	// These topics are utilised in tests
	public static final String TopicToMath1 = "topic.from.bus.1";
	public static final String TopicToMath2 = "topic.from.bus.2";
	public static final String TopicToMath3 = "topic.from.bus.3";
	public static final String TopicFromMath1 = "topic.to.bus.1";
	public static final String TopicFromMath2 = "topic.to.bus.2";
}
