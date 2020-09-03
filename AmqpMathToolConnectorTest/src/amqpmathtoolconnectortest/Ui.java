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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Functionality for the user interface.
 * @author Petri Kannisto
 */
public class Ui
{
	private BufferedReader m_reader = null;
	
	
	/**
	 * Constructor.
	 */
	public Ui()
	{
		// Creating a reader to get user input
		try
		{
			m_reader = new BufferedReader(new InputStreamReader(System.in));
		}
		catch (Exception e)
		{
			close();
		}
	}
	
	/**
	 * Release the resources of the object.
	 */
	public void close()
	{
		try
		{
			if (m_reader != null)
			{
				m_reader.close();
				m_reader = null;
			}
		}
		catch (IOException e)
		{
			// No can do!
		}
	}
	
	/**
	 * Prints an error message.
	 * @param msg Message.
	 */
	public void printError(String msg, String className)
	{
		CheckNotClosed();
		printMessageWithStream(System.err, msg, className);
	}
	
	/**
	 * Prints a message.
	 * @param msg Message.
	 */
	public void printMessage(String msg)
	{
		CheckNotClosed();
		printMessage(msg, null);
	}
	
	/**
	 * Prints a message.
	 * @param msg Message.
	 * @param className Name of the related class.
	 */
	public void printMessage(String msg, String className)
	{
		CheckNotClosed();
		printMessageWithStream(System.out, msg, className);
	}
	
	private void printMessageWithStream(PrintStream stream, String msg, String className)
	{
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
		String timestamp = LocalTime.now().format(formatter);
		String msgFull = null;
		
		if (className == null)
		{
			msgFull = String.format("%s [ConnectorTest] %s", timestamp, msg);
		}
		else
		{
			msgFull = String.format("%s [ConnectorTest][%s] %s", timestamp, className, msg);
		}
		
		stream.println(msgFull);
	}
	
	/**
	 * Reads user input.
	 * @param prompt Prompt to be shown.
	 * @return User input.
	 */
	public String readUserInput(String prompt)
	{
		CheckNotClosed();
		
		// Print prompt and get input
		System.out.print(prompt + " > ");
		return readUserInput();
	}
	
	/**
	 * Reads user input.
	 * @return User input.
	 */
	public String readUserInput()
	{
		CheckNotClosed();
		
		try
		{
			return m_reader.readLine().trim();
		}
		catch (IOException e)
		{
			throw new UiException(e);
		}
	}
	
	/**
	 * Prompts user input with a default value.
	 * @param message Message to be shown.
	 * @param defaValue Default value.
	 * @return Input or default if the input is empty.
	 */
	public String promptUserInput(String message, String defaValue)
	{
		String input = readUserInput(String.format("%s [%s]", message, defaValue)).trim();
		return input.length() < 1 ? defaValue : input;
	}
	
	private void CheckNotClosed()
	{
		if (m_reader == null)
		{
			throw new IllegalStateException("UI object already closed");
		}
	}
}
