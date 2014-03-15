package host;

import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;

import filechunk.ByteReadAndWrite;
import pseudoTorrent.TorrentLogger;
import pseudoTorrent.networking.*;


/**
 * The tracker will handle keeping track of various static information for a peer process.
 * It mainly will provide a way of looking up various information for connected peers through a lookup table.
 * It also will store the optimistically unchoked peer, and an array list of the top k peers to be unchoked.
 * It works in conjunction with 2 timers - one for unchoking the top k peers every p seconds, and 
 * one for unchoking a optimistically selected peer every m seconds.
 * 
 * @author Terek Arce
 *
 */

// TODO: Could be a problem with optimisticUnchokedPeer.  If not enough time passes, it will be -1.

public class Host 
{
	
	/******************* Class Attributes *******************/
	protected static int hostID;
	
	protected static Hashtable<Integer, HostEntry> lookup;
	
	public static int numPieces;
	protected static int numPrefNeighbors;
	protected static int unchokeInterval;
	protected static int optimisticUnchokeInterval;	
	protected static ArrayList <PeerRankEntry> AllRank;
	protected static ArrayList <PeerRankEntry> Choked;
	
	protected static ArrayList <Integer>  UnchokedTopK;
	protected static int optimisticUnchokedPeer;
	
	protected static BitSet bitfield;
	public static TorrentLogger log;
	public static ByteReadAndWrite file;
	
	/******************* Class Methods *******************/
	
	private Host () {}
	
	/**
	 * Setup a Tracker.  This should be created once at the start of main for a peer process.
	 * 
	 * @param numPrefNeighbors				the number of preferred neighbors as specified in Common.cfg
	 * @param unchokeInterval				the unchoke interval as specified in Common.cfg
	 * @param optimisticUnchokeInterval		the optimistic unchoke interval as specified in Common.cfg
	 * @param fileSize						the file size as specified in Common.cfg
	 * @param pieceSize						the piece size as specified in Common.cfg
	 * 
	 */
	public static synchronized void setup (int numPrefNeighbors, int unchokeInterval, int optimisticUnchokeInterval, int fileSize, int pieceSize, String logPath)
	{
		Host.lookup = new Hashtable<Integer, HostEntry>();
		Host.numPieces = fileSize / pieceSize;
		if ((fileSize % pieceSize) != 0) {numPieces++;}
		Host.numPrefNeighbors = numPrefNeighbors;
		Host.unchokeInterval = unchokeInterval;
		Host.optimisticUnchokeInterval = optimisticUnchokeInterval;
		Host.UnchokedTopK = new ArrayList <Integer> ();
		Host.Choked = new ArrayList <PeerRankEntry> ();
		Host.AllRank = new ArrayList <PeerRankEntry> ();
		Host.optimisticUnchokedPeer = -1;
		Host.hostID = -1;
		Host.bitfield = new BitSet(numPieces);
		// TODO: Host.file initialize
		
		try {
			Host.log = new TorrentLogger(hostID, logPath);
		} catch (IOException e) {
			e.printStackTrace();
		}
	} /* end Constructor */
	
	/**
	 * Returns the host's ID
	 * 
	 */
	public static int getID (){
		return Host.hostID;
	}
	
	/**
	 * Allows the host's ID to be set.  This should only be called once at the start of PeerProcess.
	 * 
	 * @param hostID	the host id of the process
	 * 
	 */
	public static void setHostID (int hostID) {
		Host.hostID = hostID; 
	}

	/**
	 * Returns the host's bitfield
	 * 
	 */	
	public static synchronized BitSet getHostBitfield() {
		return Host.bitfield;
	}

	/**
	 * Chokes the peer associated with peerID.
	 * 
	 * @param peerID	the peer id of the peer
	 * 
	 */
	public static synchronized void choke (int peerID) 
	{
		Host.lookup.get(peerID).choked = true;
	}
	
	/**
	 * Unchokes the peer associated with the peerID
	 * 
	 * @param peerID	the peer id of the peer
	 * 
	 */
	public static synchronized void unchoke (int peerID) 
	{
		Host.lookup.get(peerID).choked = false;
	}
		
