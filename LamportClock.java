public class LamportClock{

    private int clockValue; 

    //constructor: initialize the logical clock value = 0
    //input: no
    //output: no
    public LamportClock(){
        this.clockValue = 0; 
    }

    // getter: get the private member variable clockValue
    // input: no. 
    // output: Integer (clockValue)
    public int getClockValue(){
        return this.clockValue;
    }  
    
    // setter: set a value to the current clock value 
    // input: Integer (new value for the clock)
    // output: no
    public void setClockValue(int newValue){
        this.clockValue = newValue; 
    } 

    // update the current clock value
    // new clock value = max(this clock, another clock) + 1
    // input: int: Lamport Clock value of the externalClock
    // output: no
    public void updateClock(int externalClock){
        int max = this.clockValue; 
        if (externalClock > max){
            max = externalClock; 
        }
        // follow Lamport's algorithm, set max then increment
        this.clockValue = max; 
        this.incrementClock();
    }

    
    // increment the current Lamport clock value
    // input: no. 
    // output: no. 
    public void incrementClock(){
        this.clockValue++; 
    }

    public static void main(String args[]){
        // manual unit test
        LamportClock clock = new LamportClock(); 
        System.out.println(clock.getClockValue()); 
        clock.incrementClock(); 
        System.out.println(clock.getClockValue());
        LamportClock externalClock = new LamportClock(); 
        externalClock.setClockValue(10); 
        clock.updateClock(externalClock.getClockValue()); 
        System.out.println(clock.getClockValue()); 
    }
}
