package com.redislabs.sa.ot.apv;
import redis.clients.jedis.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * The purpose of this class is to showcase rudimentary rollbacks and Check And Set behaviors for data stored in Redis
 * It only works with binary String types
 * under the covers the Strings are stored as Hashes with multiple versions stored within them
 * To run the program execute the following: (replacing host and port values with your own)
 * mvn compile exec:java -Dexec.cleanupDaemonThreads=false -Dexec.args="--host 192.168.1.21 --port 12000"
 * You can add a username and or password like this:
 * mvn compile exec:java -Dexec.cleanupDaemonThreads=false -Dexec.args="--host 192.168.1.21 --port 12000 --username owen --password supersecret5"
 * You can add a specific keyname to manipulate like this:
 * mvn compile exec:java -Dexec.cleanupDaemonThreads=false -Dexec.args="--host 192.168.1.21 --port 12000 --keyname mykey"
 * You can look at the relative costs in speed and mempry of storing extra data for rollback purposes in a Hash vs storing a single String by adding this flag:
 * --docompare <someSize>
 * mvn compile exec:java -Dexec.cleanupDaemonThreads=false -Dexec.args="--host 192.168.1.21 --port 12000 --keyname mykey --docompare 20"
 *  *
 */
public class Main {

    public static void main(String[] args) {
        String host = "192.168.1.21";
        Integer port = 12000;
        String username = "default";
        String password = "";
        String keyname = "testkey{1}";
        boolean doCompare = false;
        int compareSize = 0; //not used unless user sets a new value

        if(args.length>0){
            ArrayList<String> argList = new ArrayList<>(Arrays.asList(args));
            if(argList.contains("--host")){
                int hostIndex = argList.indexOf("--host");
                host = argList.get(hostIndex+1);
            }
            if(argList.contains("--port")){
                int portIndex = argList.indexOf("--port");
                port = Integer.parseInt(argList.get(portIndex+1));
            }
            if(argList.contains("--username")){
                int userNameIndex = argList.indexOf("--username");
                username = argList.get(userNameIndex+1);
            }
            if(argList.contains("--password")){
                int passwordIndex = argList.indexOf("--password");
                password = argList.get(passwordIndex + 1);
            }
            if(argList.contains("--keyname")){
                int keynameIndex = argList.indexOf("--keyname");
                keyname = argList.get(keynameIndex+1);
            }
            if(argList.contains("--docompare")){
                int docompareIndex = argList.indexOf("--docompare");
                compareSize = Integer.parseInt(argList.get(docompareIndex+1));
                doCompare = true;
            }

        }
        HostAndPort hnp = new HostAndPort(host,port);
        System.out.println("Connecting to "+hnp.toString());
        URI uri = null;
        try {
            if(!("".equalsIgnoreCase(password))){
                uri = new URI("redis://" + username + ":" + password + "@" + hnp.getHost() + ":" + hnp.getPort());
            }else{
                uri = new URI("redis://" + hnp.getHost() + ":" + hnp.getPort());
            }
        }catch(URISyntaxException use){use.printStackTrace();System.exit(1);}

        test(uri,keyname);
        if(doCompare) {
            compareNumbers(uri,compareSize);
        }
    }

