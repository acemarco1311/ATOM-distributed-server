import java.io.*; 
import java.util.*; 
import java.net.*; 
import org.w3c.dom.*; 
import javax.xml.transform.*; 
import javax.xml.parsers.*; 
import javax.xml.transform.dom.*; 
import javax.xml.transform.stream.*; 
import java.time.LocalDateTime; 

public class Feed{
    private static final int FEED_ITEM_QUEUE_FULL_SIZE = 20; // store the 20 most recent items
    private static final int FIRST_ITEM = 0; 
    //when we update the main FeedLog, then check the last time backup, if the last time backup is over 15s
    //then we will backup
    private ArrayList<String> replicaList; // server's feed log replication location
    private String feedLogLocation;  //server's feed log location
    private PriorityQueue<FeedItem> feedItemQueue; 
    private Document currentAggregatedFeed; 
    private boolean feedLock;  // the lock of feedItemQueue

    // set the FeedLock because of this case: 
    // when we have PUT(clock = 2) and GET (clock = 1) come at the same time
    // then PUT request must be executed first but because the PUT request needs more time to complete 
    // then we might have the case when the PUT request has not been completed but the GET request check 
    // the currentFeed and see empty 
    
    // Feed constructor
    // input: String (server feed log location), ArrayList<String> (location of server's feedlog replications)
    // output: none
    public Feed(String feedLogLocation, ArrayList<String> replicaList){
        this.replicaList = replicaList; 
        this.feedLock = true; //true: ready for access, false otherwise 
        this.feedLogLocation = feedLogLocation; 
        // store 20 most recent feed items 
        this.feedItemQueue = new PriorityQueue<FeedItem>(FEED_ITEM_QUEUE_FULL_SIZE, new FeedItemComparator()); 
        // restore the aggregated feed from FeedLog (if not empty)
        // currentAggregatedFeed will be null if FeedLog is empty
        XMLParser parser = new XMLParser(); 
        this.currentAggregatedFeed= parser.parseNormalTextToXMLDocument(this.feedLogLocation); 
        // restore feedItemQueue from the FeedLog (if not empty)
        if(currentAggregatedFeed != null){
            Element root = this.currentAggregatedFeed.getDocumentElement(); 
            NodeList entries = root.getElementsByTagName("entry"); 
            for(int i = 0; i < entries.getLength(); i++){
                Element currentItem = (Element)entries.item(i); 
                int contentServerID = Integer.parseInt(currentItem.getAttribute("contentServerID").trim()); 
                LocalDateTime timestamp = LocalDateTime.parse(currentItem.getAttribute("timestamp").trim()); 
                FeedItem feedItem = new FeedItem(currentItem, contentServerID, timestamp); 
                this.feedItemQueue.add(feedItem); 
            }
        }
    }
    
    // add new items (from a new PUT) to the current aggregated feed 
    // so it needs to add new FeedItem to feedItemQueue
    // then update the currentAggregatedFeed
    // then update FeedLog
    // assumption is that #PUT < #GET therefore: 
    // it's faster to update the currentAggregatedFeed after every PUT 
    // then if a GET request comes, we simple send the currentAggregatedFeed. 
    // input: XMLDocument object (containing feedItems)
    // output: none
    public synchronized void add(Document newFeed, int contentServerID, LocalDateTime timestamp){
        Element root = newFeed.getDocumentElement(); 
        NodeList newFeedItems = root.getElementsByTagName("entry"); 
        // extract each feed item (entry) from XML Document
        // add FeedItem to queue
        for(int i = 0; i < newFeedItems.getLength(); i++){
            FeedItem newItem= new FeedItem((Element)newFeedItems.item(i), contentServerID, timestamp); 
            // if this is not a valid FeedItem, do not add it to the queue, continue with the next feed item
            if(newItem.isValid() == false){
                continue; 
            }
            // remove any current feed which is the same (came from the same content server and has the same feed id) to the new one
            removeDuplicateFromQueue(newItem); 
            this.feedItemQueue.add(newItem); 
            System.out.println("Added an item from " + contentServerID + " to feedItem queue"); 
        }
        // if the size is larger than 20
        if(this.feedItemQueue.size() >= FEED_ITEM_QUEUE_FULL_SIZE){
            PriorityQueue<FeedItem> newFeedItemQueue = new PriorityQueue<FeedItem>(FEED_ITEM_QUEUE_FULL_SIZE, new FeedItemComparator()); 
            // move the first 20 feed items to new queue
            while(newFeedItemQueue.size() < 20){
                newFeedItemQueue.add(this.feedItemQueue.poll()); 
            }
            this.feedItemQueue = newFeedItemQueue; 
        }
        this.updateCurrentAggregatedFeed(); 
        this.updateFeedLog(); 
    }

