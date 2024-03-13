import java.io.*; 
import java.net.*; 
import java.util.*; 
import org.w3c.dom.*; 
import javax.xml.transform.*; 
import javax.xml.parsers.*; 
import java.time.LocalDateTime; 

// in this class, the Content Server will work as a client to the 
// Aggregation Server to send PUT requests to Aggregation Server
public class ContentServer implements Runnable{
    private static int contentServerID = 0; 
    private static final int MAX_RETRY = 3; // retry 3 times before giving up
    private static final int TIMEOUT_MILLIS = 4000; // timeout for request
    private static final int FIRST_CHAR = 0; 
    private static final int FOURTH_CHAR = 4; //the first 4 characters from given url to check the form of the given url 
    private static final int SEVENTH_CHAR = 7; // if the given url containing 'http://' then we will delete the first 7 chars  
    private ObjectInputStream in; 
    private ObjectOutputStream out; 
    private int id; 
    private LamportClock clock; 
    private String fileLocation; 
    private String connectingServerName; // name of connecting AggregationServer
    private int connectingServerPort; // port of connecting AggregationServer
    private Socket socket;  //socket connecting to AggregationServer

    // Content Server constructor
    // input: no
    // output: no 
    public ContentServer(){
        this.clock = new LamportClock(); 
        this.id = ++contentServerID; 
        this.connectingServerName = ""; 
        this.in = null; 
        this.out = null; 
        this.socket = null; 
        boolean complete = false; 
        Scanner keyboard = new Scanner(System.in); 
        while(complete == false){
            System.out.println("Enter URL to connect to the Aggregation Server: "); 
            String url = keyboard.nextLine(); 
            // get hostname and port of Aggregation Server 
            try{
                int colonPosition = -1; 
                // if url start with 'http://', remove 'http://' from url for string manipulation
                if(url.substring(FIRST_CHAR, FOURTH_CHAR ).equals("http") == true){
                    url = url.substring(SEVENTH_CHAR); 
                }
                colonPosition = url.indexOf(':'); 
                String portInString = url.substring(colonPosition+1); 
                this.connectingServerPort = Integer.parseInt(portInString); 
                url = url.substring(FIRST_CHAR, colonPosition); 
                // using 'serverNameFromUrl' variable to store the server name extracted from URL
                if(url.contains(".") == true){
                    int dotPosition = -1; 
                    dotPosition = url.indexOf('.'); 
                    this.connectingServerName = url.substring(dotPosition); 
                }
                this.connectingServerName= url; 
                // try to connect to check if the server is currently available
                // to simplify, we will not send anything to the server
                // check connection in this way will cause some minor errors on server side 
                // but they will be handled properly so I chose this way to simplify the constructor here
                Socket temp = new Socket(this.connectingServerName, this.connectingServerPort); 
                temp.close(); 
                System.out.println("Connected to Aggregation Server " + this.connectingServerName + " on port " + this.connectingServerPort); 
            }catch(Exception e){
                System.out.println("Server not found, please try again."); 
                continue; 
            }
            System.out.println("Enter Content Server's file location: "); 
            String location = keyboard.nextLine(); 
            File f = new File(location); 
            // enter again if the file doesn't exist
            if(f.exists()){
                this.fileLocation = location; 
                complete = true; 
            }
            else{
                System.out.println("File cannot be found, please try again."); 
            }
        }
    }

    // another Content Server constructor, instead of getting url and file location from user
    // we take them as parameters, easier for testing
    // input: String (hostname of the server), Integer (server's port), String (file location for feed items of this CS)
    // output: no 
    public ContentServer(String hostname, int port, String fileLocation){
        this.clock = new LamportClock(); 
        this.id = ++contentServerID; 
        this.connectingServerName = hostname; 
        this.connectingServerPort = port;
        this.fileLocation = fileLocation; 
        this.in = null; 
        this.out = null; 
        this.socket = null; 
    }
    
