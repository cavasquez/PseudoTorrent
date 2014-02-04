package pseudoTorrent.networking;

import java.io.IOException;
import java.io.Serializable;
import java.net.Socket;

/**
 * ThreadedSocket provides the necessary functionality to use and maintain a
 * thread-safe BasicSocket
 * @author Carlos Vasquez
 *
 */
public abstract class ThreadedSocket extends BasicSocket
{
	/******************* Class Attributes *******************/
	protected final Thread thread;
	
	/******************* Class Methods *******************/
	public ThreadedSocket(Socket socket) throws IOException 
	{
		super(socket);
		this.thread = new Thread(this);
	} /* end Constructor */
	
	/**
	 * Returns the lock used by this object to maintain synchronicity
	 * @return	the lock used by this object to maintain synchronicity
	 */
	public synchronized final Object getLock()
	{
		return this;
	} /* end getLock method */
	
	/**
	 * Starts this object in a thread.
	 */
	public final void start()
	{
		this.thread.start();
	} /* end start method */
	
	/**
	 * A thread-safe way to get a message through the socket
	 */
	protected synchronized final Serializable getSocketMessage() throws ClassNotFoundException, IOException
	{
		return super.getSocketMessage();
	} /* end getSocketMessage method */
	
	/**
	 * A thread-safe way to send a message through the socket
	 */
	protected synchronized final void sendSocketMessage(Serializable message) throws IOException
	{
		super.sendSocketMessage(message);
	} /* end sendSocketMessage method */
	
} /* end ThreadedSocket class */
