import java.io.*; 
import java.util.*; 
import java.net.*; 

// this thread will handle 2 types invalid requests:
// 1. the request is not a Hash Map
// 2. request type is not a valid request (INIT/GET/PUT)
// 3. send a null request to Aggregation Server
// 4. the request doesn't contain "Request-Type" field
// those invalid requests will be responded with HTTP 400 
public class ServerInvalidRequestHandler implements Runnable{
    private AggregationServer server; 
    private ObjectInputStream in; 
    private ObjectOutputStream out; 
    private Socket clientSocket; 
    private HashMap<String, Object> request; 

    // this thread is created to respond to a request that we already knew that it's invalid
    // thread constructor
    // input: AggregationServer, Socket (of request sender), ObjectInputStream (on the socket), ObjectOutputStream (on the socket)
    //        HashMap<String, Object> (the request)
    // output: none
    public ServerInvalidRequestHandler(AggregationServer server, Socket clientSocket, ObjectInputStream in, ObjectOutputStream out, HashMap<String, Object> request){
        this.server = server; 
        this.clientSocket = clientSocket; 
        this.out = out; 
        this.in = in; 
        this.request = request; 
    }

    // run the thread to respond to this invalid request
    // input: none 
    // output: none
    @Override 
    public void run(){
        // no updating the server clock because this invalid request might not provide the clock of the sender
        try{
            System.out.println("Message received"); 
            if(this.request != null){
                System.out.println("Invalid request"); 
            }
            else System.out.println(this.request.toString()); 
            // increment clock before sending response
            this.server.getClock().incrementClock(); 
            // send the server clock and httpStatus in the response
            HashMap<String, Object> response = new HashMap<String, Object>(); 
            response.put("Clock", this.server.getClock().getClockValue()); 
            response.put("Status", "HTTP/1.1 400 - Bad Request"); 
            // send response
            this.out.writeObject(response); 
            this.out.flush(); 
            this.clientSocket.close(); 
        }
        catch(Exception e){
            System.out.println("Error in responding to invalid request.");
            e.printStackTrace(); 
        }
    }
}
