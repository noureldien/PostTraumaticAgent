echo // turn off screen text messages
@echo off

echo //set marker called loop, to return to
:loop

echo //start program, with title and program path
java -jar PostTraumaticAgent.jar -agent admin -password admin -host localhost -port 6500 -connection se.sics.tac.aw.TACReader -agentimpl se.sics.tac.aw.PostTraumaticAgent -exitAfterGames 1 -consoleLogLevel 2 -fileLogLevel 2 -logPrefix aw

echo //wait 9  minutes and 10 seconds
timeout /t 550 > null

echo //image name, e.g. WickedArticleCreator.exe, can be found via Task Manager > Processes Tab > Image Name column (look for your program)
echo taskkill /f /im "Image Name" > nul

echo //wait 7 seconds to give your prgram time to close fully - (optional)
echo timeout /t 7 >null

echo //return to loop marker
goto loop