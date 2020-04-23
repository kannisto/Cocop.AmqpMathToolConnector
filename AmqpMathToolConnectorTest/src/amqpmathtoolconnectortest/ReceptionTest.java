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

import java.io.IOException;
import java.nio.charset.Charset;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import com.rabbitmq.client.Channel;

/**
 * Implements a test for message reception.
 * @author Petri Kannisto
 */
public class ReceptionTest
{
	private final String classNameInLog;
	
	private final Ui m_ui;
	private final Channel m_channel;
	private final String m_exchangeName;
	
	
	/**
	 * Constructor.
	 * @param ui User interface.
	 * @param channel AMQP channel.
	 * @param excName Exchange name.
	 */
	public ReceptionTest(Ui ui, Channel channel, String excName)
	{
		classNameInLog = this.getClass().getSimpleName();
		
		m_ui = ui;
		m_channel = channel;
		m_exchangeName = excName;
	}
	
	/**
	 * Runs the test.
	 */
	public void runTest()
	{
		// Setting up a timer to send messages regularly
		Timer timer = new Timer();
		MyTimerTask timerTask = new MyTimerTask();
		
		try
		{
			timer.schedule(timerTask, 3000, 7000);
			
			myPrintMessage("Sending messages regularly.");
			myPrintMessage("Give 'q' to exit...");
			myPrintMessage("");
			
	        while (true)
	        {
		        // Reading data using readLine
		        String input = m_ui.readUserInput();
		        
		        if (input.toLowerCase().equals("q"))
		        {
		        	break;
		        }
	        }
		}
		finally
		{
			// Quitting data sender
			myPrintMessage("Quitting MyDataSource...");
			
        	timerTask.cancel();
			timer.cancel();
		}
	}
	
	private void myPrintMessage(String msg)
	{
		m_ui.printMessage(msg, classNameInLog);
	}
	
	private void myPrintError(String msg)
	{
		m_ui.printError(msg, classNameInLog);
	}
	
	
	private class MyTimerTask extends TimerTask
	{
		private final ArrayList<String> tt_topics;
		
		// This is used to point which topic to send to
		private int tt_topicIndex = 0;
		
		
		public MyTimerTask()
		{
			// Adding topics
			tt_topics = new ArrayList<>();
			tt_topics.add(Common.TopicToMath1);
			tt_topics.add(Common.TopicToMath2);
			tt_topics.add(Common.TopicToMath3);
		}
		
		/**
		 * Runs the thread.
		 */
		@Override
		public void run()
		{
			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
			String message = "Hello at " + LocalTime.now().format(formatter);
			sendMessage(message);
		}
		
		private void sendMessage(String msg)
		{
			byte[] messageOut = msg.getBytes(Charset.forName("UTF-8"));
			
			try
			{
				// Get the current topic
				String topic = tt_topics.get(tt_topicIndex);
				
				// Increment topic index
				++tt_topicIndex;
				tt_topicIndex = tt_topicIndex % tt_topics.size(); // Set to 0 if exceeding the size
				
				m_channel.basicPublish(m_exchangeName, topic, null, messageOut);
		        
				String toConsole = String.format("Sent message to topic \"%s\": \"%s\"", topic, msg);
		        myPrintMessage(toConsole);
			}
			catch (IOException e)
			{
				myPrintError("Failed to send message: " + e.getMessage());
			}
		}
	}
}