    // create a PUT request object to send to the server
    // input: none
    // output: HashMap<String, Object> which is the PUT request object
    public HashMap<String, Object> createPUTRequestObject(){
        // init the PUT request which is a HashMap 
        HashMap<String, Object> requestObject = new HashMap<String, Object>(); 
        XMLParser parser = new XMLParser(); 
        // parse normal text from file to XMLDocument to send to server
        ArrayList<HashMap<String, String>> feed = parser.getFeedItemsFromTextFile(this.fileLocation); 
        Document feedInXML = parser.parseFeedItemListToXMLDocument(feed); 
        // calculate the contentLength for PUT request
        long contentLength = 0; 
        File f = new File(this.fileLocation); 
        contentLength = f.length(); 
        // put all necessary information for the PUT Request
        requestObject.put("Request-Type", "PUT /atom.xml HTTP/1.1"); 
        requestObject.put("User-Agent", "ATOMClient/1/0"); 
        requestObject.put("Content-Type", "XML Document"); 
        requestObject.put("Content-Length", contentLength); 
        requestObject.put("Sender-Type", "CS"); 
        requestObject.put("ID", this.id); 
        requestObject.put("Clock", this.clock.getClockValue()); 
        requestObject.put("Message", feedInXML); 
        return requestObject; 
    }

    // send the PUT request to the server and print out the response (also update Lamport Clock)
    // if the server has not responded within the timeout, retry 3 times
    // input: none
    // output: String: the returned HTTP Code from the server desmonstrating if the PUT request has been successful or not
    //                  if any error or retry, the output will be the error code
    public String sendPUT(){
        String output = ""; 
        int retryCounter = 0; 
        boolean complete = false; 
        while(complete == false){
            try{
                // increment clock before sending this request
                this.clock.incrementClock(); 
                this.socket = new Socket(); 
                InetAddress address = InetAddress.getByName(this.connectingServerName); 
                SocketAddress serverAddress = new InetSocketAddress(address, this.connectingServerPort); 
                this.socket.connect(serverAddress, TIMEOUT_MILLIS); 
                // set timeout for request
                this.socket.setSoTimeout(TIMEOUT_MILLIS); 
                this.out = new ObjectOutputStream(this.socket.getOutputStream());
                HashMap<String, Object> requestObject = this.createPUTRequestObject(); //create object request
                out.writeObject(requestObject);  // send PUSH request as a HashMap object
                this.in = new ObjectInputStream(this.socket.getInputStream());  // get response from server
                // a little workaround for retry on error
                Object firstObj = in.readObject(); 
                Object secondObj = in.readObject(); 
                // read response
                HashMap<String, Object> response = (HashMap<String, Object>)in.readObject(); 
                int clock = (int)response.get("Clock"); 
                // update this Content Server clock when receive the response from Aggragation Server
                this.clock.updateClock(clock); 
                // read the httpStatus from response to check if PUT request is successful
                String httpStatus = (String)response.get("Status"); 
                System.out.println(httpStatus); 
                // if no error, then the output will be the returned HTTP code
                output += httpStatus; 
                this.out.flush(); 
                complete = true; 
            }
            // invalid hostname + port
            catch (UnknownHostException uhe){
                System.out.println("Server not found.");
                output += "Server not found." + "\n"; 
                complete = true; 
            }
            // if cannot get the response within the timeout, try 3 times before giving up
            catch (SocketTimeoutException ste){
                if(retryCounter < MAX_RETRY){
                    System.out.println("Server not responding."); 
                    output += "Server not responding." + "\n"; 
                    System.out.println("Retry."); 
                    output += "Retry." + "\n"; 
                    retryCounter += 1; 
                }
                else{
                    System.out.println("Server not responding after 3 attempts, pleasy try later."); 
                    output += "Server not responding after 3 attempts, please try later." + "\n"; 
                    complete = true; 
                }
            }
            catch(ConnectException ce){
                System.out.println("Server is temporarily unavailable, please try later."); 
                output += "Server is temporarily unavailable, please try later." + "\n"; 
                complete = true; 
            }
            catch(Exception e){
                e.printStackTrace(); 
                complete = true; 
                System.out.println("An error happened during PUT request."); 
                output += "An error happened during PUT request." + "\n"; 
            }
            finally{
                try{
                    this.socket.close();
                }
                catch(Exception e){

                }
            }
        }
        output = output.trim(); 
        return output; 
    }

    // content server id getter, return the id of this content server
    // input: no
    // output: Integer: id of this content server
    public int getId(){
        return this.id; 
    }

    //content server clock getter, return the Lamport clock of this content server
    //input: no
    //output: Lamport Clock object of this content server
    public LamportClock getClock(){
        return this.clock; 
    }

    // getter, return the location path of this content server file containing feed items from this content server
    // input: no
    // output: String: location path of the file
    public String getFileLocation(){
        return this.fileLocation; 
    }

