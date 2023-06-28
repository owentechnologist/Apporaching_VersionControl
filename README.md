* The purpose of this class is to showcase rudimentary rollbacks and Check And Set behaviors for data stored in Redis
* It only works with binary String types
* under the covers the Strings are stored as Hashes or JSON objects with multiple versions (only 1 extra actually) stored within them
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
* You can add a specific keyname to manipulate like this: (either use --keynamehash or --keynamejson )
```
mvn compile exec:java -Dexec.cleanupDaemonThreads=false -Dexec.args="--host 192.168.1.21 --port 12000 --keynamehash myhashkey2"
```
### You can look at the relative costs in speed and memory of storing extra data for rollback purposes in a Hash vs storing a single String by adding this flag:
```
--docompare true
```
* like this:
```
mvn compile exec:java -Dexec.cleanupDaemonThreads=false -Dexec.args="--host 192.168.1.21 --port 12000 --keyname mykey --docompare true"
```

### A Sample run of the program with these args:  
```
mvn compile exec:java -Dexec.cleanupDaemonThreads=false -Dexec.args="--host redis-14787.homelab.local --port 14787 --usejson true --docompare true"
``` 
looks like this:
``` 
Key called:jsontest{1} begins life with a value of 0

getWithJedisPooled() fetches : 9
After 10 iterations of updating the value it now equals: 9
Calling Rollback [only 1 layer deep is implemented)

getWithJedisPooled() fetches : 8
        Rollback (undo) results in the value now equaling: 8
Calling Rollback [only 1 layer deep is implemented)

getWithJedisPooled() fetches : 9
        Rollback (undo) results in the value now equaling: 9
Calling Rollback [only 1 layer deep is implemented)

getWithJedisPooled() fetches : 8
        Rollback (undo) results in the value now equaling: 8

getWithJedisPooled() fetches : 8
After 3 iterations of rollback (flip/flop/undo) for the value it now equals: 8

Testing Check And Set behavior with new key named: caskey{1} and versionID of 0

        Response from CAS lua call: 1

getWithJedisPooled() fetches : 2272638288021843
        After update --> VersionID of caskey{1} == 1 value of caskey{1} = 2272638288021843
Testing Check And Set behavior with wrong/old versionID of 0

        Response from CAS lua call: 1

getWithJedisPooled() fetches : 2272638288021843
        After update --> VersionID of caskey{1} == 1 value of caskey{1} = 2272638288021843
        [[[[[[{ ALERT }]]]]]]
        Value not updated! You need to refresh your local copy - current versionID is: 1
Testing Check And Set behavior with versionID of 1

        Response from CAS lua call: 2

getWithJedisPooled() fetches : 2272638305599320
        After update --> VersionID of caskey{1} == 2 value of caskey{1} = 2272638305599320


> Comparing performance speed and memory usage between regular String and LUA-managed wrapper    

Writing a simple String with a value of '0' to Redis took 1 milliseconds (including network round trip)
Writing so that rollback is possible with a value of '0' to Redis took 2 milliseconds (including network round trip)
The simple String takes up 56 bytes of memory
The rollback key takes up 76 bytes of memory

****** BIGGER VALUES FOR KEYS **********

Writing a simple String with a much bigger value size to Redis took 2 milliseconds (including network round trip)
Writing so that rollback is possible with a bigger value size to Redis took 2 milliseconds (including network round trip)

getWithJedisPooled() fetches : 0
result of rollback is: 0
Writing again so that rollback is possible with a much bigger value size in Redis took 2 milliseconds (including network round trip)
The simple String takes up 1080 bytes of memory
The rollback key takes up 1091 bytes of memory

getWithJedisPooled() fetches : 012345676890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ!@@#$%^&*()_+{},.<>/?227263832264856212345676890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ!@@#$%^&*()_+{},.<>/?227263832486074612345676890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ!@@#$%^&*()_+{},.<>/?227263832486610412345676890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ!@@#$%^&*()_+{},.<>/?227263832488051112345676890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ!@@#$%^&*()_+{},.<>/?227263832488639612345676890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ!@@#$%^&*()_+{},.<>/?227263832488968712345676890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ!@@#$%^&*()_+{},.<>/?227263832489261512345676890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ!@@#$%^&*()_+{},.<>/?227263832489562712345676890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ!@@#$%^&*()_+{},.<>/?227263832489862612345676890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ!@@#$%^&*()_+{},.<>/?2272638324901464
result of rollback is: 012345676890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ!@@#$%^&*()_+{},.<>/?227263832264856212345676890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ!@@#$%^&*()_+{},.<>/?227263832486074612345676890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ!@@#$%^&*()_+{},.<>/?227263832486610412345676890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ!@@#$%^&*()_+{},.<>/?227263832488051112345676890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ!@@#$%^&*()_+{},.<>/?227263832488639612345676890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ!@@#$%^&*()_+{},.<>/?227263832488968712345676890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ!@@#$%^&*()_+{},.<>/?227263832489261512345676890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ!@@#$%^&*()_+{},.<>/?227263832489562712345676890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ!@@#$%^&*()_+{},.<>/?227263832489862612345676890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ!@@#$%^&*()_+{},.<>/?2272638324901464
```

