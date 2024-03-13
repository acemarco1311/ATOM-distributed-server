import java.io.*; 
import java.net.*; 
import java.util.*; 
import org.w3c.dom.*; 
import javax.xml.transform.*; 
import javax.xml.parsers.*; 
import javax.xml.transform.dom.*; 
import javax.xml.transform.stream.*; 

public class GETClient implements Runnable{
    private static final int FIRST_CHAR = 0; 
    private static final int FOURTH_CHAR = 4; //the first 4 characters from given url to check the form of the given url
    private static final int SEVENTH_CHAR = 7; // if the given url containing 'http://' then we will delete the first 7 chars 
    private static final int MAX_RETRY = 3; 
    private static final int TIMEOUT_MILLIS = 4000; //set timeout 4 seconds
    private static int clientID = 0; 
    private LamportClock clock; 
    private int id; 
    private String connectingServerName; 
    private int connectingServerPort; 
    private Socket clientSocket; 
    private ObjectOutputStream out; 
    private ObjectInputStream in; 

    // constructor, create a GETClient.
    // input: no
    // output: no
    public GETClient(){
        this.id = ++clientID; 
        this.clock = new LamportClock(); 
        this.in = null; 
        this.out = null; 
        Scanner keyboard = new Scanner(System.in); 
        String serverName = ""; 
        int serverPort = -1; 
        boolean complete = false; 
        // get name and port of Aggregation Server from URL
        while(complete == false){
            System.out.println("Enter URL for Aggregation Server: "); 
            String url = keyboard.nextLine(); 
            try{
                int colonPosition = -1; 
                // if url start with 'http://', remove 'http://' from url for string manipulation
                if(url.substring(FIRST_CHAR, FOURTH_CHAR).equals("http") == true){
                    url = url.substring(SEVENTH_CHAR); 
                }
                colonPosition = url.indexOf(':'); 
                String portInString = url.substring(colonPosition+1); 
                int portNumber = Integer.parseInt(portInString); 
                this.connectingServerPort = portNumber; 
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
                complete = true; 
                // invalid name + port from the client, client can enter again
            } catch(Exception e){
                System.out.println("Error in connecting to server, please try again."); 
            }
        }
    }
    
    // client constructor with parameter, used for easier testing
    // input: String (hostname of server), Integer (port of the server) 
    // output: no
    public GETClient(String hostname, int port){
        this.id = ++clientID; 
        this.clock = new LamportClock(); 
        this.connectingServerName = hostname; 
        this.connectingServerPort = port; 
        this.in = null; 
        this.out = null; 
        this.clientSocket = null; 
    }
    

    // client id getter, return the client id
    // input: no 
    // output: Integer (this client id) 
    public int getId(){ 
        return this.id; 
    }

    // this client clock getter, return the logical clock of this client
    // input: no
    // output: LamportClock object
    public LamportClock getClock(){ 
        return this.clock; 
    }

    // get the client socket
    // input: no
    // output: Socket object
    public Socket getClientSocket(){
        return this.clientSocket; 
    }


    // close the connection to the Aggregation Server
    // input: no
    // output: no
    public void closeConnection(){
        try{
            this.clientSocket.close(); 
            this.out.close(); 
            this.in.close(); 
        }
        catch (Exception e){
            System.out.println("Unable to close connection."); 
        }
    }

    // create GET request object to send
    // input: none
    // output: HashMap<String, Object>, the request object for GET request
    public HashMap<String, Object> createGETRequest(){
        HashMap<String, Object> requestObject = new HashMap<String,Object>(); 
        requestObject.put("Request-Type", "GET /atom.xml HTTP/1.1"); 
        requestObject.put("Sender-Type", "Client"); 
        requestObject.put("ID", this.id); 
        requestObject.put("Clock", this.clock.getClockValue()); 
        requestObject.put("Message", "Gimme an aggregated feed"); 
        return requestObject; 
    }

