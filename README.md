* The purpose of this class is to showcase rudimentary rollbacks and Check And Set behaviors for data stored in Redis
* It only works with binary String types
* under the covers the Strings are stored as Hashes with multiple versions stored within them
### The program makes use of LUA scripting to execute logic that: 
* checks for valid version numbers [without which, no updates are possible to the protected String values] and
* allows for rollbacks (one level deep) when requested
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
### You can look at the relative costs in speed and mempry of storing extra data for rollback purposes in a Hash vs storing a single String by adding this flag:
```
--docompare <someSize>
```
* like this:
```
mvn compile exec:java -Dexec.cleanupDaemonThreads=false -Dexec.args="--host 192.168.1.21 --port 12000 --keyname mykey --docompare 20"
```