Or you can run it with --usejson false:
``` 
mvn compile exec:java -Dexec.cleanupDaemonThreads=false -Dexec.args="--host redis-14787.homelab.local --port 14787 --usejson false --docompare true"
```
Which results in this:

``` 
Key called:hashtest{1} begins life with a value of 0
After 10 iterations of updating the value it now equals: 9
Calling Rollback [only 1 layer deep is implemented)
        Rollback (undo) results in the value now equaling: 8
Calling Rollback [only 1 layer deep is implemented)
        Rollback (undo) results in the value now equaling: 9
Calling Rollback [only 1 layer deep is implemented)
        Rollback (undo) results in the value now equaling: 8
After 3 iterations of rollback (flip/flop/undo) for the value it now equals: 8

Testing Check And Set behavior with new key named: caskey{1} and versionID of 0

        Response from CAS lua call: 1
        After update --> VersionID of caskey{1} == 1 value of caskey{1} = 2272988307060534
Testing Check And Set behavior with wrong/old versionID of 0

        Response from CAS lua call: 1
        After update --> VersionID of caskey{1} == 1 value of caskey{1} = 2272988307060534
        [[[[[[{ ALERT }]]]]]]
        Value not updated! You need to refresh your local copy - current versionID is: 1
Testing Check And Set behavior with versionID of 1

        Response from CAS lua call: 2
        After update --> VersionID of caskey{1} == 2 value of caskey{1} = 2272988337410449


> Comparing performance speed and memory usage between regular String and LUA-managed wrapper    

Writing a simple String with a value of '0' to Redis took 2 milliseconds (including network round trip)
Writing so that rollback is possible with a value of '0' to Redis took 2 milliseconds (including network round trip)
The simple String takes up 56 bytes of memory
The rollback key takes up 117 bytes of memory

****** BIGGER VALUES FOR KEYS **********

Writing a simple String with a much bigger value size to Redis took 8 milliseconds (including network round trip)
Writing so that rollback is possible with a bigger value size to Redis took 1 milliseconds (including network round trip)
result of rollback is: 0
Writing again so that rollback is possible with a much bigger value size in Redis took 2 milliseconds (including network round trip)
The simple String takes up 1080 bytes of memory
The rollback key takes up 1344 bytes of memory
result of rollback is: "012345676890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ!@@#$%^&*()_+{},.<>/?227298835192607412345676890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ!@@#$%^&*()_+{},.<>/?227298835371174012345676890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ!@@#$%^&*()_+{},.<>/?227298835371710112345676890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ!@@#$%^&*()_+{},.<>/?227298835372080412345676890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ!@@#$%^&*()_+{},.<>/?227298835372410512345676890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ!@@#$%^&*()_+{},.<>/?227298835372718212345676890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ!@@#$%^&*()_+{},.<>/?227298835373012212345676890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ!@@#$%^&*()_+{},.<>/?227298835373300412345676890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ!@@#$%^&*()_+{},.<>/?227298835373594612345676890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ!@@#$%^&*()_+{},.<>/?2272988353742004"
```