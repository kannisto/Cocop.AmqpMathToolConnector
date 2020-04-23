//
// Please make sure to read and understand the files README.md and LICENSE.txt.
// 
// This file was prepared in the research project COCOP (Coordinating
// Optimisation of Complex Industrial Processes).
// https://cocop-spire.eu/
//
// Author: Petri Kannisto, Tampere University, Finland
// File created: 8/2018
// Last modified: 3/2020

package eu.cocop.amqp2math;

import java.io.IOException;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.AlreadyClosedException;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.ShutdownListener;
import com.rabbitmq.client.ShutdownSignalException;
import com.rabbitmq.client.AMQP.BasicProperties;


/**
 * Provides means to send messages to and receive messages from a message bus.
 * @author Petri Kannisto
 */
public class AmqpConnector
{
	// The connection retry interval is 15 seconds 
	private final int ConnectionRetryInterval_s = 15;
	
	private final boolean m_debugEnabled;
	
	private final AmqpPropsManager m_amqpProperties;
	
	// This is used as the lock object for simple variables.
	// There is no guarantee which thread the timer tasks use, therefore
	// using a lock.
	private final Object m_variableLock = new Object();
	
	// This is used as the lock object for connection-related objects.
	// There is no guarantee which thread the timer tasks use, therefore
	// using a lock.
	private final Object m_connectionLock = new Object();
	
	// No mutual exclusion is applied for this collection, because this is not modified
	// after the constructor anymore
	private final TreeMap<String, Notifier> m_notifiers = new TreeMap<>();
	
	// Objects that begin with "conn" must be synchronised with the connection lock
	private Connection m_connConnection = null;
	private Channel m_connChannel = null;
	
	private boolean m_connectionIsOpenNow = false;
	private boolean m_userHasClosedConnection = false;
	
	// This will enable retrying the connection after a certain period
	private int m_connectRetryCountdown = 0;
	
	// The timer is used for sending and connecting, as the class is asynchronous
	private Timer m_timer = null;
	
	private LinkedList<MessageToBeSent> m_sendQueue = null;
	
	
	/**
	 * Constructor.
	 * @param props Message bus properties.
	 * @param topics Topics to subscribe to.
	 * @throws CommunicationException Thrown if an error occurs.
	 */
	public AmqpConnector(AmqpPropsManager props, String ... topics) throws CommunicationException
	{
		this(props, false, topics); // Debug mode is false by default
	}
	
	/**
	 * Constructor.
	 * @param props Message bus properties.
	 * @param debugOn Whether debug prints are enabled.
	 * @param topics Topics to subscribe to.
	 * @throws CommunicationException Thrown if an error occurs.
	 */
	public AmqpConnector(AmqpPropsManager props, boolean debugOn, String ... topics) throws CommunicationException
	{
		m_debugEnabled = debugOn;
		m_amqpProperties = props;
		m_sendQueue = new LinkedList<>();
		
		// Creating a notifier for each topic.
		// This does not include any network traffic.
		if (topics != null)
		{
			for (String t : topics)
			{
				Notifier notifier = new Notifier(t);
				m_notifiers.put(t, notifier);
			}
		}
		
		m_timer = new Timer();
		
		int timerPeriod_ms = 1000;
		// Use a longer timer period when debugging
		//int timerPeriod_ms = debugOn ? 2000 : 1000;
		
		// Scheduling the timer.
		// Using the "schedule" method instead of "scheduleAtFixedRate".
		// Therefore, if the execution of run() is delayed, this will delay
		// the next timer cycle as well.
		m_timer.schedule(new TimerTask()
		{
			@Override
			public void run()
			{
				doTimerTasks();
			}
		}, 0, timerPeriod_ms);
	}
	
	/**
	 * Starts to listen the given topic.
	 * @param topic Topic.
	 * @return Notifier object for the topic.
	 * @exception IllegalArgumentException Thrown if the topic is unknown.
	 */
	public Notifier getNotifierForTopic(String topic)
	{
		expectObjectNotClosed();
		
		if (!m_notifiers.containsKey(topic))
		{
			String msg = String.format("Unknown topic \"%s\" - topics must be specified in the constructor call", topic);
			throw new IllegalArgumentException(msg);
		}
		
		return m_notifiers.get(topic);
	}
	
	/**
	 * Sends a message to given topic.
	 * @param topic Topic.
	 * @param msg Message.
	 */
	public void sendMessage(String topic, byte[] msg)
	{
		expectObjectNotClosed();
		
		// Putting the message to a queue
		synchronized (m_variableLock)
		{
			MessageToBeSent obj = new MessageToBeSent(topic, msg);
			m_sendQueue.add(obj);
		}
	}
	
	/**
	 * Releases the resources the object utilises.
	 */
	public void close()
	{
		synchronized (m_variableLock)
		{
			// This will cause the timer to close the connection and end
			m_userHasClosedConnection = true;
		}
	}
	
	
	// ### Private methods ###
	