    // this function take a new FeedItem and check in the Queue if there is any 
    // current FeedItem in the queue which is from the same ContentServer and has the same id
    // then remove the old one from the Queue
    // input: FeedItem: new feed item
    // output: none 
    public synchronized void removeDuplicateFromQueue(FeedItem newItem){
        while(this.feedItemQueue.contains(newItem) == true){
            // remove FeedItem in the Queue which is equal to newItem
            this.feedItemQueue.remove(newItem); 
        }
    }

    // update currentAggregatedFeed from the FeedItemQueue
    // after adding new feed items to the queue
    // input: none
    // output: none
    public synchronized void updateCurrentAggregatedFeed(){
        try{
            DocumentBuilderFactory documentFactory = DocumentBuilderFactory.newInstance(); 
            DocumentBuilder documentBuilder = documentFactory.newDocumentBuilder(); 
            Document document = documentBuilder.newDocument(); 
            Element root = document.createElement("feed"); 
            root.setAttribute("xml:lang", "en-US"); 
            root.setAttribute("xmlns", "http://www.w3.org/2005/Atom"); 
            document.appendChild(root); 
            // to update the current aggregated feed
            // we walk through the FeedItemQueue in order (based on the timestamp when the server get the FeedItem, because the newest feed should be placed on top)
            // to walk through queue in order, we need to iteratively poll() elements from the current queue 
            // after that the current FeedItemQueue will be empty, then we need to restore the queue by creating a new queue
            PriorityQueue<FeedItem> restoredQueue = new PriorityQueue<FeedItem>(20, new FeedItemComparator()); 
            // iterate through the current queue in order and add each feed item from the queue to the aggregated feed
            while(this.feedItemQueue.peek() != null){
                FeedItem currentItem = feedItemQueue.poll(); 
                Element currentEntry = currentItem.getItem(); 
                restoredQueue.add(currentItem); 
                // import the node from another DOM Tree to current DOM tree 
                Element newEntry = (Element)document.importNode(currentEntry, true); 
                root.appendChild(newEntry);
            }
            this.feedItemQueue = restoredQueue; 
            this.currentAggregatedFeed = document; 
        }
        catch(ParserConfigurationException pce){
            System.out.println("Error in updating current aggregated feed"); 
            pce.printStackTrace(); 
        }
    }

    // update the FeedLog after updating the aggregated feed, after update the FeedLog also update others backup FeedLog
    // input: no
    // output: no
    public synchronized void updateFeedLog(){
        XMLParser parser = new XMLParser(); 
        String normalText = parser.parseXMLDocumentToNormalText(this.currentAggregatedFeed); 
        try{
            FileWriter fileWriter = new FileWriter(this.feedLogLocation); 
            PrintWriter writer = new PrintWriter(fileWriter); 
            writer.print(normalText); 
            writer.flush(); 
            // backup FeedLog replications
            for(int i = 0; i < replicaList.size(); i++){
                fileWriter = new FileWriter(this.replicaList.get(i)); 
                writer = new PrintWriter(fileWriter); 
                writer.print(normalText); 
                writer.flush(); 
            }
            //close PrintWriter
            writer.close(); 
        }
        catch(Exception e){
            System.out.println("Error in updating feed log"); 
            e.printStackTrace(); 
        }

    }

    // this function will remove all FeedItem, from a particular Content Server, from FeedItem queue, current aggregated feed, and FeedLog
    // input: int: the id of the content server whose feed items need to be removed
    // output: none
    public synchronized void discard(int contentServerID){
        PriorityQueue<FeedItem> newFeedItemQueue = new PriorityQueue<FeedItem>(20, new FeedItemComparator()); 
        while(this.feedItemQueue.peek() != null){
            FeedItem currentFeedItem = this.feedItemQueue.poll(); 
            if(currentFeedItem.getContentServerID() != contentServerID){
                newFeedItemQueue.add(currentFeedItem); 
            }
        }
        // set the current feedItemQueue to the feedItemQueue after discarding
        this.feedItemQueue = newFeedItemQueue; 
        this.updateCurrentAggregatedFeed(); 
        this.updateFeedLog(); 
    }

    // getter, get the feedLogLocation of this Feed
    // input: none
    // output: String, the location of the FeedLog for this Feed
    public synchronized String getFeedLocation(){
        return this.feedLogLocation; 
    }

    //getter, get the FeedItemQueue of this Feed
    //input: none
    //output: PriorityQueue of FeedItem
    public synchronized PriorityQueue<FeedItem> getFeedItemQueue(){
        return this.feedItemQueue; 
    }

    //getter, get the current aggregated feed of this feed
    //input: none
    //output: XMLDocument object
    public synchronized Document getCurrentAggregatedFeed(){
        return this.currentAggregatedFeed; 
    }

    // getter, get the feed lock of the feed
    // input: no
    // output: boolean 
    public synchronized boolean getFeedLock(){
        return this.feedLock; 
    }

    // setter, set the feed lock
    // input: boolean (new value of the lock); 
    // output: none
    public synchronized void setFeedLock(boolean in){
        this.feedLock = in; 
    }

    public static void main(String args[]){
        
    }
 }

