java AggregationServer testing 12 &
# make sure the server has been opened 
# because when I run on uni computer, the server got delayed
# then the client send request before the server open
sleep 0.5
java GETClient testing 12
# wait to make sure the test case has been done
sleep 20
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
DIFF=$(diff --strip-trailing-cr Testing/TestCase12Output.txt Testing/TestCase12ExpectedOutput.txt)
if [ "$DIFF" = "" ]
then
    echo -e "Test case 12: PASSED" >> Testing/TestingLog.txt
else
    echo -e "Test case 12: FAILED" >> Testing/TestingLog.txt
fi
