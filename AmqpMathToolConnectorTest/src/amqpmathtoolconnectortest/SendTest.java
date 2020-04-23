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

import com.rabbitmq.client.AMQP.BasicProperties;

import eu.cocop.amqp2math.AmqpConnector;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

/**
 * Tests sending of messages with the AMQP connector.
 * @author Petri Kannisto
 */
public class SendTest
{
	private final String classNameInLog;
	
	private final Ui m_ui;
	private final Channel m_channel;
	private final String m_exchangeName;
	private final AmqpConnector m_connector;
	
	
	/**
	 * Constructor.
	 * @param ui User interface.
	 * @param channel AMQP channel.
	 * @param excName Exchange name.
	 * @param conn AMQP connector to be tested.
	 */
	public SendTest(Ui ui, Channel channel, String excName, AmqpConnector conn)
	{
		classNameInLog = this.getClass().getSimpleName();
		
		m_ui = ui;
		m_channel = channel;
		m_exchangeName = excName;
		m_connector = conn;
	}
	
	/**
	 * Runs the test.
	 */
	public void runTest()
	{
		try
		{
			// Creating consumer objects
			createConsumer(Common.TopicFromMath1);
			createConsumer(Common.TopicFromMath2);
			
	        while (true)
	        {
	        	// Reading data using readLine
		        String input = m_ui.readUserInput("ENTER - burst some messages; q - quit").toLowerCase();
		        
		        if (input.equals("q"))
		        {
		        	break;
		        }
		        
		        // Sending a burst of messages
		        for (int a = 0; a < 3; ++a)
		        {
		        	String msgString = "Hello from 'Math tool' " + a;
	        		byte[] msgBytes = msgString.getBytes(Charset.forName("UTF-8"));
	        		m_connector.sendMessage(Common.TopicFromMath1, msgBytes);
	        		m_connector.sendMessage(Common.TopicFromMath2, msgBytes);
		        }
	        }
		}
		catch (IOException e)
		{
			String toConsole = String.format("Error in test: %s: %s", e.getClass().getName(), e.getMessage());
			myPrintError(toConsole);
		}
	}
	
	private void createConsumer(String topic)
			throws IOException
	{
		// Creating a message queue
		String explicitName = ""; // Empty value; the name will be generated
        boolean durable = true; // The queue survives a broker restart
        boolean exclusive = true; // Exclusive to this app, delete on exit
        boolean autoDelete = true; // Delete the queue if no consumer uses it
		String queueName = m_channel.queueDeclare(explicitName, durable, exclusive, autoDelete, null).getQueue();
		m_channel.queueBind(queueName, m_exchangeName, topic);
		
		// Creating a consumer object
		boolean autoAck = true; // No manual acks
		m_channel.basicConsume(queueName, autoAck, getConsumer());
		myPrintMessage("Now listening to topic \"" + topic + "\"");
	}
	
	private Consumer getConsumer()
	{
		return new DefaultConsumer(m_channel)
		{
			@Override
			public void handleDelivery(String consumerTag, Envelope envelope, BasicProperties properties, byte[] body)
					throws IOException
			{
				String bodyString = new String(body, Charset.forName("UTF-8"));
				String toConsole = String.format("Received from topic \"%s\": \"%s\"", envelope.getRoutingKey(), bodyString);
				myPrintMessage(toConsole);
			}
		};
	}
	
	private void myPrintMessage(String msg)
	{
		m_ui.printMessage(msg, classNameInLog);
	}
	
	private void myPrintError(String msg)
	{
		m_ui.printError(msg, classNameInLog);
	}
}
