import java.io.*; 
import java.util.*; 
import java.net.*; 
import org.w3c.dom.*; 
import javax.xml.transform.*; 
import javax.xml.parsers.*; 
import java.time.LocalDateTime; 

// this thread will check the content server monitor every 1 second
// if there is any unactive content server in the last 13 seconds
// we will delete feed items from that content server
public class ContentServerMonitor implements Runnable{
    private AggregationServer server; 

    // ContentServerMonitor constructor 
    // input: AggregationServer 
    // output: none
    public ContentServerMonitor(AggregationServer server){
        this.server = server; 
    }

    public void run(){
        while(true){
            // check the content server monitor
            // updating/remove any unactive content server within the last 13 seconds (12 seconds + 1 second prepare for any delay by server)
            // remove feed items from unactive content server
            this.server.checkContentServerMonitor(); 
            try{
                // check after 1 second
                Thread.sleep(1000); 
            }
            catch(InterruptedException e){
                System.out.println("Checking unactive content servers"); 
            }
        }
    }
}
