java ContentServer testing 13
DIFF=$(diff --strip-trailing-cr Testing/TestCase13Output.txt Testing/TestCase13ExpectedOutput.txt)
if [ "$DIFF" = "" ]
then
    echo -e "Test case 13: PASSED" >> Testing/TestingLog.txt
else
    echo -e "Test case 13: FAILED" >> Testing/TestingLog.txt
fi
