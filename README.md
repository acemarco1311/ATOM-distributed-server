**Name: Nguyen Thanh Toan LE** 

# Introduction
Communication between clients, aggregation servers.  
Aggregation server, client and content servers are provided in 2 mode: normal mode and testing mode.  
In the normal mode, the client will send 1 GET request to the aggregation server then die, content server will send 1 PUT request then wait for 2 seconds then start sending heartbeat to aggregation server. Aggregation server in normal mode will run the server to listen to requests and also other works (e.g backup Lamport clock, FeedLog, FeedLog replications, etc). Therefore, in the normal mode, the content server and aggregation server will run forever until they're closed manually.  
In the testing mode, the behaviour of client, aggregation server and content server will be different based on the test case. The behavior is defined in their main() method. Please read more information about test cases below.  


# How to compile files
All the Java files can be compiled by using the command: `javac -target 8 -source 8 *.java`

# How to run the program in normal mode (for manual testing)
- Run the Aggregation Server by: `java AggregationServer normal`
    - The terminal will ask for the port to listen to request, press `Enter` if you want to use the default port **4567**
    - The default name of Aggregation Server will be: **localhost**
- Run the Content Server by: `java ContentServer normal` 
    - The terminal will ask for **URL** and the file location to get the feed items from to send PUT request. 
    - The example of URL is `localhost:4567` 
    - The 3 provided files for content server is in **ContentServerFiles** folder
    - So the example of file location will be: `ContentServerFiles/ContentServer1.txt`
    - After you enter all the information, the Content Server will parse the file you provide the location to XML Document to send PUT to the server
- Run GET client by: `java GETClient normal`
    - The terminal will ask for URL to connect to the server
    - Then the client will send a GET request to server 