    // setter (used for testing only) set the location path of this content server
    // input: String: location of new File
    // output: no
    public void setFileLocation(String newFileLocation){
        this.fileLocation = newFileLocation; 
    }

    // getter, return the name of the Aggregation Server which this content server connected to
    // input: no
    // output: String, the same of the Aggregation Server
    public String getConnectingServerName(){
        return this.connectingServerName; 
    }
    
    // getter, return the port of the Aggregation Server which this content server connected to 
    // input: no
    // output: Integer, the port of the Aggregation Server
    public int getConnectingServerPort(){
        return this.connectingServerPort; 
    }
    

    // override run() method to run this thread, when this thread is run, it will send a PUT request to the server
    // input: none
    // output: none
    public void run(){
        this.sendPUT();
    }

    public static void main(String[] args){
        // normal mode: create a Content Server, send PUT request based on the file location given, and send Heartbeat every 12s to maintain feed
        if(args[0].equals("normal")){
            ContentServer csAsClient = new ContentServer(); 
            // run the content server as a server as Dr.Szabo said :3 although it doesn't really matter in this assignment
            // so I make it as comment here to make it easier for the testing
            //RunContentServerAsAServer csAsServer = new RunContentServerAsAServer(csAsClient); 
            ContentServerSendingHeartBeat csSendingHeartBeat = new ContentServerSendingHeartBeat(csAsClient); 
            Thread runCsAsClient = new Thread(csAsClient, "Content Server as Client"); 
            //Thread runCsAsServer = new Thread(csAsServer, "Content Server as Server"); 
            Thread runCsSendingHeartBeat = new Thread(csSendingHeartBeat, "Content Server sending heart beat"); 
            // start 3 threads
            runCsAsClient.start(); 
            //runCsAsServer.start(); 
            // wait for 2 second after sending a PUT to send the heartbeat
            try{
                Thread.sleep(2000); 
            }
            catch(InterruptedException e){
                System.out.println("Start sending heartbeat"); 
            }
            runCsSendingHeartBeat.start(); 
        }
        // test case 2
        else if(args[0].equals("testing") && args[1].equals("2")){
            ContentServer contentServer = new ContentServer("localhost", 4567, "ContentServerFiles/ContentServer1.txt"); 
            contentServer.sendPUT(); 
        }
        // test case 3 
        else if(args[0].equals("testing") && args[1].equals("3")){
            ContentServer contentServerOne = new ContentServer("localhost", 4567, "ContentServerFiles/ContentServer1.txt"); 
            contentServerOne.sendPUT(); 
            try{
                Thread.sleep(1000); 
            }
            catch(Exception e){

            }
            ContentServer contentServerTwo = new ContentServer("localhost", 4567, "ContentServerFiles/ContentServer2.txt"); 
            contentServerTwo.sendPUT(); 
        }
        // test case 4
        else if(args[0].equals("testing") && args[1].equals("4")){
            ContentServer contentServer = new ContentServer("localhost", 4567, "ContentServerFiles/ContentServer1.txt"); 
            ContentServerSendingHeartBeat csSendingHeartBeat = new ContentServerSendingHeartBeat(contentServer); 
            Thread sendPUTRequest= new Thread(contentServer, "Content Server as Client"); 
            Thread maintainHeartbeat= new Thread(csSendingHeartBeat, "Content Server sending heart beat"); 
            // start thread to send PUT and heartbeat
            sendPUTRequest.start(); 
            // wait for 2 second after sending a PUT to send the heartbeat
            try{
                Thread.sleep(2000); 
            }
            catch(InterruptedException e){
                System.out.println("Start sending heartbeat"); 
            }
            maintainHeartbeat.start(); 
            // close all threads after 18 seconds 
            // so that the content server will be close after 20 seconds in total 
            try{
                Thread.sleep(18000); 
            }
            catch(Exception e){

            }
            System.exit(0); 
        }
        // test case 5
        else if(args[0].equals("testing") && args[1].equals("5")){
            ContentServer contentServerOne = new ContentServer("localhost", 4567, "ContentServerFiles/ContentServer1.txt"); 
            contentServerOne.sendPUT(); 
            // make sure the PUT from content server 1 will be process first
            try{
                Thread.sleep(1000); 
            }
            catch(Exception e){

            }
            // create content server 2 sending PUT and maintain heartbeat
            ContentServer contentServerTwo = new ContentServer("localhost", 4567, "ContentServerFiles/ContentServer2.txt"); 
            ContentServerSendingHeartBeat contentServerTwoSendingHeartbeat = new ContentServerSendingHeartBeat(contentServerTwo); 
            Thread csTwoSendPUT = new Thread(contentServerTwo); 
            Thread csTwoSendHeartbeat = new Thread(contentServerTwoSendingHeartbeat); 
            csTwoSendPUT.start(); 
            // wait for 2 seconds after sending PUT to send the first heartbeat
            try{
                Thread.sleep(2000); 
            }
            catch(Exception e){

            }
            csTwoSendHeartbeat.start(); 
            // stop 2 content servers after 17 seconds
            // so that all the threads will be closed after about 20 seconds total
            try{
                Thread.sleep(17000); 
            }
            catch(Exception e){

            }
            System.exit(0); 
        }
        // test case 6
        else if(args[0].equals("testing") && args[1].equals("6")){
            ContentServer contentServer = new ContentServer("localhost", 4567, "ContentServerFiles/ContentServer1.txt"); 
            contentServer.sendPUT(); 
            // first PUT request (containing old version of feed item)
            // should be processed first
            try{
                Thread.sleep(2000); 
            }
            catch(Exception e){

            }
            // set the file location so that the second PUT 
            // will contain 1 feed item newer version
            // although the file name is ContentServer2.txt
            // but it's sent from Content Server id = 1
            // that's why the server decides to replace 1 duplicate feed item by its newest version
            contentServer.setFileLocation("ContentServerFiles/ContentServer2.txt"); 
            contentServer.sendPUT(); 
        }
        // test case 7 
        else if(args[0].equals("testing") && args[1].equals("7")){
            ContentServer contentServerOne= new ContentServer("localhost", 4567, "ContentServerFiles/ContentServer1.txt"); 
            ContentServerSendingHeartBeat contentServerOneSendingHeartbeat = new ContentServerSendingHeartBeat(contentServerOne); 
            Thread csOneSendPUT = new Thread(contentServerOne); 
            Thread csOneSendHeartbeat = new Thread(contentServerOneSendingHeartbeat); 
            csOneSendPUT.start(); 
            // wait for 2 seconds after sending PUT to send the first heartbeat
            try{
                Thread.sleep(2000); 
            }
            catch(Exception e){

            }
            csOneSendHeartbeat.start(); 
            ContentServer contentServerTwo = new ContentServer("localhost", 4567, "ContentServerFiles/ContentServer2.txt"); 
            ContentServerSendingHeartBeat contentServerTwoSendingHeartbeat = new ContentServerSendingHeartBeat(contentServerTwo); 
            Thread csTwoSendPUT = new Thread(contentServerTwo); 
            Thread csTwoSendHeartbeat = new Thread(contentServerTwoSendingHeartbeat); 
            csTwoSendPUT.start(); 
            // wait for 2 seconds after sending PUT to send the first heartbeat
            try{
                Thread.sleep(2000); 
            }
            catch(Exception e){

            }
            csTwoSendHeartbeat.start(); 
            ContentServer contentServerThree = new ContentServer("localhost", 4567, "ContentServerFiles/ContentServer3.txt"); 
            ContentServerSendingHeartBeat contentServerThreeSendingHeartbeat = new ContentServerSendingHeartBeat(contentServerThree); 
            Thread csThreeSendPUT = new Thread(contentServerThree); 
            Thread csThreeSendHeartbeat = new Thread(contentServerThreeSendingHeartbeat); 
            csThreeSendPUT.start(); 
            // wait for 2 seconds after sending PUT to send the first heartbeat
            try{
                Thread.sleep(2000); 
            }
            catch(Exception e){

            }
            csThreeSendHeartbeat.start(); 
            // close all content server after 14 seconds
            // so all the content server will be closed after 20s in total
            try{
                Thread.sleep(14000); 
            }
            catch(Exception e){

            }
            System.exit(0); 
        }
        // test case 8
        else if(args[0].equals("testing") && args[1].equals("8")){
            ContentServer contentServer = new ContentServer("localhost", 4567, "ContentServerFiles/ContentServer1.txt"); 
            // content server start at clock 1 for this test case
            contentServer.getClock().incrementClock(); 
            contentServer.sendPUT(); 
        }
        // test case 9
        else if(args[0].equals("testing") && args[1].equals("9")){
            ContentServer contentServer = new ContentServer("localhost", 4567, "ContentServerFiles/ContentServer1.txt"); 
            contentServer.sendPUT(); 
        }
        // test case 13: content server gives up when server not available
        // when the server is not available (ConnectException thrown), then the content serer will give up
        // the expected output will be "Server is temporarily unavailable, please try later."
        else if(args[0].equals("testing") && args[1].equals("13")){
            ContentServer contentServer = new ContentServer("localhost", 4567, "ContentServerFiles/ContentServer1.txt"); 
            String testCaseOutput = contentServer.sendPUT(); 
            try{
                FileWriter fileWriter = new FileWriter("Testing/TestCase13Output.txt"); 
                PrintWriter printWriter = new PrintWriter(fileWriter); 
                printWriter.print(testCaseOutput); 
                printWriter.close(); 
            }
            catch(Exception e){

            }
        }
        // test case 14: content server retry 3 times when server doesn't respond within timeout
        // the server will be turned on but not the thread which 
        // is responsible for responding to requests
        // and we will see the content server retry 3 times
        else if(args[0].equals("testing") && args[1].equals("14")){
            ContentServer contentServer = new ContentServer("localhost", 4567, "ContentServerFiles/ContentServer1.txt"); 
            String testCaseOutput = contentServer.sendPUT(); 
            try{
                FileWriter fileWriter = new FileWriter("Testing/TestCase14Output.txt"); 
                PrintWriter printWriter = new PrintWriter(fileWriter); 
                printWriter.print(testCaseOutput); 
                printWriter.close(); 
            }
            catch(Exception e){

            }
        }
        // test case 15: content server retry 2 times then the server responds in the third retry 
        // the expected output is that the content server retry 2 times then get HTTP/1.1 201 - HTTP_CREATED in the third retry
        else if(args[0].equals("testing") && args[1].equals("15")){
            ContentServer contentServer = new ContentServer("localhost", 4567, "ContentServerFiles/ContentServer1.txt"); 
            String testCaseOutput = contentServer.sendPUT(); 
            try{
                FileWriter fileWriter = new FileWriter("Testing/TestCase15Output.txt"); 
                PrintWriter printWriter = new PrintWriter(fileWriter); 
                printWriter.print(testCaseOutput); 
                printWriter.close(); 
            }
            catch(Exception e){

            }
        }
        // test case 16: 2 content servers sending 2 PUTs
        else if(args[0].equals("testing") && args[1].equals("16")){
            ContentServer contentServerOne = new ContentServer("localhost", 4567, "ContentServerFiles/ContentServer1.txt");
            contentServerOne.sendPUT(); 
            // wait for one second to make sure that the first PUT has been completed
            try{
                Thread.sleep(1000); 
            }
            catch(Exception e){

            }
            ContentServer contentServerTwo = new ContentServer("localhost", 4567, "ContentServerFiles/ContentServer2.txt"); 
            contentServerTwo.sendPUT(); 
        }
        // test case 17: 2 content servers sending 2 PUTs
        else if(args[0].equals("testing") && args[1].equals("17")){
            ContentServer contentServerOne = new ContentServer("localhost", 4567, "ContentServerFiles/ContentServer1.txt");
            contentServerOne.sendPUT(); 
            // wait for one second to make sure that the first PUT has been completed
            try{
                Thread.sleep(1000); 
            }
            catch(Exception e){

            }
            ContentServer contentServerTwo = new ContentServer("localhost", 4567, "ContentServerFiles/ContentServer2.txt"); 
            contentServerTwo.sendPUT(); 
        }
        // test case 18: 2 content servers sending 2 PUTs
        else if(args[0].equals("testing") && args[1].equals("18")){
            ContentServer contentServerOne = new ContentServer("localhost", 4567, "ContentServerFiles/ContentServer1.txt");
            contentServerOne.sendPUT(); 
            // wait for one second to make sure that the first PUT has been completed
            try{
                Thread.sleep(1000); 
            }
            catch(Exception e){

            }
            ContentServer contentServerTwo = new ContentServer("localhost", 4567, "ContentServerFiles/ContentServer2.txt"); 
            contentServerTwo.sendPUT(); 
        }
        //close Scanner here
        Scanner keyboard = new Scanner(System.in); 
        keyboard.close(); 
    }
}
