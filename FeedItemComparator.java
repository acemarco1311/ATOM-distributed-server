import java.io.*; 
import java.util.*;
import java.net.*; 
import java.time.LocalDateTime; 
import org.w3c.dom.*; 
import javax.xml.parsers.*; 
import javax.xml.transform.dom.*; 
import javax.xml.transform.stream.*; 
import javax.xml.transform.*; 


// this is the comparator for PriorityQueue of Feed Item based on the timestamp when the server received this feed item
// the item with latest timestamp will be on top
// because we want latest feed item on the top of the aggregated feed
public class FeedItemComparator implements Comparator<FeedItem>{
    // override the compare method of comparator
    // input: 2 FeedItem
    // output: Integer: 
    //              0 if the timestamp of 2 feeditem is the same 
    //              -1 if the timestamp of the first item is after the second 
    //              1 if the timestamp of the first item is before the second
    public int compare(FeedItem first, FeedItem second){
        int output = 0; 
        LocalDateTime firstTimestamp = first.getTimestamp(); 
        LocalDateTime secondTimestamp = second.getTimestamp(); 
        if(firstTimestamp.isAfter(secondTimestamp) == true){
            output = -1; 
        }
        else if(secondTimestamp.isAfter(firstTimestamp) == true){
            output = 1; 
        }
        return output; 
    }
}