    // this function will send a GET request to the server, then print out the response of the server
    // if the client couldn't get response from the server, client will retry 3 times before giving up
    // input: none
    // output: String: the aggregated feed in text parsed from XML Document received from the server
    //                 if there is any error or retry, the output will contain the error/retry message
    //                 if the aggregated feed returned from the server is empty, then the output = EMPTY
    //                 if the server respond with http code demonstrating there is something wrong with the request, 
    //                              then the output will be the returned HTTP code
    public String sendGET(){
        // init the output (normal text of aggregated feed parsed from XML Document received from server) 
        // the output here will be used for testing.
        // In the normal mode, contentServerID and timestamp will also be printed out  
        // but for easier testing, the output here will not contain timestamp because it's impossible to 
        // know the exact timestamp to put in expected output for the test case
        String output = ""; 
        int retryCounter = 0; 
        boolean complete = false; 
        while(complete == false){
            try{
                //increment the lamport clock before sending requests
                this.clock.incrementClock(); 
                this.clientSocket = new Socket(); 
                InetAddress address = InetAddress.getByName(this.connectingServerName); 
                SocketAddress serverAddress = new InetSocketAddress(address, this.connectingServerPort); 
                this.clientSocket.connect(serverAddress, TIMEOUT_MILLIS); 
                this.clientSocket.setSoTimeout(TIMEOUT_MILLIS); 
                this.out = new ObjectOutputStream(this.clientSocket.getOutputStream()); 
                HashMap<String, Object> requestObject = this.createGETRequest(); 
                // send GET request
                out.writeObject(requestObject); 
                this.in = new ObjectInputStream(this.clientSocket.getInputStream()); 
                // server send 2 random objects to check if the socket has been closed or not
                // so we should ignore 2 first object here
                Object firstObj = in.readObject(); 
                Object secondObj = in.readObject(); 
                // received 'real' response from server which is a HashMap
                HashMap<String, Object> responseObject = (HashMap<String, Object>)in.readObject(); 
                String httpStatus = (String)responseObject.get("Status"); 
                int clock = (int)responseObject.get("Clock"); 
                Document aggregatedFeed = (Document)responseObject.get("Aggregated-Feed"); 
                //update clock when receives response
                this.clock.updateClock(clock); 
                //print out the aggregated feed received
                if(httpStatus.equals("HTTP/1.1 200 - OK") == true){
                    System.out.println(httpStatus); 
                    XMLParser parser = new XMLParser(); 
                    //parse the XMLDocument from the server to the normal text for client
                    String aggregatedFeedInString = parser.parseXMLDocumentToNormalText(aggregatedFeed); 
                    // sometime even though the request is valid, if the aggregation server has not created the feed
                    // then the client will get response = 200 OK but get the null xml then 
                    if(aggregatedFeedInString != null && aggregatedFeedInString.isEmpty() == false){
                        printAggregatedFeed(aggregatedFeedInString); 
                        output += removeTimeStampFromAggregatedFeed(aggregatedFeedInString); 
                    }
                    else{
                        System.out.println("Aggregated Feed is currently empty."); 
                        output += "EMPTY"; 
                    }
                }
                // if the server response indicate that something is wrong, then we print out httpstatus sent by the server
                else{
                    System.out.println(httpStatus); 
                    output += httpStatus; 
                }
                this.out.flush(); 
                complete = true; 
            } 
            // give up when server not found by invalid hostname and port
            catch (UnknownHostException uhe){
                System.out.println("Server not found."); 
                output += "Server not found." + "\n";  
                complete = true; 
            }
            // retry 3 times when the server doesn't respond within the timeout
            catch (SocketTimeoutException ste){
                if(retryCounter < MAX_RETRY){
                    System.out.println("Server not responding.");
                    System.out.println("Retry."); 
                    output += "Server not responding." + "\n" + "Retry." + "\n"; 
                    retryCounter += 1; 
                }
                else{
                    System.out.println("Server not responding after 3 attempts, please try later."); 
                    output += "Server not responding after 3 attempts, please try later." + "\n"; 
                    complete = true; 
                }
            }
            // give up when the server is not available
            catch (ConnectException ce){
                System.out.println("Server is temporarily unavailable, please try later."); 
                output += "Server is temporarily unavailable, please try later." + "\n"; 
                complete = true; 
            }
            // give up when any other error happens 
            catch (Exception e){
                complete = true; 
                System.out.println("An error happened during GET request."); 
                output += "An error happened during GET request." + "\n"; 
            }
            finally{
                try{
                    this.clientSocket.close(); 
                }
                catch(Exception e){
                }
            }
        }
        return output.trim(); 
    }

