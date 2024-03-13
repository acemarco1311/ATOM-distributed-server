java GETClient testing 10
DIFF=$(diff --strip-trailing-cr Testing/TestCase10Output.txt Testing/TestCase10ExpectedOutput.txt)
if [ "$DIFF" = "" ]
then
    echo -e "Test case 10: PASSED" >> Testing/TestingLog.txt
else
    echo -e "Test case 10: FAILED" >> Testing/TestingLog.txt
fi