# Automation testing strategy
There are 18 test cases provided for this program. For each test case, the behaviors of client, aggregation server and content servers will be different and are defined in their main() method.  
To run a class for a test case, we can use the command in format: `java ClassName testing testCaseIndex`  
For example, use the command `java GETClient testing 1` then the client will behave as defined in test case 1. (see below)
Each test case has a shell script to run GETClient, Aggregation Server, Content Server as defined in the test case description.  
After the test case has been completed, the script will reset the state (all the server's backup files) to make sure that the test case will not affect other test cases because of the server backup.   
Each test case also has the output and expected output file in **Testing** folder, after the test case has been completed, the shell script will compare the output and the expected output file, then the script will write the result of the test case to **Testing/TestingLog.txt**  

# How to run automation testing
The automation testing can be run by the command: `sh OverallTestScript.sh`  
This test script will run 18 test cases and write the result to **Testing/TestLog.txt**  
This script will take a few minutes to complete, the reason why it takes so long is that in some test cases the client/content server send requests after x seconds and at the end of each test case, the script will wait for x seconds to make sure that the test case has been completed (server has been closed) to reset the server state.  

# How to run a specific test case
If you want to run a specific test case (rather than all 18 test cases), you can use the command: `sh TestCaseXScript.sh`
    - For example, if you want to run test case 1, you need to use: `sh TestCase1Script.sh`

# Note for running automation testing
For the best result, please use Linux to test program.  
If you are using Window/Unix, please go the test scripts and remove `--strip-trailing-cr` in each test script in the line which starts with `DIFF=`  
The reason is that I coded on Unix-like terminal then I tested my program on Window everything worked fine, however, when I tested on the university computer (Linux Redhat), my test script failed because of the different line break between Window and Linux so I added `--strip-trailing-cr` to ignore the line break then the test script worked fine on the university computer (Linux Redhat).  
However, when I tested again on Window, the option `--strip-trailing-cr` cannot be recognized.  
So that, using Linux for testing this program is recommended.  

# How GETClient works? 
In the normal mode, the GETClient will send a single GET request as a HashMap object containing information: request type, sender type, client id, client clock, and message.  
The client will receive response as a HashMap object from the server containing: HTTP code for the request (successful or not), clock of server, and the aggregated feed (in XML Document object).  
If the request is successful, then the client will parse the aggregated feed to normal text and print out the feed.  
The client will only retry if the server is available, client can connect to server, client can send request but cannot receive response back within the timeout 4 seconds.  
The client will retry 3 times, if cannot get the response after 3 retry then the client will give up.
NOTE: Run GETClient.java in 2 separate terminals will not create 2 Clients because their id will be the same, so if you want to have 2 Clients please modify the main() method of GETClient.java 

# How AggregationServer works? 
In the normal mode, the Aggregation Server has 1 thread to listen to requests, when this thread receive a request, it will put the request in request queue and wait for 1 second to see if there is any other request. After that, another thread, which is responsible for responding to all the requests in the queue, will start responding to requests in the request queue.   
The requests in the request queue are ordered by Lamport clock, meaning that within that 1 seconds, the request with higher Lamport clock will be processed first.   
This request queue is implemented based on producer-consumer pattern (the thread listen for requests is the producer, the thread keeps track of the request queue is the consumer).   
The Feed item in the server will be ordered based on the time they came to the server, meaning that the newest feed will be on top.   
The AggregationServer backup all its state (FeedLog, FeedLog replications, ContentServerMonitor, Lamport Clock) in the **AggregationServerFiles** folder and will restore them when the server starts (if they are not empty).  
The Aggregation Server has the ContentServerMonitor which is a HashMap whose kep is the content server id and the value is the time of the last communication with that content server.   
Every second, the Aggregation Server use the ContentServerMonitor thread to check which content server has not communicated in the last 13 seconds (12 seconds just like in the description + 1 second prepared for any delay or waiting in the server side).

# How ContentServer works? 
In the normal mode, the content server will send a single PUT request as a HashMap object containing information: request type, user agent, content type, content length, sender type, content server id, content server clock and message (XML Document).   
2 second after the PUT request, the content server will start sending heartbeat every 12 seconds to the aggregation server.   
The content server will parse the given file to XML Document to the Aggregation Server.   
If the request has been successfull, then the content server will receive HTTP 201 or HTTP 200.  
The content server will only retry if the server is available, content server can connect to server, content server can send request but cannot receive response back within the timeout 4 seconds.   
The content server will retry 3 times, if cannot get the response after 3 retry then the content server will give up.
NOTE: Run ContentServer.java in 2 separate terminals will not create 2 Content Server because their id will be the same, so if you want to have 2 Content Server please modify the main() method of ContentServer.java 

# Test case description 
I will cover all 18 test cases, what feature will be tested, how the client, server, content servers will behave and also the expected output   
**NOTE**: the feed items in the aggregated feed in the server will be ordered based on the time they came to the server, meaning that the newest feed items will be on the top of the aggregated feed. We'll have a problem is that when the content server send multiple feed items at the same time, we will not know the order of those feed items hence it's hard to design the expected output. However, for each test case, the order of feed items being sent at the same time will be consistent.  
The way I design my expected output for a test case (where we have multiple feed items being sent at the same time) is that I run the test case manually to check the order of those feed items. Because in my machine, the order of feed items for a test case will remain the same no matter how many time I run the test case.  
So if by any chance, you run the test case on your machine and see the order of feed items (being sent at the same time) is different than the order in my expected output, please keep in mind that will not be a problem and the test case has been completed as expected.
## Test case 1
To run this test case, please use: `sh TestCase1Script.sh`  
The output of this test case will be in: `Testing/TestCase1Output.txt`  
The result of this test case will be in: `Testing/TestingLog.txt`  
In the test case 1, we will try to test GET request and the Lamport clock.  
We will run the Aggregation Server to listen to the requests, then the client will send a GET request.  
Because the server has not received any PUT request, therefore, the client is expected to receive an empty feed.  
The client starts at clock 0, then the client will increment its clock to 1 before sending GET request.  
The server starts at clock 0, the server will update its clock to max(1, 0) + 1 = 2 when receives GET request and send response.  
When the client receives the response, it will update its clock to 3.   
Overall, we expect to see the client receives an empty aggregated feed and the clock of client at the end will be 3.

## Test case 2
To run this test case, please use: `sh TestCase2Script.sh`  
The output of this test case will be in: `Testing/TestCase2Output.txt`  
The result of this test case will be in: `Testing/TestingLog.txt`  
In the test case 2, we will try to test PUT request, GET request, Lamport Clock, Server rejects invalid feed items.   
The content server will send 1 PUT from `ContentServerFiles/ContentServer1.txt`, this file has 4 feed items (but one of them is invalid).   
The content server in this test case will not send heartbeat following up to make it easier to test Lamport clock   
The server will reject the invalid feed and put 3 feed items to the aggregated feed.   
Then the client will send 1 GET request right after the PUT request has been completed  
We expect to see the client will get 3 valid feed items and clock of client at the end = 4 (the content server start at 0, increment to 1 to send PUT, then server updates its clock to 2, the client starts at 0, increment to 1 to send GET, the server update its clock to 3 then the client update its clock to 4 when receives the response for GET request)

## Test case 3
To run this test case, please use: `sh TestCase3Script.sh`   
The output of this test case will be in: `Testing/TestCase3Output.txt`   
The result of this test case will be in: `Testing/TestingLog.txt`   
In the test case 3, we will try to test 2 PUT request from different content servers (without heartbeat), feed item ordering.  
The content server 1 will send 1 PUT from `ContentServerFiles/ContentServer1.txt` (without heartbeat following up).   
The content server 2 will send 1 PUT from `ContentServerFiles/Contentserver2.txt` (without heartbeat following up).   
After 2 PUT requests have been completed, the client will send a GET request.  
We expect to see the client will receive the aggregated feed containing 4 feed items (3 from ContentServer1.txt and 1 from ContentServer2.txt) with the item from content server 2 on the top.  
And the expected clock value of GET client at the end of the test is 5

## Test case 4
To run this test case, please use: `sh TestCase4Script.sh`   
The output of this test case will be in: `Testing/TestCase4Output.txt`   
The result of this test case will be in: `Testing/TestingLog.txt`   
In the test case 4, we will try to test content server maintaining heartbeat, server keeps the feed items of the content server.   
The content server will send PUT request from `ContentServerFiles/ContentServer1.txt` and maintaing the heartbeat.   
After 15 seconds, the client will send a GET request.   
We expect to see the client receive the aggregated feed containing 3 feed items from ContentServer1.txt because the content server has been maintaining the heartbeat.

## Test case 5
To run this test case, please use: `sh TestCase5Script.sh`  
The output of this test case will be in: `Testing/TestCase5Output.txt`   
The result of this test case will be in: `Testing/TestingLog.txt`   
In the test case 5, we will try to test the server delete feed items from unactive content server.   
The content server 1 will send PUT request from `ContentServerFiles/ContentServer1.txt` without heartbeat following up.   
The content server 2 will send PUT request from `ContentServerFiles/ContentServer2.txt` and keep maintaining heartbeat.   
After 15 seconds, the client sends a GET request.   
We expect to see the client will receive the aggregated feed containing only 1 feed item from `ContentServerFiles/ContentServer2.txt` because the content server 1 has not been maintaining the heartbeat.

## Test case 6
To run this test case, please use: `sh TestCase6Script.sh`   
The output of this test case will be in: `Testing/TestCase6Output.txt`   
The result of this test case will be in: `Testing/TestingLog.txt`   
In the test case 6, we will try to test the server update a specific feed item to its newest version.  
Any feed item has its own id, if 2 feed items has the same id and came from the same content server (has the same content server id) then these 2 feed items are considered as duplicate feed items.   
If the server receive a PUT request and see an duplicate feed item in the PUT request, then the server will delete the current version in the current aggregated feed and update by the new version of that feed items in the PUT request.   
The content server (has id = 1) will send the first PUT request from `ContentServerFiles/ContentServer1.txt`   
Then the content server will send the second PUT request from `ContentServerFiles/ContentServer2.txt`  
The only feed item from ContentServer2.txt has the same id with one of the feed items from ContentServer1.txt  
So the server will delete the older version (the one in ContentServer1.txt) and replace by the newer version (the one in ContentServer2.txt).   
Then the client will send a GET request.   
We expect to see the client will receive the aggregated feed containing 3 feed items (1 from ContentServer2.txt and 2 from ContentServer1.txt because the duplicate feed item from ContentServer1.txt has been replaced)

## Test case 7
To run this test case, please use: `sh TestCase7Script.sh`   
The output of this test case will be in: `Testing/TestCase7Output.txt`   
The result of this test case will be in: `Testing/TestingLog.txt`  
In the test case 7, we will try to test the server stores the 20 most recent feed items, 3 content servers sending PUT and maintaining heartbeat.   
The content server 1 will send PUT request from `ContentServerFiles/ContentServer1.txt` (which has 3 valid feed items) and maintain the heartbeat.  
The content server 2 will send PUT request from `ContentServerFiles/ContentServer2.txt` (which has 1 valid feed items) and mainatain the heartbeat.   
The content server 3 will send PUT request from `ContentServerFiles/ContentServer3.txt` (which has 17 valid feed items) and maintain the heartbeat.   
In total, we have been sending 21 feed items to the Aggregation Server.   
After 15 seconds, the client will send a GET request.  
We expect to se the client will receive the aggregated feed containing 20 feed items (one of the feed items from content server 1 was deleted because feed items from content server 1 are the oldest).

## Test case 8
To run this test case, please use: `sh TestCase8Script.sh`  
The output of this test case will be in: `Testing/TestCase8Output.txt`  
The result of this test case will be in: `Testing/TestingLog.txt`  
In the test case 8, we will try to test request ordering in the server, the request will be ordered based on the Lamport clock of the request, meaning that if 2 requests are sent at the same time, the one with higher Lamport clock will be processed first.   
The content server will start at clock = 1, while the client starts at clock = 0  
The content server will send PUT from `ContentServerFiles/ContentServer1.txt` and the client will send GET at nearly the same time.   
Because the PUT request has higher Lamport clock so we expect the PUT request will be processed first, therefore, the client will receive the aggregated feed containing 3 feed items from `ContentServerFiles/ContentServer1.txt`.  
The expected clock value of client at the end of this test case is 5.

## Test case 9
To run this test case, please use: `sh TestCase9Script.sh`  
The output of this test case will be in: `Testing/TestCase9Output.txt`  
The result of this test case will be in: `Testing/TestingLog.txt`  
In the test case 9, we will do the same as we did in the test case 8 (but reversely) to make sure the request ordering is really working.  
The content server will start at clock = 0, while the client will start at clock = 1.  
The content server will send PUT from `ContentServerFiles/ContentServer1.txt` and the client will send GET at nearly the same time.  
Because the GET request has higher Lamport clock so we expecte the GET request will be processed first, therefore, the client will receive an empty aggregated feed.  
The expected clock value of client at the end of this test case is 4. (start at 1, increment to 2 to send GET, server update clock to 3, client update clock to 4 when receives response).

## Test case 10
To run this test case, please use: `sh TestCase10Script.sh`  
The output of this test case will be in: `Testing/TestCase10Output.txt`   
The result of this test case will be in: `Testing/TestingLog.txt`  
In the test case 10, we will try to test the client gives up when the server is not available. In my implementation, the client/content server only retry when the server is online, request can be sent.  
So if the server is not available (cannot connect to the server) then the client will give up.  
We will not turn the server on in this test case, hence we expect to see the output from client "Server is temporarily unavailable, please try later."

## Test case 11 
To run this test case, please use: `sh TestCase11Script.sh`  
The output of this test case will be in: `Testing/TestCase11Output.txt`  
The result of this test case will be in: `Testing/TestingLog.txt`  
In the test case 11, we will try to test the client send GET request then retry 3 times when the client is unable to get the response within the timeout (4s).  
We will turn the server on (listen to request only not responding) so we expect the client will retry 3 times and gives up.

## Test case 12
To run this test case, please use: `sh TestCase12Script.sh`  
The output of this test case will be in: `Testing/TestCase12Output.txt`  
The result of this test case will be in: `Testing/TestingLog.txt`  
In the test case 12, we will try to test the client send GET request then retry 2 times when the client is unable to get the response within the timeout (4s) then in the third retry the client will get an empty aggregated feed from the server.  
We will also see that the server will only respond to the request in the third retry.

## Test case 13
To run this test case, please use: `sh TestCase13Script.sh`  
The output of this test case will be in: `Testing/TestCase13Output.txt`   
The result of this test case will be in: `Testing/TestingLog.txt`  
In the test case 13, we will try to test the content server gives up when the server is not available. In my implementation, the client/content server only retry when the server is online, request can be sent.  
So if the server is not available (cannot connect to the server) then the content server will give up.  
We will not turn the server on in this test case, hence we expect to see the output from client "Server is temporarily unavailable, please try later."

## Test case 14 
To run this test case, please use: `sh TestCase14Script.sh`  
The output of this test case will be in: `Testing/TestCase14Output.txt`  
The result of this test case will be in: `Testing/TestingLog.txt`  
In the test case 14, we will try to test the content server send PUT request then retry 3 times when the client is unable to get the response within the timeout (4s).  
We will turn the server on (listen to request only not responding) so we expect the content server will retry 3 times and gives up.

## Test case 15
To run this test case, please use: `sh TestCase15Script.sh`  
The output of this test case will be in: `Testing/TestCase15Output.txt`  
The result of this test case will be in: `Testing/TestingLog.txt`  
In the test case 15, we will try to test the content server send PUT request then retry 2 times when the client is unable to get the response within the timeout (4s) then in the third retry the content server will get "HTTP/1.1 201 - HTTP_CREATED" from the server.  
We will also see that the server will only respond to the request in the third retry.

## Test case 16
To run this test case, please use: `sh TestCase16Script.sh`  
The output of this test case will be in: `Testing/TestCase16Output.txt`  
The result of this test case will be in: `Testing/TestingLog.txt`  
In the test case 16, we will try to test the server backup its state (FeedLog, Lamport Clock, ContentServerMonitor) and being able to restore them after being restarted.  
The content server 1 will send a PUT request from `ContentServerFiles/ContentServer1.txt` and then the content server 2 will send a PUT request from `ContentServerFiles/ContentServer2.txt`   
After that, we will shut down the server and turn it back on.   ContentServerrement to 1 to send PUT, server update its clock to 2, CS2 starts at 0, increment to 1 to send PUT, server update its clock to 3, then the server is shut down and restore clock = 3 back, client starts at 0, increment to 1 to send GET, the server update its clock to 4, the client update its clock to 5 when receives response).

