java AggregationServer testing 18 &
# make sure the server has been opened 
# because when I run on uni computer, the server got delayed
# then the client send request before the server open
sleep 0.5
java ContentServer testing 18
# wait for 10 seconds to make sure this test case has been completed
sleep 10
# compare server's FeedLog replication to the main one
DIFFONE=$(diff --strip-trailing-cr AggregationServerFiles/FeedLog.txt AggregationServerFiles/FeedLog_rep_one.txt)
DIFFTWO=$(diff --strip-trailing-cr AggregationServerFiles/FeedLog.txt AggregationServerFiles/FeedLog_rep_two.txt)
DIFFTHREE=$(diff --strip-trailing-cr AggregationServerFiles/FeedLog.txt AggregationServerFiles/FeedLog_rep_three.txt)
if [ "$DIFFONE" = "" ] && [ "$DIFFTWO" = "" ] && [ "$DIFFTHREE" = "" ]
then
    echo -e "Test case 18: PASSED" >> Testing/TestingLog.txt
else
    echo -e "Test case 18: FAILED" >> Testing/TestingLog.txt
fi
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