    private static void compareNumbers(URI uri,int compareSize) {
        System.out.println("\n\n> Comparing performance speed and memory usage between regular String and LUA-managed Hash wrapper    \n");
        String simplekey = "simplekey";
        String rollbackKey = "rollbackKey";
        String payload = "0";
        try (UnifiedJedis jedis = new UnifiedJedis(uri)) {
            jedis.del(rollbackKey);
            jedis.del(simplekey);
            long startTime = System.currentTimeMillis();
            jedis.set(simplekey, payload);
            System.out.println("Writing a simple String with a value of '0' to Redis took " + (System.currentTimeMillis() - startTime) + " milliseconds (including network round trip)");
        }
        System.out.println("Writing so that rollback is possible with a value of '0' to Redis took " +
                set(uri, rollbackKey, payload) + " milliseconds (including network round trip)");
        try (UnifiedJedis jedis = new UnifiedJedis(uri)) {
            System.out.println("The simple String takes up "+jedis.memoryUsage(simplekey)+" bytes of memory");
            System.out.println("The rollback key takes up "+jedis.memoryUsage(rollbackKey)+" bytes of memory");
        }
        System.out.println("\n****** BIGGER VALUES FOR KEYS **********\n");
        // Make the payload (value for the keys) much larger:
        for(int x = 0;x<compareSize;x++){
            payload+="12345676890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ!@@#$%^&*()_+{},.<>/?";
            payload+=System.nanoTime(); // to ensure the value is different each time
        }
        try (UnifiedJedis jedis = new UnifiedJedis(uri)) {
            long startTime = System.currentTimeMillis();
            jedis.set(simplekey, payload);
            System.out.println("Writing a simple String with a much bigger value size to Redis took " + (System.currentTimeMillis() - startTime) + " milliseconds (including network round trip)");
        }
        System.out.println("Writing so that rollback is possible with a bigger value size to Redis took " +
                set(uri, rollbackKey, payload) + " milliseconds (including network round trip)");
        //Call rollback so that the big value gets swapped into the backup slot:
        rollback(uri,rollbackKey);
        System.out.println("result of rollback is: "+get(uri,rollbackKey));
        System.out.println("Writing again so that rollback is possible with a much bigger value size in Redis took " +
                set(uri, rollbackKey, payload) + " milliseconds (including network round trip)");
        try (UnifiedJedis jedis = new UnifiedJedis(uri)) {
            System.out.println("The simple String takes up "+jedis.memoryUsage(simplekey)+" bytes of memory");
            System.out.println("The rollback key takes up "+jedis.memoryUsage(rollbackKey)+" bytes of memory");
        }
        System.out.println("result of rollback is: "+get(uri,rollbackKey));
    }

    private static void test(URI uri, String keyname){
        System.out.println("Key called:"+keyname+" begins life with a value of 0");
        for(int x=0;x<10;x++) {
            set(uri, keyname, ""+x);
        }
        System.out.println("After 10 iterations of updating the value it now equals: "+get(uri,keyname));
        for(int y=0;y<3;y++){
            System.out.println("Calling Rollback [only 1 layer deep is implemented)");
            rollback(uri,keyname);
            System.out.println("\tRollback (undo) results in the value now equaling: "+get(uri,keyname));
        }
        System.out.println("After 3 iterations of rollback (flip/flop/undo) for the value it now equals: "+get(uri,keyname));
        try (UnifiedJedis jedis = new UnifiedJedis(uri)) {
            String casKey = "caskey";
            jedis.del(casKey);
            System.out.println("\nTesting Check And Set behavior with new key named: "+casKey+" and versionID of 0");
            long versionID = 0;
            long newVersionID = checkAndSet(uri, casKey, "" + System.nanoTime(), versionID);
            System.out.println("\tAfter update --> VersionID of "+casKey+" == "+newVersionID+ " value of "+casKey+" = "+get(uri,casKey));
            if ((newVersionID != versionID + 1)) {
                System.out.println("\t[[[[[[{ ALERT }]]]]]]\n\tValue not updated! You need to refresh your local copy - current versionID is: " + newVersionID);
            }

            versionID = newVersionID;
            System.out.println("Testing Check And Set behavior with wrong/old versionID of "+(versionID-1));
            newVersionID = checkAndSet(uri, casKey, "" + System.nanoTime(), (versionID-1));
            System.out.println("\tAfter update --> VersionID of "+casKey+" == "+newVersionID+ " value of "+casKey+" = "+get(uri,casKey));
            if (newVersionID != versionID + 1)  {
                System.out.println("\t[[[[[[{ ALERT }]]]]]]\n\tValue not updated! You need to refresh your local copy - current versionID is: " + newVersionID);
            }

            System.out.println("Testing Check And Set behavior with versionID of "+newVersionID);
            versionID = newVersionID;
            newVersionID = checkAndSet(uri, casKey, "" + System.nanoTime(), versionID);
            System.out.println("\tAfter update --> VersionID of "+casKey+" == "+newVersionID+ " value of "+casKey+" = "+get(uri,casKey));
            if (newVersionID != versionID + 1)  {
                System.out.println("\t[[[[[[{ ALERT }]]]]]]\n\tValue not updated! You need to refresh your local copy - current versionID is: " + newVersionID);
            }
        }

    }

