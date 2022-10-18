* The purpose of this class is to showcase rudimentary rollbacks and Check And Set behaviors for data stored in Redis
* It only works with binary String types
* under the covers the Strings are stored as Hashes with multiple versions (only 1 extra actually) stored within them
### The program makes use of LUA scripting to execute logic that: 
* checks for valid version numbers [without which, no updates are possible to the protected String values] and
* allows for rollbacks/undo behavior (one level deep) when requested
### LUA scripts are atomic which helps to ensure that no other process can interrupt or muck with the consistency of the protected data
## To run the program execute the following: (replacing host and port values with your own)
```
mvn compile exec:java -Dexec.cleanupDaemonThreads=false -Dexec.args="--host 192.168.1.21 --port 12000"
```
* You can add a username and or password like this:
```
mvn compile exec:java -Dexec.cleanupDaemonThreads=false -Dexec.args="--host 192.168.1.21 --port 12000 --username owen --password supersecret5"
```
* You can add a specific keyname to manipulate like this:
```
mvn compile exec:java -Dexec.cleanupDaemonThreads=false -Dexec.args="--host 192.168.1.21 --port 12000 --keyname mykey"
```
### You can look at the relative costs in speed and memory of storing extra data for rollback purposes in a Hash vs storing a single String by adding this flag:
```
--docompare <someSize>
```
* like this:
```
mvn compile exec:java -Dexec.cleanupDaemonThreads=false -Dexec.args="--host 192.168.1.21 --port 12000 --keyname mykey --docompare 20"
```

### A Sample run of the program with these args: 
```
mvn compile exec:java -Dexec.cleanupDaemonThreads=false -Dexec.args="--host 192.168.1.21 --port 12000 --keyname mykey --docompare 2"
``` 
looks like this:
``` 
Connecting to 192.168.1.21:12000
Key called:mykey begins life with a value of 0
SLF4J: Failed to load class "org.slf4j.impl.StaticLoggerBinder".
SLF4J: Defaulting to no-operation (NOP) logger implementation
SLF4J: See http://www.slf4j.org/codes.html#StaticLoggerBinder for further details.
After 10 iterations of updating the value it now equals: 9
Calling Rollback [only 1 layer deep is implemented)
        Rollback (undo) results in the value now equaling: 8
Calling Rollback [only 1 layer deep is implemented)
        Rollback (undo) results in the value now equaling: 9
Calling Rollback [only 1 layer deep is implemented)
        Rollback (undo) results in the value now equaling: 8
After 3 iterations of rollback (flip/flop/undo) for the value it now equals: 8

Testing Check And Set behavior with new key named: caskey and versionID of 0
        After update --> VersionID of caskey == 1 value of caskey = 169764307691434
Testing Check And Set behavior with wrong/old versionID of 0
        After update --> VersionID of caskey == 1 value of caskey = 169764307691434
        [[[[[[{ ALERT }]]]]]]
        Value not updated! You need to refresh your local copy - current versionID is: 1
Testing Check And Set behavior with versionID of 1
        After update --> VersionID of caskey == 2 value of caskey = 169764360069924


> Comparing performance speed and memory usage between regular String and LUA-managed Hash wrapper    

Writing a simple String with a value of '0' to Redis took 4 milliseconds (including network round trip)
Writing so that rollback is possible with a value of '0' to Redis took 5 milliseconds (including network round trip)
The simple String takes up 56 bytes of memory
The rollback key takes up 117 bytes of memory

****** BIGGER VALUES FOR KEYS **********

Writing a simple String with a much bigger value size to Redis took 8 milliseconds (including network round trip)
Writing so that rollback is possible with a bigger value size to Redis took 8 milliseconds (including network round trip)
result of rollback is: 0
Writing again so that rollback is possible with a much bigger value size in Redis took 7 milliseconds (including network round trip)
The simple String takes up 280 bytes of memory
The rollback key takes up 544 bytes of memory
result of rollback is: 012345676890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ!@@#$%^&*()_+{},.<>/?16976442678700812345676890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ!@@#$%^&*()_+{},.<>/?169764429748690

```