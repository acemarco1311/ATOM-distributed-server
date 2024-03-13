import java.io.*; 
import java.util.*; 
import java.net.*; 
import java.util.concurrent.atomic.AtomicBoolean; 
import java.time.LocalDateTime; 

// this thread will keep track and execute the requests in request queue
// in producer-consumer pattern
public class ServerRequestHandler implements Runnable{
    private static final int DELAY_TIME = 1; 
    // when there is a request in the queue, allows the server to put more requests within delay time before executing
    private ObjectInputStream in; 
    private ObjectOutputStream out; 
    private AggregationServer server; 

    // ServerRequestHandler constructor
    // input: Aggregation Server
    // output: none
    public ServerRequestHandler(AggregationServer server){
        this.server = server; 
        this.in = null; 
        this.out = null; 
    }

    // run this thread to keep track and respond to all requests in the request queue
    // input: no
    // output: no
    @Override 
    public void run(){
        while(true){
            if(!(this.server.getQueueLock().get())){
                // when the lock is released (a new request has been added) 
                // instead of executing this request immediately, we allow the server to put more requests within 1 seconds then executing all requests
                // this is for the case when the server might get 2 requests with different Lamport clock at the same time 
                // and maybe the request with lower Lamport Clock will be executed first (I don't want that happen)
                LocalDateTime initTimer = LocalDateTime.now(); 
                while(LocalDateTime.now().isAfter(initTimer.plusSeconds(DELAY_TIME)) == false){
                    this.server.getQueueLock().getAndSet(true);  //set the lock back to allow server putting more requests 
                }
                // responding to all requests in queue
                while(this.server.getRequestQueue().peek() != null){
                    try{
                        // get the request on the top of the queue
                        HashMap<String, Object> req = this.server.getRequestQueue().poll(); 
                        // extract info from the requestQueue element
                        Socket clientSocket = (Socket)req.get("Socket"); 
                        this.out = (ObjectOutputStream)req.get("out"); 
                        this.in = (ObjectInputStream)req.get("in"); 
                        HashMap<String, Object> request = (HashMap<String, Object>)req.get("request"); 
                        LocalDateTime timestamp = (LocalDateTime)req.get("timestamp"); 
                        // if the request is GET, create new thread for handling this GET Request
                        if(request != null && request.containsKey("Request-Type") == true && request.get("Request-Type").equals("GET /atom.xml HTTP/1.1") == true){
                            ServerGETRequestHandler handler = new ServerGETRequestHandler(this.server, clientSocket, in, out, request); 
                            Thread executingRequest = new Thread(handler, "Executing GET request"); 
                            executingRequest.start(); 
                        }
                        // if the request is PUT, create new thread for handling this PUT request
                        else if(request != null && request.containsKey("Request-Type") == true && request.get("Request-Type").equals("PUT /atom.xml HTTP/1.1") == true){
                            ServerPUTRequestHandler handler = new ServerPUTRequestHandler(this.server, clientSocket, in, out, request, timestamp); 
                            Thread executingRequest = new Thread(handler, "Executing PUT request");
                            executingRequest.start(); 
                        }
                        // if request = null(request not a hashmap or request object is null itself)
                        // or request obj doesn't contain "Request-Type"
                        // or "Request-Type" != {GET, PUT} 
                        // then open new thread to handle this invalid request
                        // if we have a heartbeat request in here, we already knew that it's an invalid request
                        else {
                            ServerInvalidRequestHandler handler = new ServerInvalidRequestHandler(this.server, clientSocket, in, out, request); 
                            Thread executingRequest = new Thread(handler, "Responding to invalid request"); 
                            executingRequest.start(); 
                        }
                    }
                    catch(Exception e){
                        System.out.println("Error in executing requests in Request Queue."); 
                        e.printStackTrace(); 
                    }
                }
                // set lock back to adding when there is no other requests in queue
                this.server.getQueueLock().getAndSet(true); 
            }
        }
    }


}
