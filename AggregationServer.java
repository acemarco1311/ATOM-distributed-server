import java.io.*; 
import java.util.*; 
import java.net.*; 
import org.w3c.dom.*; 
import javax.xml.transform.*; 
import javax.xml.parsers.*; 
import java.time.LocalDateTime; 
import java.util.Map.*; 
import java.util.concurrent.atomic.AtomicBoolean; 


// this thread will run the server to receive request and add the request (GET/PUT) to request queue
// or create another thread to handle request if the request is a heartbeat
public class AggregationServer implements Runnable{
    private static final String DEFAULT_PORT = "4567"; 
    private static final long MAX_INTERVAL_BETWEEN_COMMUNICATION = 13; 
    // every content server must maintain the communication within 13 seconds; 
    // actually it should be 12, but we consider 1 second delay might happen in the server while processing many request
    private String serverName; 
    private int serverPort; 
    private LamportClock clock; 
    private String clockLocation; // backup file for server clock
    private ServerSocket serverSocket; 
    private String feedLogLocation; // location of main FeedLog
    private Feed currentFeed; 
    private HashMap<Integer, LocalDateTime> contentServerMonitor; // keep track of connected content server to discard items from unactive content server
    private boolean monitorLock; // lock of contentServerMonitor HashMap
    private String contentServerMonitorLocation; // back up of contentServerMonitor
    private PriorityQueue<HashMap<String, Object>> requestQueue; 
    private static final int REQUEST_QUEUE_CAPACITY = 20; //executing max 20 requests at a time
    private AtomicBoolean queueLock; // lock for the request queue as 2 threads: this thread and ServerRequestHandler access to the queue
    private ArrayList<String> replicaList; // paths of replications


    // server constructor
    // input: no
    // output: no
    public AggregationServer(){
        this.queueLock = new AtomicBoolean(true); 
        this.serverName = "localhost"; // run server on local machine
        this.clock = new LamportClock(); 
        this.clockLocation = "AggregationServerFiles/LamportClock.txt"; 
        // restore clock of server if clock file not empty
        File file = new File(this.clockLocation); 
        if(file.length() != 0){
            try{
                Scanner reader = new Scanner(file); 
                while(reader.hasNextLine()){
                    int clockValue = reader.nextInt(); 
                    this.clock.setClockValue(clockValue); 
                }
            }
            catch(FileNotFoundException e){
                System.out.println("Error in restoring Lamport clock"); 
                e.printStackTrace(); 
            }
        }
        // provide the path for FeedLog
        this.feedLogLocation = "AggregationServerFiles/FeedLog.txt"; 
        // provide the path of FeedLog replication
        this.replicaList = new ArrayList<String>(); 
        this.replicaList.add("AggregationServerFiles/FeedLog_rep_one.txt"); 
        this.replicaList.add("AggregationServerFiles/FeedLog_rep_two.txt"); 
        this.replicaList.add("AggregationServerFiles/FeedLog_rep_three.txt"); 
        // init the current Feed, also restore the currentFeed from the FeedLog if the FeedLog is not empty (done by Feed construtor)
        this.currentFeed = new Feed(this.feedLogLocation, this.replicaList); 
        // keep track of content servers, <content server id> mapped to <timestamp of last communication> 
        this.contentServerMonitorLocation = "AggregationServerFiles/ContentServerMonitor.txt"; 
        // restored the content server monitor based on log
        if(this.restoreContentServerMonitor() == null){
            this.contentServerMonitor = new HashMap<Integer, LocalDateTime>(); 
        }
        else{
            this.contentServerMonitor = this.restoreContentServerMonitor(); 
        }
        // init the lock for content server monitor
        this.monitorLock = true; 
        // create a customized comparator for priority of hash map based on the lamport clock of requests
        this.requestQueue = new PriorityQueue<HashMap<String, Object>>(REQUEST_QUEUE_CAPACITY, new RequestComparator()); 
        boolean complete = false; 
        String startingPort = ""; // set starting port
        Scanner keyboard = new Scanner(System.in); 
        // asking for the port of Aggregation Server
        while (complete == false){
            System.out.println("Enter the starting port (press Enter if using the default port 4567): "); 
            try{
                startingPort = keyboard.nextLine(); 
                if (startingPort.isEmpty() == true){
                    startingPort = DEFAULT_PORT; // use default port if get empty input
                }
                this.serverPort = Integer.parseInt(startingPort); 
                complete = true; 
            } catch(Exception e){
                System.out.println("Error, try again."); 
            }
        }
        try{
            // set Server Socket for listening to requests
            this.serverSocket = new ServerSocket(this.serverPort); 
            System.out.println("Aggregation Server started."); 
        } catch (Exception e){
            System.out.println("Unable to  start Server Socket."); 
            e.printStackTrace(); 
        }
    }

