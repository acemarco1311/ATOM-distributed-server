import java.io.*; 
import java.net.*; 
import java.util.*; 

// this thread is responsible for sending heartbeat to the connected 
// Aggregation Server every 12 seconds to maintain the connection
public class ContentServerSendingHeartBeat implements Runnable{
    private static final long INTERVAL_MILLIS = 12000; // send heartbeat every 12 seconds
    private static final int MAX_RETRY = 3; // retry on error, retry 3 times max
    private static final int TIMEOUT_MILLIS = 4000; // timeout = 4 seconds
    private ContentServer contentServer; 
    private ObjectInputStream in; 
    private ObjectOutputStream out; 
    private Socket socket;

    // Thread constructor
    // input: ContentServer
    // output: no
    public ContentServerSendingHeartBeat(ContentServer cs){
        // assumption that: the name + port of the server has been provided correctly in ContentServer constructor
        this.contentServer = cs; 
        this.in = null; 
        this.out = null; 
        this.socket = null; 
    }

    // create a heartbeat request which is a HashMap object
    // input: no
    // output: HashMap
    public HashMap<String, Object> createHeartBeat(){
        HashMap<String, Object> heartBeatObject = new HashMap<String, Object>(); 
        heartBeatObject.put("Request-Type", "Heartbeat"); 
        heartBeatObject.put("Sender-Type", "CS"); 
        heartBeatObject.put("ID", this.contentServer.getId()); 
        heartBeatObject.put("Clock", this.contentServer.getClock().getClockValue()); 
        heartBeatObject.put("Message", "I'm still alive"); 
        return heartBeatObject; 
    }

    // send the heartbeat every 12 seconds
    // input: no
    // output: no
    public void sendHeartBeat(){
        int retryCounter = 0; 
        while(true){
            try{
                // increment clock before sending this request
                this.contentServer.getClock().incrementClock(); 
                this.socket = new Socket(); 
                InetAddress address = InetAddress.getByName(this.contentServer.getConnectingServerName());
                SocketAddress serverAddress = new InetSocketAddress(address, this.contentServer.getConnectingServerPort()); 
                this.socket.connect(serverAddress, TIMEOUT_MILLIS); 
                this.socket.setSoTimeout(TIMEOUT_MILLIS); 
                this.out = new ObjectOutputStream(this.socket.getOutputStream()); 
                this.in = new ObjectInputStream(this.socket.getInputStream()); 
                HashMap<String, Object> heartBeatObject = this.createHeartBeat(); 
                out.writeObject(heartBeatObject); 
                HashMap<String, Object> response = (HashMap<String, Object>)in.readObject(); 
                // update clock
                this.contentServer.getClock().updateClock((int)response.get("Clock")); 
                String responseInString = (String)response.get("Status"); 
                System.out.println(responseInString); 
                this.out.flush(); 
                this.socket.close(); 
                retryCounter = 0; 
                // send another heartbeat after 12s 
                try{
                    Thread.sleep(INTERVAL_MILLIS); 
                }
                catch(InterruptedException e){
                    System.out.println("Sending heart beat thread for Content Server " + this.contentServer.getId() + "interrupted");
                    e.printStackTrace(); 
                }
            }
            // if the server doesn't respond to heartbeat request within timeout, try 2 more times
            // if after total 3 times, still doesn't get response then stop sending heart beat
            catch(SocketTimeoutException ste){
                System.out.println("Server not responding"); 
                retryCounter += 1; 
                if(retryCounter < MAX_RETRY){
                    System.out.println("Retry"); 
                }
                else{
                    System.out.println("Server not responding after 3 attempts, please try later."); 
                    break; 
                }
            }
            catch (Exception e){
                System.out.println("Unable to send the heart beat from Content Server " + this.contentServer.getId()); 
                e.printStackTrace(); 
                break; 
            }
        }
    }

    // run this thread to send heartbeat every 12 seconds
    // input: no
    // output: no
    @Override
    public void run(){ 
        this.sendHeartBeat(); 
    }

    public static void main(String args[]){
        //close Scanner
        Scanner keyboard = new Scanner(System.in); 
        keyboard.close(); 
    }

}
