import java.io.*; 
import java.util.*; 
import java.net.*; 
import org.w3c.dom.*; 
import javax.xml.transform.*; 
import javax.xml.parsers.*; 

// this thread will be created to respond to a GET request
public class ServerGETRequestHandler implements Runnable{
    private AggregationServer server; 
    private ObjectInputStream in; 
    private ObjectOutputStream out; 
    private Socket clientSocket; 
    private HashMap<String, Object> request; 

    // thread constructor
    // input: AggregationServer, Socket (of the request sender), ObjectInputStream (on the socket), ObjectOutputStream(on the socket), 
    //          HashMap<String, Object> (the GET request containing information)
    // output: none
    public ServerGETRequestHandler(AggregationServer server, Socket clientSocket, ObjectInputStream in, ObjectOutputStream out, HashMap<String, Object> request){
        this.server = server; 
        this.clientSocket = clientSocket; 
        this.out = out; 
        this.in = in; 
        this.request = request; 
    }

    // this function will check if this GET request is valid, required 
    // Request-Type, Sender-Type, ID, Clock with valid values
    // Message is not really matter 
    // input: no
    // output: true if this GET request is valid, false otherwise
    public boolean validGETRequest(){
        boolean output = true; 
        if(this.request.get("Request-Type") == null || this.request.get("Sender-Type") == null ||
                this.request.get("ID") == null || this.request.get("Clock") == null){
            return false; 
                }
        String requestType = (String)this.request.get("Request-Type"); 
        String senderType = (String)this.request.get("Sender-Type"); 
        int id = (int)this.request.get("ID"); 
        int clock = (int)this.request.get("Clock"); 
        if(requestType.equals("GET /atom.xml HTTP/1.1") == false){
            output = false; 
        }
        else if(senderType.equals("Client") == false){
            output = false; 
        }
        else if(id < 0){ 
            output = false; 
        }
        else if(clock < 0){
            output = false; 
        }
        return output; 
    }

    // this function init the response for this current GET request
    // if this GET request is valid then init the response = 200
    // which will be replaced by the aggregated feed later
    // if this GET request is not valid, then init the response = 400 
    // then the server will not process this GET request
    // input: no
    // output: String ("HTTP/1.1 200 - OK" or "HTTP/1.1 400 - Bad Request")
    public String createResponseStatus(){
        String response = "HTTP/1.1 200 - OK"; 
        try{
            if(this.validGETRequest() == false){
                response = "HTTP/1.1 400 - Bad Request"; 
            }
        }
        // if any exceptions happens in valid checking function, 
        // then this request is not valid
        catch(Exception e){
            response = "HTTP/1.1 400 - Bad Request"; 
        }
        return response; 
    }

    // run the thread to respond to GET request
    // input: no
    // output: no
    @Override 
    public void run(){
        try{
            // lock the feed
            while(true){
                if(this.server.getFeed().getFeedLock() == true){
                    this.server.getFeed().setFeedLock(false); 
                    break; 
                }
            }
            // this is a little workaround for retry on errors
            // this will try to trigger SocketException when the client has closed the socket
            // when client retry on error they closed their socket and try another request
            // if the client has closed the socket, then the server will ignore this request
            // therefore server will respond to only one request when the client retry on error
            this.out.writeObject(""); 
            this.out.writeObject(""); 
            this.out.flush(); 
            // store updatedServerClock to avoid the case that some other thread change the server clock
            // because some other thread may update the server clock after this thread update server's clock but before this thread 
            // piggyback server's clock.
            int updatedServerClock = this.server.updateAndBackUpClock((int)this.request.get("Clock")); 
            Document aggregatedFeed = this.server.getFeed().getCurrentAggregatedFeed(); 
            System.out.println("Message received: "); 
            String requestInString = this.request.toString(); 
            System.out.println(requestInString); 
            // init response which is also a HashMap containing: http status, aggregation server lamport clock and XMLDocument(if successful)
            HashMap<String, Object> responseObject = new HashMap<String, Object>(); 
            responseObject.put("Status", this.createResponseStatus()); 
            responseObject.put("Aggregated-Feed", null); 
            responseObject.put("Clock", updatedServerClock); 
            // if the request is invalid (createResponse() not empty), stop processing
            if(this.createResponseStatus().equals("HTTP/1.1 200 - OK") == false){
                this.out.writeObject(responseObject); 
                this.out.flush(); 
                this.clientSocket.close(); 
                return; 
            }
            //handling valid GET request
            responseObject.remove("Aggregated-Feed"); 
            // put the current aggregated feed in response object
            responseObject.put("Aggregated-Feed", aggregatedFeed); 
            // send response
            this.out.writeObject(responseObject); 
            this.out.flush(); 
            this.clientSocket.close(); 
            // release the lock of Feed for other threads
            this.server.getFeed().setFeedLock(true); 
        }
        // when the socket has been closed prematurely by the client
        // stop processing request and release the lock
        // to move to the next request in queue
        catch(SocketException se){
            this.server.getFeed().setFeedLock(true); 
        }
        catch(Exception e){
            e.printStackTrace(); 
            System.out.println("Error in responding to GET request."); 
            e.printStackTrace(); 
            this.server.getFeed().setFeedLock(true); 
        }
    }
}