	/**
	 * Returns if the peer associated with the peerID is choked or not
	 * 
	 * @param peerID	the peer id of the peer
	 * 
	 */	
	public static synchronized boolean peerIsChoked (int peerID) {
		if (Host.lookup.isEmpty()) {return true;} //TODO: do I need this?
		return Host.lookup.get(peerID).choked;
	}
	
	/**
	 * Allows the user to add a peer and associated socket to the lookup map.  
	 * 
	 * @param peerID	the peer id of the connected peer
	 * @param socket	the socket the peer is using
	 * 
	 */
	public static synchronized void add(int peerID, final TorrentSocket socket)
	{
		Host.lookup.put(peerID, new HostEntry(socket, numPieces));
	}
	
	/**
	 * Allows the host to set its bitfield to either having the file (all 1s/true)
	 * or not having the file (all 0s/false)  
	 * 
	 * @param hasFile	the value 1 represents the host has the file, 0 otherwise
	 * 
	 */
	public static void setFile(int hasFile) {
		if (hasFile == 1) {
			Host.bitfield.clear();
			Host.bitfield.flip(0, Host.numPieces);
		}
		else {
			//TODO: error
		}
	}

	/**
	 * Updates the bitfield record for a given peer using the piece info.
	 * This is overloaded.
	 * 
	 * @param peerID	the peer id of the peer
	 * @param piece		the piece that needs to be updated in the bitfield
	 * 
	 */
	public static synchronized void updatePeerBitfield (int peerID, int chunkID) 
	{
		Host.lookup.get(peerID).bitfield.set(chunkID);

		Iterator<Entry<Integer, HostEntry>> it = Host.lookup.entrySet().iterator();
        while (it.hasNext()) {
        	Map.Entry<Integer, HostEntry> entry = (Map.Entry<Integer, HostEntry>)it.next();
        	BitSet result = Host.compare(entry.getValue().bitfield, Host.bitfield);
        	if (result.isEmpty()) {
        		entry.getValue().hostInterested = false;
        	}
        	else {
        		entry.getValue().hostInterested = true;
        	}
        }
	}
	
	/**
	 * Updates the bitfield record for a given peer using the piece info.
	 * This is overloaded
	 * 
	 * @param peerID	the peer id of the peer
	 * @param bitfield	the bitset the bitfield of the peer will be set to
	 * 
	 */
	public static synchronized void updatePeerBitfield (int peerID, BitSet bitfield) {
		
		Host.lookup.get(peerID).bitfield = bitfield;
		
		Iterator<Entry<Integer, HostEntry>> it = Host.lookup.entrySet().iterator();
        while (it.hasNext()) {
        	Map.Entry<Integer, HostEntry> entry = (Map.Entry<Integer, HostEntry>)it.next();
        	BitSet result = Host.compare(entry.getValue().bitfield, Host.bitfield);
        	if (result.isEmpty()) {
        		entry.getValue().hostInterested = false;
        	}
        	else {
        		entry.getValue().hostInterested = true;
        	}
        }
	}

	/**
	 * Sets the host to be choked by this peer
	 * 
	 * @param peerID	the peer id of the peer
	 * 
	 */	
	public static synchronized void chokedBy (int peerID) 
	{
		Host.lookup.get(peerID).choking = true;
	}
	
	/**
	 * Sets the host to be unchoked by this peer
	 * 
	 * @param peerID	the peer id of the peer
	 * 
	 */	
	public static synchronized void unchokedBy (int peerID) 
	{
		Host.lookup.get(peerID).choking = false;
	}
	
	/**
	 * Returns true/false for whether or not the peer associated with the peerID is choking the host.
	 * 
	 * @param peerID	the peer id of the peer
	 * 
	 */
	public static synchronized boolean peerIsChoking (int peerID) 
	{
		return Host.lookup.get(peerID).choking;
	}
	
	/**
	 * Sets the field to let the host know the peer is interested in the its pieces.
	 * 
	 * @param peerID	the peer id of the peer
	 * 
	 */
	public static synchronized void peerIsInterested (int peerID) 
	{
		Host.lookup.get(peerID).peerInterested = true;
	}
	
	/**
	 * Sets the field to let the host know the peer is NOT interested in its pieces.
	 * 
	 * @param peerID	the peer id of the peer
	 * 
	 */
	public static synchronized void peerIsNotInterested (int peerID) 
	{
		Host.lookup.get(peerID).peerInterested = false;
	}