    // print the feed from GET request, replace all the 'entry' keywords by --------------
    // to make it easier to see the aggregated feed in client side
    // assumption is that the argument will not be null or empty.
    // input: String: the aggregated feed in normal text
    // output: no
    public void printAggregatedFeed(String feed){
        // extract each line from the aggregated feed and put in an array of String
        String[] lines = feed.split("\\r?\\n"); 
        System.out.println("------------------AGGREGATED FEED------------------"); 
        for(int i = 0; i < lines.length; i++){
            // replace 'entry' keywords to separate feed items then easier for clients to see
            if(lines[i].equals("entry")){
                System.out.println("------------------------------"); 
            }
            else{
                System.out.println(lines[i]); 
            }
        }
    }

    // get the output from the GET request
    // the output here will be used for testing 
    // because in testing, we don't know the exact timestamp to design the expected output
    // therefore we will remove all the timestamp from the aggregated feed in text
    // input: String: aggregated feed, which is returned from the GET request, has been parsed to normal text
    // output: String: aggregated feed in text without timestamp fields in every feed item 
    public String removeTimeStampFromAggregatedFeed(String feed){
        String output = ""; 
        // extract each line from the returned feed
        String[] lines = feed.split("\\r?\\n"); 
        for(int i = 0; i < lines.length; i++){
            if(lines[i].equals("entry")){
                output += lines[i] + "\n"; 
            }
            else{
                // find the name of the field in each line
                int colonIndex = lines[i].indexOf(':'); 
                String field = lines[i].substring(FIRST_CHAR, colonIndex); 
                // remove the timestamp 
                if(!field.equals("timestamp")){
                    output += lines[i] + "\n"; 
                }
            }
        }
        output = output.trim(); 
        return output; 
    }

    // start this thread to send 1 GET request to the server
    // input: no
    // output: no
    @Override
    public void run(){
        this.sendGET(); 
    }
    