    // another server constructor, instead of asking the user to type the port
    // take a port as a parameter, this is used for easier testing
    // input: Integer: the port that the server will listen to, String: the name of server 
    // output: no
    public AggregationServer(String hostname, int port){
        this.queueLock = new AtomicBoolean(true); 
        this.serverName = hostname; // run server on local machine
        this.clock = new LamportClock(); 
        this.clockLocation = "AggregationServerFiles/LamportClock.txt"; 
        // restore clock of server if clock file not empty
        File file = new File(this.clockLocation); 
        if(file.length() != 0){
            try{
                Scanner reader = new Scanner(file); 
                while(reader.hasNextLine()){
                    int clockValue = reader.nextInt(); 
                    this.clock.setClockValue(clockValue); 
                }
            }
            catch(FileNotFoundException e){
                System.out.println("Error in restoring Lamport clock"); 
                e.printStackTrace(); 
            }
        }
        // provide the path for FeedLog
        this.feedLogLocation = "AggregationServerFiles/FeedLog.txt"; 
        // provide the path of FeedLog replication
        this.replicaList = new ArrayList<String>(); 
        this.replicaList.add("AggregationServerFiles/FeedLog_rep_one.txt"); 
        this.replicaList.add("AggregationServerFiles/FeedLog_rep_two.txt"); 
        this.replicaList.add("AggregationServerFiles/FeedLog_rep_three.txt"); 
        // init the current Feed, also restore the currentFeed from the FeedLog if the FeedLog is not empty (done by Feed construtor)
        this.currentFeed = new Feed(this.feedLogLocation, this.replicaList); 
        // keep track of content servers, <content server id> mapped to <timestamp of last communication> 
        this.contentServerMonitorLocation = "AggregationServerFiles/ContentServerMonitor.txt"; 
        // restored the content server monitor based on log
        if(this.restoreContentServerMonitor() == null){
            this.contentServerMonitor = new HashMap<Integer, LocalDateTime>(); 
        }
        else{
            this.contentServerMonitor = this.restoreContentServerMonitor(); 
        }
        // init the lock for content server monitor
        this.monitorLock = true; 
        // create a customized comparator for priority of hash map based on the lamport clock of requests
        this.requestQueue = new PriorityQueue<HashMap<String, Object>>(REQUEST_QUEUE_CAPACITY, new RequestComparator()); 
        this.serverPort = port; 
        try{
            // set Server Socket for listening to requests
            this.serverSocket = new ServerSocket(this.serverPort); 
            System.out.println("Aggregation Server started."); 
        } catch (Exception e){
            System.out.println("Unable to  start Server Socket."); 
            e.printStackTrace(); 
        }
    }

    // this function will restore the content server monitor from the content server monitor log (in file) 
    // this is used when the Aggregation Server is created (or restarted) 
    // input: no (the path of content server monitor log is already provided in server's constructor) 
    // output: no (but the content server monitor (HashMap) will be initiated based on the log) 
    public synchronized HashMap<Integer, LocalDateTime> restoreContentServerMonitor(){
        HashMap<Integer, LocalDateTime> restoredMonitor = new HashMap<Integer, LocalDateTime>(); 
        try{
            File monitorLog = new File(this.contentServerMonitorLocation); 
            // if the file is empty, return null
            if(monitorLog.length() == 0){
                return null; 
            }
            Scanner reader = new Scanner(monitorLog); 
            // read each line in the log which is in form: contentServerID:lastTimeCommunication
            while(reader.hasNextLine()){
                String currentLine = reader.nextLine(); 
                int contentServerID = Integer.parseInt(currentLine.substring(0, currentLine.indexOf(':'))); 
                LocalDateTime timestamp = LocalDateTime.parse(currentLine.substring(currentLine.indexOf(':') + 1)); 
                // add information of each content server to the monitor (HashMap)
                restoredMonitor.put(contentServerID, timestamp); 
            }
        }
        catch (Exception e){
            System.out.println("Error in restoring ContentServerMonitor"); 
            e.printStackTrace(); 
        }
        return restoredMonitor; 
    }