## Test case 17 
To run this test case, please use: `sh TestCase17Script.sh`  
The output of this test case will be in: `Testing/TestCase17Output.txt`  
The result of this test case will be in: `Testing/TestingLog.txt`  
In the test case 17, we will try to test the server backup its state (similar to test case 16) but this time we focus more on ContentServerMonitor
The content server 1 will send a PUT request from `ContentServerFiles/ContentServer1.txt` and then the content server 2 will send a PUT request from `ContentServerFiles/ContentServer2.txt`   
After that, we will shut down the server and turn it back on.   
The client will send GET after 15 seconds.   
Because the server is able to restore the ContentServerMonitor then when the server restarts, after 12 seconds, it will delete all the feed items because Content Server 1 and Content Server 2 has not been maintain heartbeat.   
Therefore, we expect to see the client receive an empty feed and (again) the expected clock value of client at the end of the test case is 5.

## Test case 18
To run this test case, please use: `sh TestCase18Script.sh`  
This test case doesn't have output and expected output  
The result of this test case will be in: `Testing/TestingLog.txt`  
In the test case 18, we will try to test the server backup FeedLog to 3 FeedLog replications.  
The content server 1 will send a PUT request from `ContentServerFiles/ContentServer1.txt` and then the content server 2 will send a PUT request from `ContentServerFiles/ContentServer2.txt`.   
After that, the test script will compare 3 FeedLog replications to the main FeedLog, if they match, then the test is passed. If the FeedLog replications are not the same as the main FeedLog after 2 PUT requests then the test case fails.