    public static void main(String args[]){
        // normal mode, the client will send 1 GET then die
        if(args[0].equals("normal")){
            GETClient client = new GETClient(); 
            Thread runClient = new Thread(client); 
            runClient.start(); 
        }
        // test case 1: test GET request and Lamport Clock
        // send GET request when server has no aggregated feed so it's expected to see an empty feed 
        // and also check the lamport clock
        // client & server start at clock = 0, client increment clock to 1 and send request
        // server update clock to 2 and receive a request 
        // client update clock to 3 when receive a response 
        // so we expect an empty feed and clock after request = 3 in this test case
        else if(args[0].equals("testing") && args[1].equals("1")){
            GETClient client = new GETClient("localhost", 4567); 
            String output = client.sendGET(); 
            String testCaseOutput = ""; 
            try{
                FileWriter fileWriter = new FileWriter("Testing/TestCase1Output.txt"); 
                PrintWriter printWriter = new PrintWriter(fileWriter); 
                testCaseOutput += output; 
                testCaseOutput += "\n"; 
                testCaseOutput += Integer.toString(client.getClock().getClockValue()); 
                printWriter.print(testCaseOutput); 
                printWriter.close(); 
            }
            catch(Exception e){

            }
        }
        // test case 2: check PUT request from 1 Content Server, Server reject invalid feed item (including XML Parsing, the Lamport Clock)
        // this is a test for the basic workflow
        // Run the server, then the content server will send a PUT request from ContentServer1.txt (without heartbeat following up)
        // then the client send a GET request 
        // ContentServer1.txt has 4 feed items, 1 of them is invalid,
        // so we expect to get the feed with 3 valid feed items from ContentServer1.txt
        // Content Server start at clock 0, then increment to 1 to send PUT
        // the server start at clock 0, update clock to 2 when receive PUT
        // the server send response with clock = 2, the content server update its clock to 3 
        // then the client start at 0, increment to 1 to send GET
        // the server update its clock to 3 and send response to GET
        // the client update its clock to 4
        // so we expect the client end up at clock 4 and a feed from ContentServer1.txt 
        // NOTE: the feed items will be ordered based on the time they came to the server 
        // therefore, the order of feed items will be inconsistent from case to case 
        // because they're sent at the same time from the same content server 
        // just keep that in mind just in case you get confused
        // but for each test case, the order of feed items which are sent at the same time will remain the same
        // every time you run a test case
        // this helps me to write the expected output
        else if(args[0].equals("testing") && args[1].equals("2")){
            // send GET after the PUT request completed
            GETClient client = new GETClient("localhost", 4567); 
            String output = client.sendGET(); 
            String testCaseOutput = ""; 
            try{
                FileWriter fileWriter = new FileWriter("Testing/TestCase2Output.txt"); 
                PrintWriter printWriter = new PrintWriter(fileWriter); 
                testCaseOutput += output; 
                testCaseOutput += "\n"; 
                testCaseOutput += Integer.toString(client.getClock().getClockValue()); 
                printWriter.print(testCaseOutput); 
                printWriter.close(); 
            }
            catch(Exception e){

            }
        }
        //test case 3: check PUT request from 2 Content Server 
        //(including Aggregated Feed Item ordering, XML Parsing, Lamport Clock) 
        // another test case for basic workflow
        // Run the server, Content Server 1 will send PUT from ContentServer1.txt (without heartbeat follow up)
        // After that, Content Server 2 will send PUT from ContentServer2.txt (without heartbeat follow up)
        // Then the client send GET request 
        // All elements in this test case will start at Lamport Clock = 0
        // So we expect an aggregated feed containing items from both content server 1 and content server 2
        // with items from content server 2 on top
        // The expected clock of GETClient after sending GET request is 5
        else if(args[0].equals("testing") && args[1].equals("3")){
            // send GET immediately after 2 PUT requests has been completed
            GETClient client = new GETClient("localhost", 4567); 
            String output = client.sendGET(); 
            String testCaseOutput = ""; 
            try{
                FileWriter fileWriter = new FileWriter("Testing/TestCase3Output.txt"); 
                PrintWriter printWriter = new PrintWriter(fileWriter); 
                testCaseOutput += output; 
                testCaseOutput += "\n"; 
                testCaseOutput += Integer.toString(client.getClock().getClockValue()); 
                printWriter.print(testCaseOutput); 
                printWriter.close(); 
            }
            catch(Exception e){

            }
        }

        // test case 4: content server maintaining heartbeat
        // A content server send a PUT request (from ContentServer1.txt) and maintain the heartbeat
        // after ~15 seconds client send GET
        // client should be able to see the feed from the content server
        // because the content server maintain the heartbeat with AS
        else if(args[0].equals("testing") && args[1].equals("4")){
            // send get request after 15 seconds
            try{
                Thread.sleep(15000); 
            }
            catch(Exception e){

            }
            GETClient client = new GETClient("localhost", 4567); 
            String output = client.sendGET(); 
            String testCaseOutput = ""; 
            try{
                FileWriter fileWriter = new FileWriter("Testing/TestCase4Output.txt"); 
                PrintWriter printWriter = new PrintWriter(fileWriter); 
                testCaseOutput += output; 
                printWriter.print(testCaseOutput); 
                printWriter.close(); 
            }
            catch(Exception e){

            }
        }
        // test case 5: server delete items from unactive Content Server, content servers maintaining heartbeat
        // Content Server 1 sends PUT request (from ContentServer1.txt)
        // Content Server 2 sends PUT request (from ContentServer2.txt)
        // Content Server 2 will continue to send heart beat, Content Server 1 doesn't
        // after 15 seconds, client send a GET
        // we expect to see output only contains items from Content Server 2
        else if(args[0].equals("testing") && args[1].equals("5")){
            // send get request after 15 seconds (instead of exact 12s) just to make sure that the server
            // has completed its work (aka delete feed items from unactive content server)
            try{
                Thread.sleep(15000); 
            }
            catch(Exception e){

            }
            GETClient client = new GETClient("localhost", 4567); 
            String output = client.sendGET(); 
            String testCaseOutput = ""; 
            try{
                FileWriter fileWriter = new FileWriter("Testing/TestCase5Output.txt"); 
                PrintWriter printWriter = new PrintWriter(fileWriter); 
                testCaseOutput += output; 
                printWriter.print(testCaseOutput); 
                printWriter.close(); 
            }
            catch(Exception e){

            }
        }
        // test case 6: server update the current feed item to its newest version
        // the content server 1 send a PUT to server containing one feed has the id X (for example)
        // then the content server continue to send another PUT containing the feed item which has the id X too
        // then the server say: "Ok this feed item from the same content server so i will replace the old version by the new version"
        // in this test case, the content server 1 send the first PUT request containing the feed item id = urn:uuid:1225c695-cfb8-4ebb-aaaa-80da344efa6a
        // (from ContentServer1.txt)
        // then content server 1 send another PUT (from ContentServer2.txt) also has the item with id: urn:uuid:1225c69-cfb8-4ebb-aaaa-80da344efa6a
        // the new version of the feed id from the second PUT will be applied, the old version will be deleted
        else if(args[0].equals("testing") && args[1].equals("6")){
            // send get request after 15 seconds
            GETClient client = new GETClient("localhost", 4567); 
            String output = client.sendGET(); 
            String testCaseOutput = ""; 
            try{
                FileWriter fileWriter = new FileWriter("Testing/TestCase6Output.txt"); 
                PrintWriter printWriter = new PrintWriter(fileWriter); 
                testCaseOutput += output; 
                printWriter.print(testCaseOutput); 
                printWriter.close(); 
            }
            catch(Exception e){

            }
        }
        // test case 7: server stores 20 most recent feed items, 3 content servers maintaining heartbeat
        // in this test case, content server 1 sends 3 feed items (actually it has 4 but one of them is invalid so the server will reject it)
        // content server 2 sends 1 feed item
        // content server 3 send 17 feed items
        // total we have 21 feed items
        // after 15 seconds, client sends GET request
        // we expect to see one of the feed items from content server 1 will be removed
        // because the Server only stores the most recent 20 feed items
        // NOTE: 3 feed items from the content server 1 was sent at the same time
        // so we're not sure which one will be removed :3 but we know that one of them will be removed
        // despite this fact, the item which will be removed will be the same for this test case 
        // do a little bit of manual testing, then we will know which one will be removed
        // then we can design the expected output for this test case
        else if(args[0].equals("testing") && args[1].equals("7")){
            // send get request after 15 seconds (instead of exact 12s) just to make sure that the server
            // has completed its work (aka delete feed items from unactive content server)
            try{
                Thread.sleep(15000); 
            }
            catch(Exception e){

            }
            GETClient client = new GETClient("localhost", 4567); 
            String output = client.sendGET(); 
            String testCaseOutput = ""; 
            try{
                FileWriter fileWriter = new FileWriter("Testing/TestCase7Output.txt"); 
                PrintWriter printWriter = new PrintWriter(fileWriter); 
                testCaseOutput += output; 
                printWriter.print(testCaseOutput); 
                printWriter.close(); 
            }
            catch(Exception e){

            }
        }
        // test case 8: request ordering with Lamport clock
        // in this test case, we will see how requests which are sent at (nearly) the same time being ordered by its Lamport clock
        // content server will start at Lamport clock = 1, and client starts at Lamport clock = 0
        // then content server will send PUT, meanwhile client send GET at nearly the same time 
        // because content server has higher Lamport clock value 
        // so we expect PUT will be processed first, therefore, the output of GET request will not be empty
        // content server starts at clock = 1, increment to 2 then send PUT
        // server update its clock to 3, then send response
        // client start at clock = 0, increment to 1 then send GET
        // server update its clock to 4, then send response
        // client update its clock to 5
        // so the expected clock value of client is 5 
        else if(args[0].equals("testing") && args[1].equals("8")){
            GETClient client = new GETClient("localhost", 4567); 
            String output = client.sendGET(); 
            String testCaseOutput = ""; 
            try{
                FileWriter fileWriter = new FileWriter("Testing/TestCase8Output.txt"); 
                PrintWriter printWriter = new PrintWriter(fileWriter); 
                testCaseOutput += output; 
                testCaseOutput += "\n"; 
                testCaseOutput += Integer.toString(client.getClock().getClockValue()); 
                printWriter.print(testCaseOutput); 
                printWriter.close(); 
            }
            catch(Exception e){

            }
        }
        // test case 9: request ordering with Lamport clock
        // we do the same as the test case 8 but reversely, to make sure that request ordering is really working
        // client start at clock = 1, while content server will start at clock = 0
        // then client and content server send GET and PUT at (nearly) the same time
        // this time, the expected Lamport clock is 4 when the GET request completes, and the GET request return empty feed 
        // (client starts at clock 1, increment to 2 to send GET, server update clock to 3, client update its clock to 4 when receives response)
        else if(args[0].equals("testing") && args[1].equals("9")){
            GETClient client = new GETClient("localhost", 4567); 
            // client starts at clock = 1 before sending GET in this test case
            client.getClock().incrementClock(); 
            String output = client.sendGET(); 
            String testCaseOutput = ""; 
            try{
                FileWriter fileWriter = new FileWriter("Testing/TestCase9Output.txt"); 
                PrintWriter printWriter = new PrintWriter(fileWriter); 
                testCaseOutput += output; 
                testCaseOutput += "\n"; 
                testCaseOutput += Integer.toString(client.getClock().getClockValue()); 
                printWriter.print(testCaseOutput); 
                printWriter.close(); 
            }
            catch(Exception e){

            }
        }
        // test case 10: client gives up when server not available.
        // my system behaviour is that if the server is not available at the moment (ConnectException being thrown) 
        // then the client will give up 
        // because there is no point retrying when we already knew that server is unavailable now
        // so the expected output from the GET request should be "Server is temporarily unavailable, please try later."
        else if(args[0].equals("testing") && args[1].equals("10")){
            GETClient client = new GETClient("localhost", 4567); 
            String output = client.sendGET(); 
            String testCaseOutput = ""; 
            try{
                FileWriter fileWriter = new FileWriter("Testing/TestCase10Output.txt"); 
                PrintWriter printWriter = new PrintWriter(fileWriter); 
                testCaseOutput += output; 
                printWriter.print(testCaseOutput); 
                printWriter.close(); 
            }
            catch(Exception e){

            }
        }
        // test case 11: client retry 3 times when server not responding
        // in this test case, we'll see the client retry on error (not responding) 
        // in my system, clients only retry when the server is available, GET request can be sent
        // but unable to get response within the timeout (4 seconds)
        // the client will retry 3 times before giving up
        // we will turn the server on but not the thread which is responsible of responding request
        // then we see the client retry 3 times
        else if(args[0].equals("testing") && args[1].equals("11")){
            GETClient client = new GETClient("localhost", 4567); 
            String output = client.sendGET(); 
            String testCaseOutput = ""; 
            try{
                FileWriter fileWriter = new FileWriter("Testing/TestCase11Output.txt"); 
                PrintWriter printWriter = new PrintWriter(fileWriter); 
                testCaseOutput += output; 
                printWriter.print(testCaseOutput); 
                printWriter.close(); 
            }
            catch(Exception e){

            }
        }
        // test case 12: client retry 2 times when server not respond then server respond in the third retry
        // the expected output will be client retry 2 times then get an empty aggregated feed in the third retry
        else if(args[0].equals("testing") && args[1].equals("12")){
            GETClient client = new GETClient("localhost", 4567); 
            String output = client.sendGET(); 
            String testCaseOutput = ""; 
            try{
                FileWriter fileWriter = new FileWriter("Testing/TestCase12Output.txt"); 
                PrintWriter printWriter = new PrintWriter(fileWriter); 
                testCaseOutput += output; 
                printWriter.print(testCaseOutput); 
                printWriter.close(); 
            }
            catch(Exception e){

            }
        }
        // test case 16: server backup states (FeedLog and Lamport clock), server restores its state after being restarted
        // in this test case, we'll see the server backup its state and be able to restore it
        // we'll run the server, the Content Server 1 will make a PUT (without heartbeat follow up for easier Lamport clock test) 
        // then the Content Server 2 will make another PUT (without heartbeat follow up) 
        // then we shut down the server
        // we turn the server on and the client will make a GET
        // we expect to see that the client will receive the aggregated feed containing items from CS1 and CS2
        // the Lamport clock of the client at the end is expected to be 5
        // (CS1 start at 0, increment to 1 to send PUT, server update to 2)
        // (CS2 start at 0, increment to 1 to send PUT, server update to 3) 
        // (Client starts at 0, increment to 1 to send GET, server update to 4, client increments to 5 when receives response)
        else if(args[0].equals("testing") && args[1].equals("16")){
            GETClient client = new GETClient("localhost", 4567); 
            String output = client.sendGET(); 
            String testCaseOutput = ""; 
            try{
                FileWriter fileWriter = new FileWriter("Testing/TestCase16Output.txt"); 
                PrintWriter printWriter = new PrintWriter(fileWriter); 
                testCaseOutput += output; 
                testCaseOutput += "\n"; 
                testCaseOutput += Integer.toString(client.getClock().getClockValue()); 
                printWriter.print(testCaseOutput); 
                printWriter.close(); 
            }
            catch(Exception e){

            }
        }
        // test case 17: we will do the same as we did in the test case 16
        // however this time, the client will wait for 15 seconds to make sure that
        // after the server restart, it delete all the feed items because the content servers didn't maintain heartbeat
        // then the client will get an empty feed item 
        // the Lamport clock of the client at the end is expected to be 5
        else if(args[0].equals("testing") && args[1].equals("17")){
            try{
                Thread.sleep(15000); 
            }
            catch(Exception e){

            }
            GETClient client = new GETClient("localhost", 4567); 
            String output = client.sendGET(); 
            String testCaseOutput = ""; 
            try{
                FileWriter fileWriter = new FileWriter("Testing/TestCase17Output.txt"); 
                PrintWriter printWriter = new PrintWriter(fileWriter); 
                testCaseOutput += output; 
                testCaseOutput += "\n"; 
                testCaseOutput += Integer.toString(client.getClock().getClockValue()); 
                printWriter.print(testCaseOutput); 
                printWriter.close(); 
            }
            catch(Exception e){

            }
        }
        //close the Scanner of this program
        Scanner keyboard = new Scanner(System.in); 
        keyboard.close(); 
    }
}