    // the Content Server Monitor is a hash map containing: content server id (key) : last time of communication (value)
    // this function will add one more content server to the list (for checking heartbeat) when it made its first PUT request 
    // if the content server is already in the monitor, we will update it with the time of the latest communication 
    // input: Integer (id of new content server), LocalDateTime (the time when the server get the content server's first PUT request) 
    // output: no
    public synchronized void updateContentServerMonitor(int contentServerID, LocalDateTime lastCommunication){
        // use while true to wait for the lock to released if some thread is using the monitor
        while(true){
            // when the lock is released, set it to false so that other thread will not access to monitor object 
            if(this.monitorLock == true){
                this.monitorLock = false; 
                // if the content server is already in the monitor
                if(this.contentServerMonitor.containsKey(contentServerID) == true){
                    this.contentServerMonitor.remove(contentServerID); 
                }
                //update it with the time of latest communication
                this.contentServerMonitor.put(contentServerID, lastCommunication); 
                // release the lock of updateContentServerMonitorLog to run
                this.monitorLock = true; 
                this.updateContentServerMonitorLog(); 
                break; 
            }
        }
    }

    // this function is used for updating the Content Monitor log (in file)
    // the Content Monitor Log will be updated based on the Content Monitor (HashMap in memory) 
    // so we update what is currently in the memory to the file
    // input: no
    // output: no
    public synchronized void updateContentServerMonitorLog(){
        // wait for monitor's lock
        while(true){
            if(this.monitorLock == true){
                // lock the monitor so that other threads will not interrupt. 
                this.monitorLock = false; 
                String text = ""; 
                Iterator entries = this.contentServerMonitor.entrySet().iterator(); 
                // iterate through the HashMap (monitor)
                while(entries.hasNext()){
                    Entry thisEntry = (Entry)entries.next(); 
                    int key = (int)thisEntry.getKey(); 
                    String value = thisEntry.getValue().toString(); 
                    text += key + ":" + value; 
                    text += "\n"; 
                }
                //update the log with the elements from the monitor
                try{
                    FileWriter fileWriter = new FileWriter(this.contentServerMonitorLocation); 
                    PrintWriter writer = new PrintWriter(fileWriter); 
                    writer.print(text); 
                    writer.close(); 
                }
                catch(Exception e){
                    System.out.println("Error in updating ContentServerMonitorLocation"); 
                    e.printStackTrace(); 
                }
                // release the lock after working
                this.monitorLock = true; 
                break; 
            }
        }
    }

    // this function is used for repeatedly checking the connecting content server list
    // if any of the content server hasn't been communicating within 13 seconds (12 seconds + 1 seconds for potential delay) 
    // we will remove it out of the list, and also remove all the feed item of that content server
    // input: no
    // output: no
    public synchronized void checkContentServerMonitor(){
        LocalDateTime timeCheck = LocalDateTime.now(); 
        while(true){
            if(this.monitorLock == true){
                this.monitorLock = false; 
                ArrayList<Integer> unactiveContentServer = new ArrayList<Integer>(); 
                Iterator entries = this.contentServerMonitor.entrySet().iterator(); 
                // iterate through the list of content server in monitor
                while(entries.hasNext()){
                    Entry thisEntry = (Entry)entries.next(); 
                    int contentServerID = (int)thisEntry.getKey(); 
                    LocalDateTime lastCommunication = (LocalDateTime)thisEntry.getValue(); 
                    // if any content server has not been active within 13 seconds (12 seconds based on the description + 1 second prepared for some potential delay)
                    if(timeCheck.isAfter(lastCommunication.plusSeconds(MAX_INTERVAL_BETWEEN_COMMUNICATION)) == true){
                        // add it to the array of unactive content server
                        unactiveContentServer.add(contentServerID); 
                    }
                }
                // remove the unactive content servers from the monitor list
                for(int i = 0; i < unactiveContentServer.size(); i++){
                    this.contentServerMonitor.remove(unactiveContentServer.get(i)); 
                }
                this.monitorLock = true; 
                // update the log because we made some changes in the monitor (in memory)
                this.updateContentServerMonitorLog(); 
                // remove FeedItems of all unactive content server
                while(true){
                    if(this.getFeed().getFeedLock() == true){
                        this.getFeed().setFeedLock(false); 
                        for(int i=0; i < unactiveContentServer.size(); i++){
                            this.getFeed().discard(unactiveContentServer.get(i)); 
                        }
                        this.getFeed().setFeedLock(true); 
                        break; 
                    }
                }
                break; 
            }
        }
    }


