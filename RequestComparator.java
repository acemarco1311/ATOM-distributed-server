import java.io.*; 
import java.util.*; 
import java.net.*; 


// this is a customized comparator for PriorityQueue<Request>
// requests will be sorted based on their Lamport clock 
// the higher Lamport clock, the better
// higher Lamport clock request will be on top
public class RequestComparator implements Comparator<HashMap<String, Object>>{

    //override compare method of comparator
    //input: 2 HashMap<String, Object>, which are 2 request
    //output: Integer: 
    //                 0 if 2 request has the same Lamport clock value
    //                 -1 if the Lamport clock of the first request is larger than the second
    //                 1 if the Lamport clock of the first request is smaller than the second
    public int compare(HashMap<String, Object> first, HashMap<String, Object> second){
        int output = 0; 
        try{
            HashMap<String, Object> firstRequest = (HashMap<String, Object>)first.get("request"); 
            HashMap<String, Object> secondRequest = (HashMap<String, Object>)second.get("request"); 
            int firstRequestClock = (int)firstRequest.get("Clock"); 
            int secondRequestClock = (int)secondRequest.get("Clock"); 
            // we're gonna reverse the output recommended in the Oracle Documentation
            // because the recommended output: 
            // 0 if 2 objects are equal 
            // 1 if the first object > the second object
            // -1 if the first object < the second object
            // these values are used for PriorityQueue with ascending order
            // but in our case we need descending order for the clock(prioritize request with high Lamport Clock) 
            if(firstRequestClock > secondRequestClock){
                output = -1; 
            }
            else if(firstRequestClock < secondRequestClock){
                output = 1; 
            }
        }
        catch(Exception e){
            System.out.println("Error in comparing requests"); 
            e.printStackTrace(); 
        }
        return output; 
    }
}
