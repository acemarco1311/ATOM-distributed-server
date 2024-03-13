java AggregationServer testing 9 &
# make sure the server has been opened 
# because when I run on uni computer, the server got delayed
# then the client send request before the server open
sleep 0.5
# send 2 requests at nearly the same time
java ContentServer testing 9 &
java GETClient testing 9 &
# wait 10 seconds to make sure the server has been closed
sleep 10
# don't back up the Lamport clock of server for other test cases
# remove content from all the backup files of Aggregation Server
# to make sure that this test case will not affect other test cases
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
DIFF=$(diff --strip-trailing-cr Testing/TestCase9Output.txt Testing/TestCase9ExpectedOutput.txt)
if [ "$DIFF" = "" ]
then
    echo -e "Test case 9: PASSED" >> Testing/TestingLog.txt
else
    echo -e "Test case 9: FAILED" >> Testing/TestingLog.txt
fi