    //run the thread to start the server
    //input: no 
    //output: no
    @Override
    public void run(){
        this.runServer(); 
    }


    // run the aggregation server to get requests
    // input: no 
    // output: no
    // this function listen on the port, if got any request, if the request is GET/PUT
    // put the request to requestQueue then create a new thread to respond to the request
    //
    public void runServer(){
        while(true){
            try{
                System.out.println("Waiting for clients."); 
                //block and wait for clients to connect
                Socket clientSocket = this.serverSocket.accept(); 
                LocalDateTime timestamp = LocalDateTime.now(); 
                // get the timestamp when receive request to avoid incorrect timestamp because of delay or waiting
                ObjectOutputStream os = new ObjectOutputStream(clientSocket.getOutputStream());  
                ObjectInputStream is = new ObjectInputStream(clientSocket.getInputStream()); 
                // convert request to HashMap, API: the requests from\
                // clients or content servers MUST be sent to the 
                // Aggregation Server in the form of a HashMap
                HashMap<String, Object> request; 
                // handle the failure when client send wrong request (not in hashmap)
                Object reqObj = is.readObject(); 
                if(reqObj instanceof HashMap == true){
                    request = (HashMap<String, Object>)reqObj; 
                }
                else request = null; 
                // check if this is a valid heartbeat request, if yes, create thread to respond
                // otherwise, put this request in queue and treat it like an invalid request later
                if(request != null && request.containsKey("Request-Type") == true && request.get("Request-Type").equals("Heartbeat") == true && 
                        request.containsKey("ID") == true && request.get("ID") != null && request.containsKey("Clock") == true && 
                        request.get("Clock") != null){
                    ServerHeartbeatRequestHandler handler = new ServerHeartbeatRequestHandler(this, clientSocket, is, os, request, timestamp); 
                    Thread executingRequest = new Thread(handler, "Executing heartbeat request"); 
                    executingRequest.start(); 
                }
                else{

                    //create new item to requestQueue
                    HashMap<String, Object> req = new HashMap<String, Object>(); 
                    // each element in the requestQueue is a HashMap containing: the request (HashMap), socket (Socket), in(ObjectInputStream obj on
                    // the socket), out(ObjectOutputStream obj on the socket)
                    req.put("Socket", clientSocket); 
                    req.put("out", os); 
                    req.put("in", is); 
                    req.put("request", request);  
                    req.put("timestamp", timestamp); 
                    // add new item (HashMap) to requestQueue but need to check the lock first
                    // if the queue is busy, wait until the lock is release and add new element to the queue
                    // if the queue is full, then the server will wait until all the requests has been responded
                    while(true){
                        if(this.getQueueLock().get() && this.getRequestQueue().size() < this.REQUEST_QUEUE_CAPACITY){
                            this.requestQueue.add(req); 
                            queueLock.getAndSet(false); 
                            break;
                        }
                    }
                }
            }
            // when client/content server try to check the connection in their constructor
            // they connect to the server (without sending anything) and close their socket
            // SocketException will be thrown here
            // this is the ONLY case where client/content server close the socket before getting response
            // so this is an acceptable behavior in my implementation
            catch(SocketException se){

            }
            // another minor error when client/content server check connection by trying to connect without sending anything
            // this will not affect the other server's behaviors or operations
            // we do nothing in here too
            catch (EOFException eof){

            }
            catch (Exception e){
                System.out.println("Connection failed."); 
                e.printStackTrace(); 
            }
        }
    }

    //check if the FeedLog.txt is empty, this is used for determining 
    //the first content server that will receive 201 _ HTTP_CREATED
    //input: none
    //output: boolean: true if feedLog is empty, false otherwise
    public synchronized boolean isFeedLogEmpty(){
        File feedLog = new File(this.feedLogLocation); 
        if(feedLog.length() == 0){
            return true; 
        }
        return false; 
    }

