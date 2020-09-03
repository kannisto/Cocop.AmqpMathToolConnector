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
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import eu.cocop.amqp2math.AmqpConnector;
import eu.cocop.amqp2math.AmqpPropsManager;
import eu.cocop.amqp2math.CommunicationException;
import eu.cocop.amqp2math.Notifier;
import eu.cocop.amqp2math.Notifier.IMessageListener;
import eu.cocop.amqp2math.Notifier.MessageReceivedEvent;


/**
 * Implements the test logic.
 * @author Petri Kannisto
 */
public class TestLogic
{
	private final String classNameInLog;
	
	private final Ui m_ui;
	
	// This stores notifier objects to prevent carbage collection
	// (presumably, the objects could otherwise disappear)
	private final ArrayList<Notifier> m_notifierStorage;
	
	private boolean m_alreadyClosed = false;
	private Connection m_connection = null;
	private Channel m_channel = null;
	
	
	/**
	 * Constructor.
	 * @param ui User interface.
	 */
	public TestLogic(Ui ui)
	{
		classNameInLog = this.getClass().getSimpleName();
		
		m_ui = ui;
		m_notifierStorage = new ArrayList<>();
	}
	
	/**
	 * Releases all the resources of the object.
	 */
	public void close()
	{
		m_alreadyClosed = true;
		
		// Cleaning up AMQP resources
		try
		{
			if (m_channel != null)
			{
				m_channel.close();
				m_channel = null;
			}
			if (m_connection != null)
			{
				m_connection.close();
				m_connection = null;
			}
		}
		catch (Exception e)
		{
			// No can do!
		}
	}
	
	/**
	 * Runs the test logic.
	 * @throws IOException Thrown if the math tool connector fails.
	 */
	public void run() throws IOException
	{
		if (m_alreadyClosed)
		{
			throw new IllegalStateException("Test logic object already closed");
		}
		
		myPrintMessage("Started.");
		
		// 1) Creating a connection to enable testing
		AmqpParamsHolder connParams = runConnectSequence();
		
		if (connParams == null)
		{
			// Not connected -> quit
			return;
		}
        
		// 2) Setting up math tool connector for the test
		AmqpConnector connector = setUpConnectorForTest(connParams);
		
		try
		{
			while (true)
			{
				// 3) Asking the user which test to perform
				myPrintMessage("Please choose test to perform or quit.");
				String userInputToChoose = m_ui.readUserInput("Q - quit, R - receive; S - send").toLowerCase();
				
				if (userInputToChoose.equals("q"))
				{
					break;
				}
				else if (userInputToChoose.equals("r"))
				{
					new ReceptionTest(m_ui, m_channel, connParams.exchange).runTest();
				}
				else if (userInputToChoose.equals("s"))
				{
					new SendTest(m_ui, m_channel, connParams.exchange, connector).runTest();
				}
				else
				{
					myPrintMessage("Unexpected input.");
				}
			}
		}
		finally // Cleanup
		{
			connector.close();
			connector = null;
		}
		
		myPrintMessage("Exit.");
	}
	
	private AmqpConnector setUpConnectorForTest(AmqpParamsHolder amqpParams)
			throws IOException
	{
		AmqpPropsManager msgBusProps = new AmqpPropsManager(amqpParams.host, amqpParams.exchange, amqpParams.username, amqpParams.password);
		msgBusProps.setPort(amqpParams.port);
		msgBusProps.setSecure(amqpParams.secure);
		msgBusProps.setExchangeDurable(amqpParams.durableExchange);
		msgBusProps.setExchangeAutoDelete(amqpParams.autoDeleteExchange);
		AmqpConnector connector = null;
		
		try
		{
			// Creating a connector
			connector = new AmqpConnector(msgBusProps, true, Common.TopicToMath1, Common.TopicToMath2, Common.TopicToMath3);
			
			// Waiting for the connector to start. Otherwise, the output would mess up the UI.
			Thread.sleep(1500); // 1500 ms
			
			// Creating math tool (e.g., Matlab) notifiers. Keeping the references to prevent
			// garbage collection from cleaning the objects away.
			m_notifierStorage.add(getListenerForTopic(connector, Common.TopicToMath1));
			m_notifierStorage.add(getListenerForTopic(connector, Common.TopicToMath2));
			m_notifierStorage.add(getListenerForTopic(connector, Common.TopicToMath3));
			
			return connector;
		}
		catch (Exception e)
		{
			if (connector != null) {
				connector.close();
			}
			
			throw new IOException("Failed to create connector: " + e.getMessage(), e);
		}
	}
	