	/**
	 * Returns whether or not the host is interested in the peer pieces.
	 * 
	 * @param peerID	the peer id of the peer
	 * 
	 */
	public static synchronized boolean isInterested (int peerID) {
		return Host.lookup.get(peerID).hostInterested;
	}
	
	/**
	 * Sets the field to let the host know the peer is NOT interested in its pieces.
	 * 
	 * @param peerID	the peer id of the peer
	 * 
	 */
	public static ArrayList<TorrentSocket> getSocketList () {
		ArrayList<TorrentSocket> result = new ArrayList <TorrentSocket>();
		
		Iterator<Entry<Integer, HostEntry>> it = Host.lookup.entrySet().iterator();
        while (it.hasNext()) {
        	Map.Entry<Integer, HostEntry> entry = (Map.Entry<Integer, HostEntry>)it.next();
        	result.add(entry.getValue().socket);
        }
        
		return result;
	}
	
	/**
	 * Updates the bitfield record for the host using the piece info.
	 * 
	 * @param chunkID	the chunk id of the piece sent by the peer
	 * @param peerID	the peer id of the peer that is sending the piece
	 * 
	 */
	public static synchronized void updatePiece (int chunkID, int peerID) {
		Host.bitfield.set(chunkID);
		Host.lookup.get(peerID).bitsReceived += 1;
	}

	/**
	 * Returns whether or not this host believes everyone, including itself has the file.
	 * 
	 */
	public static synchronized boolean everyoneHasFile() 
	{		
		boolean result = true;
		
		if (bitfield.cardinality() == bitfield.size()) {
			result = false;
		}
		else {
			Iterator<Entry<Integer, HostEntry>> it = Host.lookup.entrySet().iterator();
	        while (it.hasNext()) {
	        	Map.Entry<Integer, HostEntry> entry = (Map.Entry<Integer, HostEntry>)it.next();
	        	BitSet toTest = entry.getValue().bitfield;
	        	if (toTest.cardinality() != toTest.size()) {
	        		result = false;
	        		break;
	        	}
	        }
		}			
		return result;
	}
	

	
	/**
	 * Returns the integer chunkID of the chunk that is being requested by the host
	 * from the peer identified by peerID.
	 * 
	 * @param peerID	the peer id of the peer that is sending the piece
	 * 
	 */
	public static synchronized int getRandomChunkID(int peerID) {
		//get random piece from among those the peer has that host dosn't have
		//TODO: modify random to ignore previous random #'s until unset.
		
		//do this by keepign another bitset and setting those bits if the piece is outstanding /requested,
		//unset the bit and modify original if 
		
		BitSet hostBitfield = bitfield;
		BitSet peerBitfield = lookup.get(peerID).bitfield;
		BitSet randomBitfield = lookup.get(peerID).randBitfield;
		
		hostBitfield.flip(0, bitfield.size());
		randomBitfield.flip(0,randomBitfield.size());
		
		
		boolean stop = false;
		int result = -1;
		while (!stop) {
		      Random randomNum = new Random();
		      int index = randomNum.nextInt(bitfield.size());
		      if (bitfield.get(index) == true) {
		    	  stop = true;
		    	  result = index;
		    	  //set other bitset to true at index
		      }
		}
		return result;		
	}
	
	public static synchronized void unsetRandomChunk(int chunkID) {
		
	}
	
	//TODO:
	private static synchronized void updateFileCompletion () {
		
	}
	