    // Hash stores: oldval, desiredval, timestamp
    private static String get(URI uri,String keyname) {
        String result = "";
        try (UnifiedJedis jedis = new UnifiedJedis(uri)) {
            result = jedis.hget(keyname,"val");
        }
        return result;
    }

    // Hash stores: oldval, val, timestamp
    private static long set(URI uri, String keyname, String value) {
        long duration = 0l;
        try (UnifiedJedis jedis = new UnifiedJedis(uri)) {
            // create the LUA script string for our use:
            String luaString = "local changeTime = redis.call('TIME') local putold ='nil' if redis.call('HEXISTS',KEYS[1],'val') == 1 then putold = redis.call('HGET', KEYS[1], 'val') end redis.call('HSET',KEYS[1],'oldval',putold,'val',ARGV[1],'timestamp',changeTime[1]..':'..changeTime[2])";
            // create the List of keynames to pass along with the lua script:
            ArrayList<String> keynamesArray = new ArrayList<>();
            keynamesArray.add(keyname);
            ArrayList<String> values = new ArrayList<String>();
            values.add(value);
            long startTime = System.currentTimeMillis();
            // fire the lua script that adds the default value and stored any old value in the rollback attribute:
            jedis.eval(luaString,keynamesArray,values);
            duration = System.currentTimeMillis()-startTime;
        }
        return duration;
    }

    // Hash stores: oldval, val, timestamp
    private static long checkAndSet(URI uri, String keyname, String value,long versionID) {
        long newVersionID = 0l;
        try (UnifiedJedis jedis = new UnifiedJedis(uri)) {
            // create the LUA script string for our use:
            String luaString = "local changeTime = redis.call('TIME') local workingVersionID = -1 if ARGV[2] == ''..0 then workingVersionID = 0 end local putold ='nil' if redis.call('HEXISTS',KEYS[1],'val') == 1 then putold = redis.call('HGET', KEYS[1], 'val') workingVersionID = redis.call('HGET',KEYS[1],'versionID') end if workingVersionID..'' == ''..ARGV[2] then workingVersionID = (ARGV[2]+1) redis.call('HSET',KEYS[1],'oldval',putold,'val',ARGV[1],'timestamp',changeTime[1]..':'..changeTime[2],'versionID',workingVersionID) end if workingVersionID..'' == ''..ARGV[2] then return -1 end return workingVersionID";
            // create the List of keynames to pass along with the lua script:
            ArrayList<String> keynamesArray = new ArrayList<>();
            keynamesArray.add(keyname);
            ArrayList<String> values = new ArrayList<String>();
            values.add(value);
            values.add(""+versionID);
            // fire the lua script that adds the default value and stored any old value in the rollback attribute and retrieve the VersionID:
            String s ="";
            try{
                s = String.valueOf(jedis.eval(luaString,keynamesArray,values));
                newVersionID = Long.parseLong(s);
            }catch(Throwable t){
                System.out.println("ERROR returned value from LUA ="+t.getMessage());
                newVersionID=-1l;
            }
        }
        return newVersionID;
    }

    // Hash stores: oldval, desiredval, timestamp
    // Rollback only allows for one historical (old) value (this value is flopped with the desiredVal)
    // if oldval == 2 and desiredVal == 3 -- calling rollback will set oldval to 3 and desiredVal to 2
    private static void rollback(URI uri,String keyname) {
        try (UnifiedJedis jedis = new UnifiedJedis(uri)) {
            // create the LUA script string for our use:
            String luaString = "local changeTime = redis.call('TIME') local putdesired = redis.call('HGET', KEYS[1], 'oldval') local putold = redis.call('HGET', KEYS[1], 'val') redis.call('HMSET',KEYS[1],'oldval',putold,'val',putdesired,'timestamp',changeTime[1]..':'..changeTime[2])";
            // create the List of keynames to pass along with the lua script:
            ArrayList<String> keynames = new ArrayList<>();
            keynames.add(keyname);
            ArrayList<String> values = new ArrayList<>();
            // fire the lua script that returns the rollback attribute:
            jedis.eval(luaString, keynames,values);
        }
    }
}