	private void doTimerTasks()
	{
		try
		{
			// Quit requested?
			if (userWantsToQuit())
			{
				printDebugMessage("User wants to quit, timer ending");
				
				// Ending timer execution
				m_timer.cancel();
				
				// Closing the connection
				connCloseConnection();
				
				return;
			}
			
			// Proceed only if the connection is already open or if it can be opened successfully.
			if (!connectionIsOpenNow() && !trySetUpConnectionAndListeners())
			{
				return;
			}
			
			// Send if anything to send
			sendIfAnythingToSend();
		}
		// 1) This block should catch basic errors where the connection has just closed.
		catch (AlreadyClosedException e)
		{
			printError("Failed to send because the connection is closed. A retry will occur.");
			
			synchronized (m_variableLock)
			{
				m_connectionIsOpenNow = false;
			}
		}
		// 2) This block catches the rest of errors. A retry will occur.
		catch (Exception e)
		{
			printError("Failed to perform timer tasks: " + e.getMessage());
		}
	}
	
	private void sendIfAnythingToSend() throws IOException
	{
		MessageToBeSent messageData = null;
		
		// There is no need to reserve the queue entirely for this function,
		// because the timer will always fire serially and never parallerly.
		// Therefore, only one thread will remove items from it.
		// Still, synchronisation is required because another adds
		// items to the queue.
		synchronized (m_variableLock)
		{
			if (m_sendQueue.isEmpty())
			{
				// Nothing to send currently
				//printDebugMessage("Nothing to send");
				return;
			}
			
			// Peeking the next message to send
			messageData = m_sendQueue.peek();
		}
		
		// Sending.
		// Use a TTL of 15 minutes for the messages.
		int ttlMilliseconds = 15 * 60 * 1000; // 15 minutes
		BasicProperties props = new BasicProperties().builder()
				.expiration(Integer.toString(ttlMilliseconds))
				.build();
		connSendMessage(messageData.topic, messageData.body, props);
		
		// No exception -> sent successfully. Remove the message from the queue.
		printDebugMessage("Message was sent to topic \"" + messageData.topic + "\"");
		
		synchronized (m_variableLock)
		{
			m_sendQueue.remove();
		}
	}
	
	private void printError(String msg)
	{
		String fullMsg = getMessageForPrint("ERR", msg);
		System.err.println(fullMsg);
	}
	
	private void printDebugMessage(String msg)
	{
		printDebugMessage(msg, false); // By default, do not override debug
	}
	
	private void printDebugMessage(String msg, boolean overrideDebug)
	{
		// The debug flag can be overridden
		if (m_debugEnabled || overrideDebug)
		{
			String fullMsg = getMessageForPrint("INF", msg);
			System.out.println(fullMsg);
		}
	}
	
	private String getMessageForPrint(String tag, String msg)
	{
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
		String timeString = LocalTime.now().format(formatter);
		return String.format("%s [AmqpConnector] (%s) %s", timeString, tag, msg);
	}
	
	private void expectObjectNotClosed()
	{
		if (userWantsToQuit())
		{
			throw new IllegalStateException("User has already closed the AMQP connector");
		}
	}
	
	private boolean connectionIsOpenNow()
	{
		synchronized (m_variableLock)
		{
			return m_connectionIsOpenNow;
		}
	}
	
	private boolean userWantsToQuit()
	{
		synchronized (m_variableLock)
		{
			return m_userHasClosedConnection;
		}
	}
	
	
	
	// ### Methods that modify connection-related objects ###
	
	// This code section was created to enable seeing easily if each access
	// to the connection-related object is synchronised appropriately.
	
	// This top-level method uses the lock statement
	private boolean trySetUpConnectionAndListeners()
	{
		// Time to retry?
		synchronized (m_variableLock)
		{
			if (m_connectRetryCountdown > 0)
			{
				// Retry interval not gone yet
				//printDebugMessage("Retry connection in " + m_connectRetryCountdown);
				--m_connectRetryCountdown;
				return false;
			}
		}
		
		// Close in case already open
		connCloseConnection();
		
		try
		{
			synchronized (m_connectionLock)
			{
				// Connecting
				connOpenAmqpConnection();
				printDebugMessage("Connection set up successfully", true);
				
				// Setting up listeners for topics
				for (String topic : m_notifiers.keySet())
				{
					connSetUpNotifierForTopic(topic, m_notifiers.get(topic));
					printDebugMessage("Now consuming topic \"" + topic + "\"");
				}
			}
		}
		catch (IOException | CommunicationException e)
		{
			String errMsg = String.format("Failed to set up connection. Retry in %s s. \"%s\"",
					Integer.toString(ConnectionRetryInterval_s), e.getMessage());
			printError(errMsg);
			
			// Resetting the retry counter
			synchronized (m_variableLock)
			{
				m_connectRetryCountdown = ConnectionRetryInterval_s;
			}
			
			// Close in case integrity not OK
			connCloseConnection();
			
			return false;
		}
		
		synchronized (m_variableLock)
		{
			m_connectionIsOpenNow = true;
		}
		
		// Success
		return true;
	}
	