	/**
	 * Updates the AllRank, UnchokedTopK and Choked array list.  These lists provide the user with access
	 * to which peers are to be unchoked for a give k time interval and are utilized in determining
	 * the optimistic unchoked peer.  This method should only be used by the associated UnchokeTask for timing
	 * purposes.
	 * 
	 */
	//TODO: hasEveryoneRecieved file method?
	//TODO: if I have the whole file unchoke everyone;
	protected static synchronized void updateTopK () {
		Host.AllRank.clear();
		Host.UnchokedTopK.clear();
		Host.Choked.clear();
        
		Iterator<Entry<Integer, HostEntry>> it = Host.lookup.entrySet().iterator();
        while (it.hasNext()) {
        	Map.Entry<Integer, HostEntry> entry = (Map.Entry<Integer, HostEntry>)it.next();
        	Host.AllRank.add(new PeerRankEntry(entry.getKey(), entry.getValue().bitsReceived, entry.getValue().peerInterested));
        }

        Collections.sort(Host.AllRank, PeerRankEntry.DESCENDING_COMPARATOR);
        
        int i = 0;
        for (PeerRankEntry e : Host.AllRank) {
        	if ((i < Host.numPrefNeighbors) && (e.isInterested)){
        		Host.UnchokedTopK.add(e.peerID);
        		i++;
        	}
        }
        
        for (int j = Host.numPrefNeighbors; j < Host.AllRank.size(); j++) {
        	if (Host.AllRank.get(j).isInterested) {
            	Host.Choked.add(Host.AllRank.get(j));
        	}
        }
        
		Iterator<Entry<Integer, HostEntry>> it2 = Host.lookup.entrySet().iterator();
        while (it2.hasNext()) {
        	Map.Entry<Integer, HostEntry> entry = (Map.Entry<Integer, HostEntry>)it2.next();
        	entry.getValue().bitsReceived = 0;			//reset bitsRecieved
        }
        
        //TODO: at this point the unchokedTopK and choked lists are done,
        //so go through the lookup and if the unchoked value at the peer changes send a choke/unchoke message
        //then update the lookup table acordingly updating who is choked and unchoked.
        
	}
	
	private static synchronized BitSet compare(BitSet peer, BitSet host) {
		BitSet host1 = host;
		BitSet peer1 = peer;
		host1.flip(0, host1.size());
		host1.and(peer1);
		return peer1;	
	}
	
	/**
	 * Returns the optimistically unchoked peerID for this m interval time frame.
	 * Returns a -1 if there is no optimistically unchoked peer currently.
	 * 
	 */
	private static synchronized int getOptimisticPeer () {
		return Host.optimisticUnchokedPeer;
	}
	
	/**
	 * Determines the current optimistically unchoked peer for this m interval time frame.
	 * This method should only be used by the associated OptUnchokeTask for timing purposes.
	 * 
	 */
	protected static synchronized void findOptimisticPeer () {
		Random randomGenerator = new Random();
		
		if (!Host.Choked.isEmpty()) {
			int index = randomGenerator.nextInt(Host.Choked.size());
	        Host.optimisticUnchokedPeer = Host.Choked.get(index).peerID;
		}
		else {
			Host.optimisticUnchokedPeer = -1;
		}
		//send msgs that are needed to unchoke the optimistic guy
	}
	
	/**
	 * Returns if he peer associated with the peerID has the complete file.
	 * 
	 * @param peerID	the peer id of the peer
	 * 
	 */
	private static synchronized boolean hasFile (int peerID) 
	{
		boolean hasFile = false;
		BitSet temp = (BitSet) Host.lookup.get(peerID).bitfield.clone();
		if (temp.cardinality() == temp.size()) {hasFile = true;}
		return hasFile;
		
	}
	
	/**
	 * Returns the number of bits recieved from the peer associated with the peerID
	 * 
	 * @param peerID	the peer id of the peer
	 * 
	 */
	private synchronized int getBitsRecieved (int peerID) 
	{
		return Host.lookup.get(peerID).bitsReceived;
	}
	
	/**
	 * Adds some number of bits to the total number of bits recieved in the last time period 
	 * from the peer associated with the peerID.  This is used in determining the download 
	 * rate from the peers.
	 * 
	 * @param peerID	the peer id of the peer
	 * @param numBits	the number of bits to add to this peer
	 * 
	 */
	private static synchronized void addBytes (int peerID, int numBytes) {
		Host.lookup.get(peerID).bitsReceived += numBytes;
	}
	
	/**
	 * Returns whether or not the peer is interested in the host pieces.
	 * 
	 * @param peerID	the peer id of the peer
	 * 
	 */
	private static synchronized boolean isPeerInterested (int peerID) {
		return Host.lookup.get(peerID).peerInterested;
	}
	
	/**
	 * Sets host as being interested in the peer.
	 * 
	 * @param peerID	the peer id of the peer
	 * 
	 */
	private static synchronized boolean isHostInterested (int peerID) {
		return Host.lookup.get(peerID).hostInterested;
	}
	
}
