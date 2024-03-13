java AggregationServer testing 17 &
# make sure the server has been opened 
# because when I run on uni computer, the server got delayed
# then the client send request before the server open
sleep 0.5
java ContentServer testing 17
# wait for 20 seconds to make sure the server has been closed
sleep 20
# restart the server
java AggregationServer testing 17 &
# make sure the server has been opened 
# because when I run on uni computer, the server got delayed
# then the client send request before the server open
sleep 0.5
# client wait for 15 seconds after the server restarted then send GET
java GETClient testing 17
# wait for 7 seconds to make sure this test case has been completed
sleep 7
# delete server's backup so that it will not affect other test cases
rm AggregationServerFiles/FeedLog.txt
touch AggregationServerFiles/FeedLog.txt
rm AggregationServerFiles/LamportClock.txt
touch AggregationServerFiles/LamportClock.txt
rm AggregationServerFiles/ContentServerMonitor.txt
touch AggregationServerFiles/ContentServerMonitor.txt
rm AggregationServerFiles/FeedLog_rep_one.txt
touch AggregationServerFiles/FeedLog_rep_one.txt
rm AggregationServerFiles/FeedLog_rep_two.txt
touch AggregationServerFiles/FeedLog_rep_two.txt
rm AggregationServerFiles/FeedLog_rep_three.txt
touch AggregationServerFiles/FeedLog_rep_three.txt
DIFF=$(diff --strip-trailing-cr Testing/TestCase17Output.txt Testing/TestCase17ExpectedOutput.txt)
if [ "$DIFF" = "" ]
then
    echo -e "Test case 17: PASSED" >> Testing/TestingLog.txt
else
    echo -e "Test case 17: FAILED" >> Testing/TestingLog.txt
fi
