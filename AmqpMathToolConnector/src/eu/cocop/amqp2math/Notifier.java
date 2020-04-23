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
// 
// Modified from code by Sathish Kumar Narayanan
// https://github.com/ragavsathish/RabbitMQ-Matlab-Client/tree/master/src/mqwrapper

package eu.cocop.amqp2math;

/**
 * Class to enable eventing with a math tool, such as Matlab.
 * @author Sathish Kumar Narayanan
 * @author Petri Kannisto
 */
public class Notifier
{
	/*
	In the original implementation (by Sathish Kumar Narayanan), this class is called MessagingEvent
	(see https://github.com/ragavsathish/RabbitMQ-Matlab-Client/blob/master/src/mqwrapper/MessagingEvent.java).
	This implementation has been modified substantially from the original.
	
	The most important modifications in this file:
	- Added comments (all of them)
	- Renamed package mqwrapper to cocop.amqp2math
	- Notifier
	  - this class was renamed from MessagingEvent
	  - renamed method notifyMatlab to notifyMathTool
	  - now using explicit typing for the Vector of message listeners
	- IMessageListener
	  - this was renamed from MessageListener
	- MessageReceivedEvent
	  - this class was renamed from GetMessageEvent
	  - new member: routing key
	  - now delivering the message body as byte[] instead of string
	*/
	
	private final String m_topic;
	
	private java.util.Vector<IMessageListener> m_listeners = new java.util.Vector<IMessageListener>();
	
	
	/**
	 * Constructor.
	 * @param topic Topic.
	 */
	Notifier(String topic)
	{
		m_topic = topic;
	}
	
	/**
	 * Adds an event listener.
	 * @param lis Listener.
	 */
	public synchronized void addListener(IMessageListener lis)
	{		
		// This method is called explicitly neither here nor in the math tool (such as Matlab).
		// Therefore, it is assumed that the math tool calls this in the background.
		// It is also assumed that its name is fixed ("addListener").
		
		synchronized (this)
		{
			m_listeners.addElement(lis);
		}
	}
	
	/**
	 * Removes an event listener.
	 * @param lis Listener.
	 */
	public synchronized void removeListener(IMessageListener lis)
	{
		// This method is called explicitly neither here nor in the math tool (such as Matlab).
		// Therefore, it is assumed that the math tool calls this in the background.
		// It is also assumed that its name is fixed ("removeListener").
		
		synchronized (this)
		{
			m_listeners.removeElement(lis);
		}
	}
	
	/**
	 * Notifies the math tool (such as Matlab) with a message.
	 * @param msg Message.
	 * @exception CommunicationException Thrown if an error occurs.
	 */
	@SuppressWarnings("unchecked")
	void notifyMathTool(byte[] msg) throws CommunicationException
	{
		// Copying the listener list in case it is modified during notifications
		java.util.Vector<IMessageListener> listenersCopy;
		
		synchronized (this)
		{
			listenersCopy = (java.util.Vector<IMessageListener>)m_listeners.clone();
		}
		
		for (int i = 0; i < listenersCopy.size(); i++)
		{
			MessageReceivedEvent event = new MessageReceivedEvent(this, m_topic, msg);
			((IMessageListener)listenersCopy.elementAt(i)).listen(event);
		}
	}
	
	/**
	 * Event class. These events are delivered to the math tool when a message arrives
	 * from the message bus.
	 * @author Sathish Kumar Narayanan
	 * @author Petri Kannisto
	 */
	public class MessageReceivedEvent extends java.util.EventObject
	{
		private static final long serialVersionUID = 1L;
		
		/**
		 * The routing key of the message.
		 */
		public final String routingKey;
		
		/**
		 * Message.
		 */
		public final byte[] message;
		
		/**
		 * Constructor.
		 * @param obj Source object.
		 * @param rkey The routing key of the message.
		 * @param obs Message.
		 */
		MessageReceivedEvent(Object obj, String rkey, byte[] msg)
		{
			super(obj);
			this.routingKey = rkey;
			this.message = msg;
		}
	}
	
	/**
	 * Message listener interface.
	 * @author Sathish Kumar Narayanan
	 * @author Petri Kannisto
	 */
	public interface IMessageListener extends java.util.EventListener
	{
		// Presumably, the name of the listener function is fixed.
		// How could the math tool (such as Matlab) otherwise know which function to implement?
		
		/**
		 * Listener function. The math tool implements this.
		 * @param event Event object.
		 * @exception CommunicationException Thrown if an error occurs.
		 */
		void listen(MessageReceivedEvent event) throws CommunicationException;
	}
}
