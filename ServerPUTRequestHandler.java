import java.io.*; 
import java.util.*; 
import java.net.*; 
import org.w3c.dom.*; 
import javax.xml.transform.*; 
import javax.xml.parsers.*; 
import java.time.LocalDateTime; 
import java.nio.channels.FileLock; 
import java.nio.channels.FileChannel; 
import java.nio.channels.OverlappingFileLockException; 


// this thread will be created to respond to a PUT request
public class ServerPUTRequestHandler implements Runnable{
    private AggregationServer server; 
    private ObjectInputStream in; 
    private ObjectOutputStream out; 
    private Socket senderSocket; 
    private HashMap<String, Object> request; 
    private LocalDateTime timestamp; 
    
    // thread constructor
    // input: AggregationServer, Socket (of the sender), ObjectInputStream (on the Socket), ObjectOutputStream(from the socket)
    //          Hashmap<String, Object> (PUT request), LocalDateTime (timestamp when the server received this request); 
    // output: none
    public ServerPUTRequestHandler(AggregationServer server, Socket senderSocket, ObjectInputStream in, ObjectOutputStream out, HashMap<String, Object> request, LocalDateTime timestamp){
        this.server = server; 
        this.senderSocket = senderSocket; 
        this.out = out; 
        this.in = in; 
        this.request = request; 
        this.timestamp = timestamp; 
    }

    // check if this PUT request has empty content (empty feed items)
    // if yes, then send 204 HTTP code
    // input: no (because the request is already set in constructor)
    // output: boolean (true if this PUT request contain empty content, false otherwise)
    public boolean emptyContentPUTRequest(){
        //XML Document is in Message field
        //so check the Message field and Content-Length of the request
        boolean output = false;
        if(this.request.get("Message") == null || this.request.get("Content-Length") == null){
            return true; 
        }
        long contentLength = (long)this.request.get("Content-Length"); 
        if(contentLength <= 0){
            output = true; 
        }
        return output; 
    }
    
    // this function continue to check if this PUT request is a valid PUT request
    // a valid PUT request must contain all the fields required in appropriate datatype(e.g Message will contain XML Document, Clock contains an integer, Content-Length contains a long, etc), and contain correct information (e.g Content-Length >= 0, correct request type)
    // Message and Content-Length will not be checked as they will be
    // checked in emptyContentPUTRequest()
    // input: no (as the request is already set in the constructor)
    // output: boolean: true if the request contains all required fields in appropriate datatypes, false otherwise
    public boolean validPUTRequest(){
        boolean output = true; 
        // check null of required fields
        if(this.request.get("Request-Type") == null || this.request.get("User-Agent") == null ||
                this.request.get("Content-Type") == null || this.request.get("Sender-Type") == null ||
                this.request.get("ID") == null || this.request.get("Clock") == null){
            return false; 
                }
        // content-length and message have been checked
        String requestType = (String)this.request.get("Request-Type"); 
        String userAgent = (String) this.request.get("User-Agent"); 
        String contentType = (String)this.request.get("Content-Type"); 
        String senderType = (String)this.request.get("Sender-Type"); 
        int id = (int)this.request.get("ID"); 
        int clock = (int)this.request.get("Clock"); 
        if(requestType.equals("PUT /atom.xml HTTP/1.1") == false){
            output = false; 
        }
        else if(userAgent.equals("ATOMClient/1/0") == false){
            output = false; 
        }
        else if(contentType.equals("XML Document" ) == false){
            output = false; 
        }
        else if(senderType.equals("CS") == false){
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

    // this function will initialize the response for this request
    // if this request doesn't contain content, then response = 204 and will not process this request
    // if this request is not valid, response = 400 then will not process
    // otherwise, init response = 200, then process this request. 
    // input: no
    // output: String(initialized response: 200, 204 or 400)
    public String createResponse(){
        String response = "HTTP/1.1 200 - OK"; 
        try{
            if(this.emptyContentPUTRequest() == true){
                response = "HTTP/1.1 204 - No Content"; 
            }
            if(this.validPUTRequest() == false){
                response = "HTTP/1.1 400 - Bad Request"; 
            }
        }
        // if any exception happens during casting, then this is not a valid request
        catch(Exception e){
            response = "HTTP/1.1 400 - Bad Request"; 
        }
        return response; 
    }

    @Override 
    public void run(){
        try{
            // the first thing to do is lock the Feed
            while(true){
                if(this.server.getFeed().getFeedLock() == true){
                    this.server.getFeed().setFeedLock(false); 
                    break; 
                }
            }
            // a little workaround for retry on errors
            // writing to the stream to check if the Socket has been closed or not 
            // if the socket has been closed, SocketException will be thrown
            // and the server ignore this request
            this.out.writeObject(""); 
            this.out.writeObject(""); 
            this.out.flush(); 
            System.out.println("Message received: "); 
            String requestInString = this.request.toString(); 
            System.out.println(requestInString); 
            // init response
            HashMap<String, Object> response = new HashMap<String, Object>(); 
            // update server clock: max() + 1 when receive request
            int updatedServerClock = this.server.updateAndBackUpClock((int)this.request.get("Clock")); 
            // store updatedServerClock to avoid the case that some other thread change the server clock
            // because some other thread may update the server clock after this thread update server's clock but before this thread 
            // piggyback server's clock.
            // this can happen although we already declare updateAndBackUpClock() is synchronized 
            response.put("Clock", updatedServerClock); 
            String responseStatus = this.createResponse(); 
            response.put("Status", responseStatus); 
            // if the request is not valid (empty xml or not enough info) 
            // then we will not process this PUT request.
            if(responseStatus.equals("HTTP/1.1 200 - OK") == false){
                this.out.writeObject(response); 
                this.out.flush(); 
                this.senderSocket.close(); 
                return; 
            }
            // handling valid PUT request
            Document message = (Document)this.request.get("Message"); 
            int contentServerID = (int)this.request.get("ID"); 
            // check the feedLog before request
            boolean isFeedLogEmptyBeforeRequest = this.server.isFeedLogEmpty(); 
            // add to Feed
            this.server.getFeed().add(message,contentServerID, this.timestamp); 
            // update the timestamp of the last communication for this server in the monitor
            this.server.updateContentServerMonitor(contentServerID, this.timestamp); 
            // recheck the response
            boolean isFeedLogEmptyAfterRequest = this.server.isFeedLogEmpty(); 
            // if PUT request is handled successfully and this request
            // created the FeedLog in the Aggregation Server for the 
            // first time then we will send 201 - HTTP_CREATED
            if(responseStatus.equals("HTTP/1.1 200 - OK") == true && 
                    isFeedLogEmptyBeforeRequest == true && 
                    isFeedLogEmptyAfterRequest == false){
                        responseStatus = "HTTP/1.1 201 - HTTP_CREATED"; 
                    }
            //update http status in the response object
            response.remove("Status"); 
            response.put("Status", responseStatus); 
            this.out.writeObject(response); 
            this.out.flush(); 
            this.senderSocket.close(); 
            // release the lock of Feed for other threads
            this.server.getFeed().setFeedLock(true); 
        }
        // when the socket has been closed prematurely by the content server
        // stop processing request and release the lock to move to the next request in queue
        catch(SocketException se){
            this.server.getFeed().setFeedLock(true); 
        }
        catch(Exception e){
            System.out.println("Error in responding to PUT request."); 
            e.printStackTrace(); 
        }
    }
}