	// Only call this method when the connection lock is applied!
	private void connOpenAmqpConnection() throws CommunicationException
	{
		ConnectionFactory factory = new ConnectionFactory();
		
		try
		{
			if (m_amqpProperties.getSecure())
			{
				// Due to calling this function, no certificate verification will be performed
				factory.useSslProtocol();
			}
	        
			factory.setUri(m_amqpProperties.getUrl());
		}
		catch (KeyManagementException | NoSuchAlgorithmException | URISyntaxException e)
		{
			throw new CommunicationException("Connection setup failed: " + e.getMessage(), e);
		}
		
		try
		{
			// Opening a connection
			m_connConnection = factory.newConnection();
			m_connChannel = m_connConnection.createChannel();
			
			// Adding shutdown listeners
			ShutdownListener shutdownListener = new ShutdownListener()
			{				
				@Override
				public void shutdownCompleted(ShutdownSignalException arg0)
				{
					// Passing execution to a local method
					myShutdownCompleted();
				}
			};
			m_connConnection.addShutdownListener(shutdownListener);
			m_connChannel.addShutdownListener(shutdownListener);
			
			// Declaring the desired exchange
			boolean durable = true;
			boolean autoDelete = false;
			m_connChannel.exchangeDeclare(m_amqpProperties.getExchange(), "topic", durable, autoDelete, null);
		}
		catch (TimeoutException e)
		{
			handleConnectError(e); // throws CommunicationException
		}
		catch (IOException e)
		{
			handleConnectError(e); // throws CommunicationException
		}
	}
	
	private void handleConnectError(Exception e) throws CommunicationException
	{
		String msgStart = "Failed to create AmqpConnector";
		String errMsg = String.format("%s: %s: %s", msgStart, e.getClass().getSimpleName(), e.getMessage());
		printError(errMsg);
		
		if (e.getCause() != null)
		{
			Throwable cause = e.getCause();
			printError("-- Error cause: " + cause.getMessage());
		}
		
		throw new CommunicationException(msgStart, e);
	}
	
	private void myShutdownCompleted()
	{
		// The connection has shut down!
		synchronized (m_variableLock)
		{
			// This flag will trigger a reconnect unless the user called close()
			m_connectionIsOpenNow = false;
		}
		
		if (!userWantsToQuit())
		{
			printError("Connection lost!");
		}
	}
	
	// Only call this method when the connection lock is applied!
	private void connSetUpNotifierForTopic(String topic, Notifier notifier) throws IOException
	{
		// Creating a message queue
		String explicitName = ""; // Empty value; the name will be generated
        boolean durable = false; // The queue does not survive a broker restart
        boolean exclusive = true; // Exclusive to this app, delete on exit
        boolean autoDelete = true; // Delete the queue if no consumer uses it
		String queueName = m_connChannel.queueDeclare(explicitName, durable, exclusive, autoDelete, null).getQueue();
		m_connChannel.queueBind(queueName, m_amqpProperties.getExchange(), topic);
		
		// Creating a consumer object
		MyConsumer consumer = new MyConsumer(m_connChannel, notifier);
		boolean autoAck = true; // No manual acks
		m_connChannel.basicConsume(queueName, autoAck, consumer);
	}
	
	// This top-level method uses the lock statement
	private void connSendMessage(String topic, byte[] msg, BasicProperties props) throws IOException
	{
		synchronized (m_connectionLock)
		{
			// TODO: This will not fail immediately after losing the connection.
			// It is unclear what happens to messages sent before the connection is
			// declared lost. :/
			m_connChannel.basicPublish(m_amqpProperties.getExchange(), topic, props, msg);
		}
	}
	
	// This top-level method uses the lock statement
	private void connCloseConnection()
	{
		synchronized (m_connectionLock)
		{
			if (m_connChannel != null)
			{
				try {
					m_connChannel.close();
					m_connChannel = null;
				} catch (Exception ignore) {}
			}
			
			if (m_connConnection != null)
			{
				try {
					m_connConnection.close();
					m_connConnection = null;
				} catch (Exception ignore) {}
			}
		}
		
		synchronized (m_variableLock)
		{
			m_connectionIsOpenNow = false;
		}
	}
	
	
	
	// ### Nested classes ###
	
	/**
	 * Consumer class to receive messages.
	 * @author Petri Kannisto
	 */
	private class MyConsumer extends DefaultConsumer
	{
		// These start with "cons_" not to confuse with "m_" of the enclosing class
		private Notifier cons_eventManager;
		
		
		/**
		 * Constructor.
		 * @param ch Channel.
		 * @param topic Topic.
		 * @param evm Event manager.
		 */
		public MyConsumer(Channel ch, Notifier evm)
		{
			super(ch);
			
			cons_eventManager = evm;
		}
		
		@Override
	    public void handleDelivery(String consumerTag, Envelope envelope,
	    		BasicProperties properties, byte[] body) throws IOException
	    {
			try
			{
				cons_eventManager.notifyMathTool(body);
			}
			catch (CommunicationException e)
			{
				throw new IOException(e.getMessage(), e);
			}
	    }
	}
	
	// This class enables message information to be associated and enqueued together.
	private class MessageToBeSent
	{
		public final String topic;
		public final byte[] body;
		
		public MessageToBeSent(String t, byte[] b)
		{
			topic = t;
			body = b;
		}
	}
}