    //getters and setters (prepared here), unnecessary will be removed later

    public synchronized LamportClock getClock(){ 
        return this.clock;  
    }

    // when the server receive a request: we update the clock of server = max(sender clock, server clock) + 1
    // this function will update and backup clock of server when server receive a request and is about to send response
    // this function also update the lamport clock log in server file for backup
    // input: Integer: the value of request sender clock
    // output: Integer: the value of server clock after updating and incrementing
    public synchronized int updateAndBackUpClock(int externalClockValue){
        // update clock when receive a request
        this.clock.updateClock(externalClockValue); 
        // update clock backup 
        try{
            FileWriter fileWriter = new FileWriter(this.clockLocation); 
            PrintWriter writer = new PrintWriter(fileWriter); 
            writer.print(this.clock.getClockValue()); 
            writer.close(); 
        }
        catch(Exception e){
            System.out.println("Error in updating Lamport Clock"); 
            e.printStackTrace(); 
        }
        return this.clock.getClockValue(); 
    }


    // getter: get the request queue of server
    // input: no
    // output: PriorityQueue<HashMap<String, Object>>: request queue
    public synchronized PriorityQueue<HashMap<String, Object>> getRequestQueue(){
        return this.requestQueue; 
    }

    // getter: get the path of the Feed log
    // input: no 
    // output: String: the location of the server's feedlog
    public synchronized String getFeedLogLocation(){
        return this.feedLogLocation; 
    }

    // getter: get the Feed Object of the server
    // input: no 
    // output: Feed object of the serevr
    public synchronized Feed getFeed(){
        return this.currentFeed; 
    }

    //getter: get the content server monitor of the server which is a HashMap containing: 
    //contentServerID(key) -> last time communication of the content server(value); 
    //input: no
    //output: HashMap<Integer, LocalDateTime>: server's content servers monitor
    public synchronized HashMap<Integer, LocalDateTime> getContentServerMonitor(){
        return this.contentServerMonitor; 
    }

    // getter: get the location of content server monitor log
    // input: no 
    // output: String: the location of content server monitor log in server's files
    public String getContentServerMonitorLocation(){
        return this.contentServerMonitorLocation; 
    }

    // getter: get the request queue lock
    // input: no
    // output: AtomicBoolean 
    public synchronized AtomicBoolean getQueueLock(){
        return this.queueLock; 
    }

    // getter: get the list of server's FeedLog replication locations
    // input: no 
    // output: ArrayList<String> 
    public ArrayList<String> getReplicaList(){
        return this.replicaList; 
    }


