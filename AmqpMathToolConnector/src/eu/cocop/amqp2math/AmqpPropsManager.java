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
 * Holds message bus properties.
 * @author Petri Kannisto
 */
public class AmqpPropsManager
{
	// Constants
	private static final int defaultPortSecure = 5671;
	private static final int defaultPortNotSecure = 5672;
	private static final int portUnspecified = -1;
	
	private final String m_host;
	private final String m_exchange;
	private final String m_username;
	private final String m_password;
	
	private boolean m_secure = true; // Secure by default
	
	// This indicates the port if set explicitly. Otherwise, a default port is assumed.
	private int m_explicitPort = portUnspecified;
	
	
	
	/**
	 * Constructor.
	 * @param host Host.
	 * @param excName Exchange name.
	 * @param user Username.
	 * @param pwd Password.
	 */
	public AmqpPropsManager(String host, String excName, String user, String pwd)
	{
		m_host = host;
		m_exchange = excName;
		m_username = user;
		m_password = pwd;
	}
	
	
	// *** Getters ***
	
	/**
	 * Sets AMQP server URL.
	 */
	String getUrl()
	{
		int port = m_explicitPort;
		
		// Use default port?
		if (m_explicitPort == portUnspecified)
		{
			if (m_secure)
			{
				port = defaultPortSecure;
			}
			else
			{
				port = defaultPortNotSecure;
			}
		}
		
		// Choosing URI scheme
		String scheme = m_secure ? "amqps" : "amqp";
		
		// Building the URI
		return String.format("%s://%s:%s@%s:%d", scheme, m_username, m_password, m_host, port);
	}
	
	/**
	 * Sets the exchange on the AMQP server.
	 */
	String getExchange()
	{
		return m_exchange;
	}
	
	
	// *** Setters ***
	
	/**
	 * Sets the AMQP server port. If not set, a default port will be chosen.
	 * The default depends on whether the connection is secure or not.
	 * @param i Port number.
	 */
	public void setPort(int i)
	{
		m_explicitPort = i;
	}
	
	/**
	 * Sets whether a secure connection is used. The default is "true".
	 * @param sec True if secure, otherwise false.
	 */
	public void setSecure(boolean sec)
	{
		m_secure = sec;
	}
	
	/**
	 * Gets whether a secure connection is used. The default is "true".
	 * @return True if secure, otherwise false.
	 */
	boolean getSecure()
	{
		return m_secure;
	}
}
