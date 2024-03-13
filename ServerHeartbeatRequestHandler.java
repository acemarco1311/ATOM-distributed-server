import java.io.*; 
import java.util.*; 
import java.net.*; 
import javax.xml.transform.*; 
import javax.xml.parsers.*; 
import java.time.LocalDateTime; 

// this thread is created to respond to a heartbeat request (which we already knew that it's valid)
public class ServerHeartbeatRequestHandler implements Runnable{
    private AggregationServer server; 
    private ObjectInputStream in; 
    private ObjectOutputStream out; 
    private Socket senderSocket; 
    private HashMap<String, Object> request; 
    private LocalDateTime timestamp; 

    // thread constructor
    // input: AggregationServer, Socket (of heartbeat sender), ObjectInputStream (on the socket), ObjectOutputStream (on the socket)
    //        HashMap<String, Object> (heartbeat request), LocalDateTime (timestamp when the server received this heartbeat)
    // output: none
    public ServerHeartbeatRequestHandler(AggregationServer server, Socket senderSocket, ObjectInputStream in, ObjectOutputStream out, HashMap<String, Object> request, LocalDateTime timestamp){
        this.server = server; 
        this.senderSocket = senderSocket; 
        this.out = out; 
        this.in = in; 
        this.request = request; 
        this.timestamp = timestamp; 
    }
    
    // run the thread to respond to the heartbeat request
    // input: none
    // output: none
    @Override 
    public void run(){
        try{
            System.out.println("Message received: "); 
            System.out.println(this.request.toString()); 
            // update server clock before sending response
            int serverClock = this.server.updateAndBackUpClock((int)this.request.get("Clock")); 
            // get the timestamp here
            // to avoid the case that another thread is using Monitor object leading to being delayed for updating Monitor
            int contentServerID = (int)this.request.get("ID"); 
            // update Monitor object and Monitor Log
            this.server.updateContentServerMonitor(contentServerID,this.timestamp); 
            // create response
            HashMap<String, Object> response = new HashMap<String, Object>(); 
            // put server clock and httpStatus in the response
            response.put("Clock", serverClock); 
            response.put("Status", "HTTP/1.1 200 - OK"); 
            // send response
            this.out.writeObject(response); 
            this.out.flush(); 
            this.senderSocket.close(); 
        }
        catch(Exception e){
            System.out.println("Error in responding to heart beat request"); 
            e.printStackTrace(); 
        }
    }
}