    public static void main(String args[]){
        // normal mode: run the server and handle requests
        if(args[0].equals("normal")){
            // create the server
            AggregationServer server = new AggregationServer(); 
            // create content server monitor
            ContentServerMonitor contentServerMonitor = new ContentServerMonitor(server); 
            // create request handler
            ServerRequestHandler requestHandler = new ServerRequestHandler(server); 
            Thread runningServer = new Thread(server); 
            Thread executingRequests = new Thread(requestHandler, "Handling requests in Queue"); 
            Thread checkingContentServer = new Thread(contentServerMonitor); 
            // run request handler thread, content server monitor thread and also run the server
            executingRequests.start(); 
            checkingContentServer.start(); 
            runningServer.start(); 
        }
        // test case 1 
        else if(args[0].equals("testing") && args[1].equals("1")){
            LocalDateTime startTime = LocalDateTime.now(); 
            // create the server
            AggregationServer server = new AggregationServer("localhost", 4567); 
            // create content server monitor
            ContentServerMonitor contentServerMonitor = new ContentServerMonitor(server); 
            // create request handler
            ServerRequestHandler requestHandler = new ServerRequestHandler(server); 
            Thread runningServer = new Thread(server); 
            Thread executingRequests = new Thread(requestHandler, "Handling requests in Queue"); 
            Thread checkingContentServer = new Thread(contentServerMonitor); 
            // run request handler thread, content server monitor thread and also run the server
            executingRequests.start(); 
            checkingContentServer.start(); 
            runningServer.start(); 
            while(true){
                if(LocalDateTime.now().isAfter(startTime.plusSeconds(4))){
                    System.exit(0); 
                }
            }
        }
        // test case 2 
        else if(args[0].equals("testing") && args[1].equals("2")){
            // create the server
            AggregationServer server = new AggregationServer("localhost", 4567); 
            // create content server monitor
            ContentServerMonitor contentServerMonitor = new ContentServerMonitor(server); 
            // create request handler
            ServerRequestHandler requestHandler = new ServerRequestHandler(server); 
            Thread runningServer = new Thread(server); 
            Thread executingRequests = new Thread(requestHandler, "Handling requests in Queue"); 
            Thread checkingContentServer = new Thread(contentServerMonitor); 
            // run request handler thread, content server monitor thread and also run the server
            executingRequests.start(); 
            checkingContentServer.start(); 
            runningServer.start(); 
            // close server after 8 seconds
            try{
                Thread.sleep(8000); 
            }
            catch(Exception e){

            }
            System.exit(0); 
        }
        // test case 3 
        else if(args[0].equals("testing") && args[1].equals("3")){
            // create the server
            AggregationServer server = new AggregationServer("localhost", 4567); 
            // create content server monitor
            ContentServerMonitor contentServerMonitor = new ContentServerMonitor(server); 
            // create request handler
            ServerRequestHandler requestHandler = new ServerRequestHandler(server); 
            Thread runningServer = new Thread(server); 
            Thread executingRequests = new Thread(requestHandler, "Handling requests in Queue"); 
            Thread checkingContentServer = new Thread(contentServerMonitor); 
            // run request handler thread, content server monitor thread and also run the server
            executingRequests.start(); 
            checkingContentServer.start(); 
            runningServer.start(); 
            // end server after 12s
            try{
                Thread.sleep(12000); 
            }
            catch(Exception e){

            }
            System.exit(0); 
        }
        // test case 4 
        else if(args[0].equals("testing") && args[1].equals("4")){
            // create the server
            AggregationServer server = new AggregationServer("localhost", 4567); 
            // create content server monitor
            ContentServerMonitor contentServerMonitor = new ContentServerMonitor(server); 
            // create request handler
            ServerRequestHandler requestHandler = new ServerRequestHandler(server); 
            Thread runningServer = new Thread(server); 
            Thread executingRequests = new Thread(requestHandler, "Handling requests in Queue"); 
            Thread checkingContentServer = new Thread(contentServerMonitor); 
            // run request handler thread, content server monitor thread and also run the server
            executingRequests.start(); 
            checkingContentServer.start(); 
            runningServer.start(); 
            // close server after 25 seconds 
            // just to make sure that the AS will be closed after the content server
            try{
                Thread.sleep(25000); 
            }
            catch(Exception e){

            }
            System.exit(0); 
        }
        // test case 5
        else if(args[0].equals("testing") && args[1].equals("5")){
            // create the server
            AggregationServer server = new AggregationServer("localhost", 4567); 
            // create content server monitor
            ContentServerMonitor contentServerMonitor = new ContentServerMonitor(server); 
            // create request handler
            ServerRequestHandler requestHandler = new ServerRequestHandler(server); 
            Thread runningServer = new Thread(server); 
            Thread executingRequests = new Thread(requestHandler, "Handling requests in Queue"); 
            Thread checkingContentServer = new Thread(contentServerMonitor); 
            // run request handler thread, content server monitor thread and also run the server
            executingRequests.start(); 
            checkingContentServer.start(); 
            runningServer.start(); 
            // close server after 25 seconds just to make sure that
            // the AS will be closed after the content servers
            try{
                Thread.sleep(25000); 
            }
            catch(Exception e){

            }
            System.exit(0); 
        }
        // test case 6
        else if(args[0].equals("testing") && args[1].equals("6")){
            // create the server
            AggregationServer server = new AggregationServer("localhost", 4567); 
            // create content server monitor
            ContentServerMonitor contentServerMonitor = new ContentServerMonitor(server); 
            // create request handler
            ServerRequestHandler requestHandler = new ServerRequestHandler(server); 
            Thread runningServer = new Thread(server); 
            Thread executingRequests = new Thread(requestHandler, "Handling requests in Queue"); 
            Thread checkingContentServer = new Thread(contentServerMonitor); 
            // run request handler thread, content server monitor thread and also run the server
            executingRequests.start(); 
            checkingContentServer.start(); 
            runningServer.start(); 
            // close the server after 6 seconds (expected time when the test case has been done) 
            try{
                Thread.sleep(6000); 
            }
            catch(Exception e){

            }
            System.exit(0); 
        }
        // test case 7
        else if(args[0].equals("testing") && args[1].equals("7")){
            // create the server
            AggregationServer server = new AggregationServer("localhost", 4567); 
            // create content server monitor
            ContentServerMonitor contentServerMonitor = new ContentServerMonitor(server); 
            // create request handler
            ServerRequestHandler requestHandler = new ServerRequestHandler(server); 
            Thread runningServer = new Thread(server); 
            Thread executingRequests = new Thread(requestHandler, "Handling requests in Queue"); 
            Thread checkingContentServer = new Thread(contentServerMonitor); 
            // run request handler thread, content server monitor thread and also run the server
            executingRequests.start(); 
            checkingContentServer.start(); 
            runningServer.start(); 
            // close server after 25 seconds just to make sure that
            // the AS will be closed after the content servers
            try{
                Thread.sleep(25000); 
            }
            catch(Exception e){

            }
            System.exit(0); 
        }
        // test case 8
        else if(args[0].equals("testing") && args[1].equals("8")){
            // create the server
            AggregationServer server = new AggregationServer("localhost", 4567); 
            // create content server monitor
            ContentServerMonitor contentServerMonitor = new ContentServerMonitor(server); 
            // create request handler
            ServerRequestHandler requestHandler = new ServerRequestHandler(server); 
            Thread runningServer = new Thread(server); 
            Thread executingRequests = new Thread(requestHandler, "Handling requests in Queue"); 
            Thread checkingContentServer = new Thread(contentServerMonitor); 
            // run request handler thread, content server monitor thread and also run the server
            executingRequests.start(); 
            checkingContentServer.start(); 
            runningServer.start(); 
            // close server after 4 seconds
            try{
                Thread.sleep(4000); 
            }
            catch(Exception e){

            }
            System.exit(0); 
        }
        // test case 9
        else if(args[0].equals("testing") && args[1].equals("9")){
            // create the server
            AggregationServer server = new AggregationServer("localhost", 4567); 
            // create content server monitor
            ContentServerMonitor contentServerMonitor = new ContentServerMonitor(server); 
            // create request handler
            ServerRequestHandler requestHandler = new ServerRequestHandler(server); 
            Thread runningServer = new Thread(server); 
            Thread executingRequests = new Thread(requestHandler, "Handling requests in Queue"); 
            Thread checkingContentServer = new Thread(contentServerMonitor); 
            // run request handler thread, content server monitor thread and also run the server
            executingRequests.start(); 
            checkingContentServer.start(); 
            runningServer.start(); 
            // close server after 4 seconds
            try{
                Thread.sleep(4000); 
            }
            catch(Exception e){

            }
            System.exit(0); 
        }
        // test case 11
        else if(args[0].equals("testing") && args[1].equals("11")){
            // create the server, only run the server without responding to requests in this test case
            AggregationServer server = new AggregationServer("localhost", 4567); 
            Thread runningServer = new Thread(server); 
            runningServer.start(); 
            // close server after 20 seconds when the client has given up 
            // the first GET takes 4 seconds
            // then retry 3 times takes about other 12 seconds
            // so this test case should be ended after around 16 seconds
            // therefore the server can be closed after 20 seconds just for sure
            try{
                Thread.sleep(20000); 
            }
            catch(Exception e){

            }
            System.exit(0); 
        }
        // test case 12
        else if(args[0].equals("testing") && args[1].equals("12")){
            // create the server listen to requests
            AggregationServer server = new AggregationServer("localhost", 4567); 
            Thread runningServer = new Thread(server); 
            runningServer.start(); 
            // wait for 14 seconds (the expected time when the client retrying for the third time)
            try{
                Thread.sleep(14000); 
            }
            catch(Exception e){

            }
            // create thread responding to request
            ServerRequestHandler requestHandler = new ServerRequestHandler(server); 
            Thread executingRequest = new Thread(requestHandler); 
            executingRequest.start();
            // close the server 5 seconds after respond to the client request
            try{
                Thread.sleep(5000); 
            }
            catch(Exception e){

            }
            System.exit(0); 
        }
        // test case 14
        else if(args[0].equals("testing") && args[1].equals("14")){
            // create the server, only run the server without responding to requests in this test case
            AggregationServer server = new AggregationServer("localhost", 4567); 
            Thread runningServer = new Thread(server); 
            runningServer.start(); 
            // close server after 20 seconds when the content server has given up 
            // the first PUT takes 4 seconds
            // then retry 3 times takes about other 12 seconds
            // so this test case should be ended after around 16 seconds
            // therefore the server can be closed after 20 seconds just for sure
            try{
                Thread.sleep(20000); 
            }
            catch(Exception e){

            }
            System.exit(0); 
        }
        // test case 15
        else if(args[0].equals("testing") && args[1].equals("15")){
            // create the server listen to requests
            AggregationServer server = new AggregationServer("localhost", 4567); 
            Thread runningServer = new Thread(server); 
            runningServer.start(); 
            // wait for 14 seconds (the expected time when the content server retrying for the third time)
            try{
                Thread.sleep(14000); 
            }
            catch(Exception e){

            }
            // create thread responding to request
            ServerRequestHandler requestHandler = new ServerRequestHandler(server); 
            Thread executingRequest = new Thread(requestHandler); 
            executingRequest.start();
            // close the server 5 seconds after respond to the content server request
            try{
                Thread.sleep(5000); 
            }
            catch(Exception e){

            }
            System.exit(0); 
        }
        // test case 16
        else if(args[0].equals("testing") && args[1].equals("16")){
            AggregationServer server = new AggregationServer("localhost", 4567); 
            // create content server monitor
            ContentServerMonitor contentServerMonitor = new ContentServerMonitor(server); 
            // create request handler
            ServerRequestHandler requestHandler = new ServerRequestHandler(server); 
            Thread runningServer = new Thread(server); 
            Thread executingRequests = new Thread(requestHandler, "Handling requests in Queue"); 
            Thread checkingContentServer = new Thread(contentServerMonitor); 
            // run request handler thread, content server monitor thread and also run the server
            executingRequests.start(); 
            checkingContentServer.start(); 
            runningServer.start(); 
            // close the server 5 seconds after respond to the content server request
            try{
                Thread.sleep(5000); 
            }
            catch(Exception e){

            }
            System.exit(0); 
        }
        // test case 17
        else if(args[0].equals("testing") && args[1].equals("17")){
            AggregationServer server = new AggregationServer("localhost", 4567); 
            // create content server monitor
            ContentServerMonitor contentServerMonitor = new ContentServerMonitor(server); 
            // create request handler
            ServerRequestHandler requestHandler = new ServerRequestHandler(server); 
            Thread runningServer = new Thread(server); 
            Thread executingRequests = new Thread(requestHandler, "Handling requests in Queue"); 
            Thread checkingContentServer = new Thread(contentServerMonitor); 
            // run request handler thread, content server monitor thread and also run the server
            executingRequests.start(); 
            checkingContentServer.start(); 
            runningServer.start(); 
            // close server after 18 seconds
            try{
                Thread.sleep(18000); 
            }
            catch(Exception e){

            }
            System.exit(0); 
        }
        // test case 18: server FeedLog replication backup
        // in this test case, we will see the server backup its FeedLog
        // 2 content servers will send 2 PUT
        // after processing 2 PUT, then we will compare all the FeedLog replicatio to the main FeedLog
        // this test case does not have output
        // the test script will compare the FeedLog and its replication
        // if they match, then the test case passed
        else if(args[0].equals("testing") && args[1].equals("18")){
            AggregationServer server = new AggregationServer("localhost", 4567); 
            // create content server monitor
            ContentServerMonitor contentServerMonitor = new ContentServerMonitor(server); 
            // create request handler
            ServerRequestHandler requestHandler = new ServerRequestHandler(server); 
            Thread runningServer = new Thread(server); 
            Thread executingRequests = new Thread(requestHandler, "Handling requests in Queue"); 
            Thread checkingContentServer = new Thread(contentServerMonitor); 
            // run request handler thread, content server monitor thread and also run the server
            executingRequests.start(); 
            checkingContentServer.start(); 
            runningServer.start(); 
            // close server after 8 seconds 
            try{
                Thread.sleep(8000); 
            }
            catch(Exception e){

            }
            System.exit(0); 
        }
        // close Scanner for of this program
        Scanner keyboard = new Scanner(System.in); 
        keyboard.close(); 
    }
}