	private AmqpParamsHolder runConnectSequence()
	{
		// Return connection params if connecting succeeds. Otherwise, return null.
		
		AmqpParamsHolder holder = new AmqpParamsHolder();
		
		// Get user input to connect
		
		holder.host = m_ui.promptUserInput("Host", "localhost");
		holder.secure = getBoolFromUserInput(m_ui.promptUserInput("Secure connection? 'y' for true, any other for not", ""));
		
		String defaultPort = holder.secure ? "5671" : "5672";
		holder.port = Integer.parseInt(m_ui.promptUserInput("Port", defaultPort));
		
		holder.exchange = m_ui.promptUserInput("Exchange", "my.exchange");
		holder.durableExchange = getBoolFromUserInput(m_ui.promptUserInput("Durable exchange? 'y' for true, any other for false", ""));
		holder.autoDeleteExchange = getBoolFromUserInput(m_ui.promptUserInput("Auto delete exchange? 'y' for auto delete, any other for not", ""));
		holder.username = m_ui.promptUserInput("Username", "guest");
		
        while (true)
        {
        	// Get password. This is inside the loop, so there is a chance to retry.
        	holder.password = m_ui.promptUserInput("Password", "guest");
        	
        	myPrintMessage("Connecting the test application to the AMQP exchange...");
        	
        	try
        	{
        		// Connecting
        		setUpAmqpConnection(holder);
        		
        		myPrintMessage("Connection opened.");
        		return holder;
        	}
        	catch (IOException e)
        	{
        		myPrintError("Connecting failed.");
        		myPrintError("Error message: " + e.getMessage());
        		
        		// Retry connection?
        		myPrintMessage("Retry with another password?");
        		String userInputForRetry = m_ui.readUserInput("'y' to retry, any other to quit");
        		
        		if (!userInputForRetry.trim().toLowerCase().equals("y"))
        		{
        			return null; // Do not retry
        		}
			}
        }
	}
	
	private boolean getBoolFromUserInput(String input)
	{
		return input.trim().toLowerCase().equals("y");
	}
	
	private void setUpAmqpConnection(AmqpParamsHolder holder)
			throws IOException
	{
		// Setting up a connection factory
		ConnectionFactory factory = new ConnectionFactory();
        
		try
		{
			if (holder.secure)
			{
				factory.useSslProtocol(); // Due to this call, no certificate verification occurs
			}
			
			factory.setUri(holder.buildUrl());
		}
		catch (KeyManagementException | URISyntaxException | NoSuchAlgorithmException e)
		{
			HandleConnectionException(e); // throws IOException
		}
		
		// Setting up a connection and the exchange
		try
		{
			m_connection = factory.newConnection();
			
			// Creating a channel and an exchange
			m_channel = m_connection.createChannel();
			m_channel.exchangeDeclare(holder.exchange, "topic", holder.durableExchange, holder.autoDeleteExchange, null);
		}
		catch (IOException | java.util.concurrent.TimeoutException e)
		{
			closeAmqpObjects();
			HandleConnectionException(e); // throws IOException
		}
	}
	
	private void HandleConnectionException(Exception e)
			throws IOException
	{
		// If an inner exception exists, taking its message. Otherwise, re-use the one from the exception object.
		String msg = e.getMessage();
		
		if (e.getCause() != null)
		{
			msg = e.getCause().getMessage();
		}
		
		throw new IOException("Failed to connect: " + msg, e);
	}
	
	private void closeAmqpObjects()
	{
		// Cleaning up AMQP-related objects
		try
		{
			if (m_channel != null)
			{
				m_channel.close();
				m_channel = null;
			}
		}
		catch (Exception e) // No can do!
		{ }
		
		try
		{
			if (m_connection != null)
			{
				m_connection.close();
				m_connection = null;
			}
		}
		catch (Exception e) // No can do!
		{ }
	}
	
	private Notifier getListenerForTopic(AmqpConnector connector, String topic)
	{
		// Getting the notifier of the topic
		
		Notifier mathToolNotifier = connector.getNotifierForTopic(topic);
		
		// Adding a listener. Expecting the math tool to do this.
		IMessageListener myListener = new IMessageListener()
		{
			@Override
			public void listen(MessageReceivedEvent event) throws CommunicationException
			{
				String toConsole = String.format("Got message with routing key \"%s\": \"%s\"",
						event.routingKey, new String(event.message, Charset.forName("UTF-8")));
				myPrintMessage(toConsole, "IMessageListener");
			}
		};
		mathToolNotifier.addListener(myListener);
		
		myPrintMessage("Created an IMessageListener for topic \"" + topic + "\"");
		
		return mathToolNotifier;
	}
	
	private void myPrintMessage(String msg, String className)
	{
		m_ui.printMessage(msg, className);
	}
	
	private void myPrintMessage(String msg)
	{
		m_ui.printMessage(msg, classNameInLog);
	}
	
	private void myPrintError(String msg)
	{
		m_ui.printError(msg, classNameInLog);
	}
	
	/**
	 * Holds AMQP connection parameters.
	 * @author Petri Kannisto
	 */
	private class AmqpParamsHolder
	{
		public String host = null;
		public boolean secure = true;
		public int port = 5672;
		public String exchange = null;
		public boolean durableExchange = false;
		public boolean autoDeleteExchange = false;
		public String username = null;
		public String password = null;
		
		public String buildUrl()
		{
			String scheme = secure ? "amqps" : "amqp";
			return String.format("%s://%s:%s@%s:%d", scheme, username, password, host, port);
		}
	}
}
