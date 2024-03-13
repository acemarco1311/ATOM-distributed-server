java AggregationServer testing 16 &
# make sure the server has been opened 
# because when I run on uni computer, the server got delayed
# then the client send request before the server open
sleep 0.5
java ContentServer testing 16
# wait for 5 seconds to make sure the server has been closed
sleep 5
# restart the server
java AggregationServer testing 16 &
# make sure the server has been opened 
# because when I run on uni computer, the server got delayed
# then the client send request before the server open
sleep 0.5
# client send GET after the server restarted
java GETClient testing 16
# wait for 7 seconds to make sure that the test case has been completed
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
DIFF=$(diff --strip-trailing-cr Testing/TestCase16Output.txt Testing/TestCase16ExpectedOutput.txt)
if [ "$DIFF" = "" ]
then
    echo -e "Test case 16: PASSED" >> Testing/TestingLog.txt
else
    echo -e "Test case 16: FAILED" >> Testing/TestingLog.txt
fi
