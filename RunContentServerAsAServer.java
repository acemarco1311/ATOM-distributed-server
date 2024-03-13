import java.io.*; 
import java.net.*; 
import java.util.*; 

// this Thread will run the content server as a server, 
// although in this assignment, this thread will not do anything much
// but Claudia said the Content Server should be able to run as a SERVER 
// so that's what she said :3
public class RunContentServerAsAServer implements Runnable{ 
    private ContentServer contentServer; 
    private String contentServerName; 
    private int contentServerPort; 
    private ServerSocket contentServerSocket; 
    private Socket clientSocket; 
    private ObjectInputStream in; 
    private ObjectOutputStream out; 

    public RunContentServerAsAServer(ContentServer cs){
        this.contentServer = cs; 
        this.contentServerName = "localhost"; //run server on local machine
        boolean complete = false; 
        String startingPort = ""; 
        Scanner keyboard = new Scanner(System.in); 
        while(complete == false){
            System.out.println("Enter the starting port to run this Content Server (press Enter if using the default port 1111): "); 
            try{
                startingPort = keyboard.nextLine(); 
                if(startingPort.isEmpty() == true){
                    startingPort = "1111"; // use the default port
                }
                this.contentServerPort = Integer.parseInt(startingPort); 
                complete = true; 
            }
            catch(Exception e){
                System.out.println("Error, try again."); 
            }
        }
        try{
            this.contentServerSocket = new ServerSocket(this.contentServerPort); 
            System.out.println("Content Server " + contentServer.getId() + " started."); 
            this.in = null; 
            this.out = null; 
            this.clientSocket = null; 
        }
        catch (Exception e){
            System.out.println("Unable to start Content Server "  + contentServer.getId()); 
            e.printStackTrace(); 
        }
    }

    public void runContentServer(){
        try{
            while(true){
                System.out.println("Content Server " + this.contentServer.getId() + " waiting for clients."); 
                // block and wait for clients 
                this.clientSocket = this.contentServerSocket.accept(); 
                // handing request
                this.in = new ObjectInputStream(clientSocket.getInputStream()); 
                this.out = new ObjectOutputStream(clientSocket.getOutputStream()); 
                // behavior of server when receives a request
                // expected to get a request in String
                String request = (String)in.readObject(); 
                System.out.println("Message received: "); 
                System.out.println(request); 
                // respond
                out.writeObject("response from content server " + this.contentServer.getId()); 
            }
        }
        catch (Exception e){
            System.out.println("Connection failed."); 
            e.printStackTrace(); 
        }
    }

    public void shutDownContentServer(){
        try{
            this.contentServerSocket.close(); 
        }
        catch(Exception e){
            System.out.println("Unable to shut down Content Server" + this.contentServer.getId()); 
        }
    }

    public void closeConnection(){
        try{
            this.in.close(); 
            this.out.close(); 
            this.clientSocket.close(); 
        }
        catch(Exception e){
            System.out.println("Unable to close the connection with the client."); 
            e.printStackTrace(); 
        }
    }

    @Override
    public void run(){
        runContentServer(); 
    }

    public static void main(String args[]){
        // close Scanner here
        Scanner keyboard = new Scanner(System.in); 
        keyboard.close(); 
    }
}
