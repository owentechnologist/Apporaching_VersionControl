package com.redislabs.sa.ot.apv;
import org.w3c.dom.ranges.Range;
import redis.clients.jedis.*;
import redis.clients.jedis.json.Path;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
        String keyname = "";
        String keynameHash = "hashtest{1}";
        String keynameJSON = "jsontest{1}";
        boolean doCompare = false;
        boolean useJSON = false;
        int compareSize = 10; //not used unless user sets a new value

        if(args.length>0){
            ArrayList<String> argList = new ArrayList<>(Arrays.asList(args));
            if(argList.contains("--host")){
                int argIndex = argList.indexOf("--host");
                host = argList.get(argIndex+1);
            }
            if(argList.contains("--port")){
                int argIndex = argList.indexOf("--port");
                port = Integer.parseInt(argList.get(argIndex+1));
            }
            if(argList.contains("--username")){
                int argIndex = argList.indexOf("--username");
                username = argList.get(argIndex+1);
            }
            if(argList.contains("--password")){
                int argIndex = argList.indexOf("--password");
                password = argList.get(argIndex + 1);
            }
            if(argList.contains("--keynamehash")){
                int argIndex = argList.indexOf("--keynamehash");
                keynameHash = argList.get(argIndex+1);
            }
            if(argList.contains("--keynamejson")){
                int argIndex = argList.indexOf("--keynamejson");
                keynameJSON = argList.get(argIndex+1);
            }
            if(argList.contains("--docompare")){
                int argIndex = argList.indexOf("--docompare");
                doCompare = Boolean.parseBoolean(argList.get(argIndex+1));
            }
            if(argList.contains("--comparesize")){
                int argIndex = argList.indexOf("--comparesize");
                compareSize = Integer.parseInt(argList.get(argIndex+1));
            }
            if(argList.contains("--usejson")){
                int argIndex = argList.indexOf("--usejson");
                useJSON = Boolean.parseBoolean(argList.get(argIndex+1));
            }
        }
        if(useJSON){keyname=keynameJSON;}else{keyname=keynameHash;}
        if("".equalsIgnoreCase(password)) {
            try (JedisPooled jedisPooled = new JedisPooled(host, port)) {
                testWithJedisPooled(jedisPooled, keyname, useJSON);
                if (doCompare) {
                    compareNumbersWithJedisPooled(jedisPooled, compareSize, useJSON);
                }
            }
            }else{
                try (JedisPooled jedisPooled = new JedisPooled(host, port, username, password)) {
                    testWithJedisPooled(jedisPooled, keyname, useJSON);
                    if (doCompare) {
                        compareNumbersWithJedisPooled(jedisPooled, compareSize, useJSON);
                    }
                }
            }
        }

    // Hash or JSON stores: oldval, desiredval, timestamp
    private static String getWithJedisPooled(JedisPooled jedisPooled,String keyname,boolean useJSON) {
        String result = "";
        if(useJSON){
            //System.out.println("\ngetWithJedisPooled() fetches : "+jedisPooled.jsonResp(keyname, Path.of("$.val")));
            List<Object> lResult = jedisPooled.jsonResp(keyname, Path.of("$.val"));
            if(null == lResult){
                System.out.println("Path == $.val on json object "+keyname+" is null");
                System.out.println("Exists: "+keyname+" returns: "+jedisPooled.exists(keyname));
            }else {
                result = lResult.get(0).toString();
                System.out.println("\ngetWithJedisPooled() fetches : "+result);
            }
        }else {
            result = jedisPooled.hget(keyname, "val");
        }
        return result;
    }

    // Hash or JSON stores: oldval, val, timestamp

    /**
     *  This code updates the specified key in Redis by setting the $.oldval field to the previous value
     *  (if it exists),
     *  the $.val field to the value specified in ARGV[1],
     *  and the $.timestamp field to the current time.
     *  It does something similar when using the hash datatype instead of JSON (using keynames not JSON paths)
     * @param jedisPooled
     * @param keyname
     * @param value
     * @param useJSON
     * @return
     */
    private static long setWithJedisPooled(JedisPooled jedisPooled, String keyname, String value,boolean useJSON) {
        long duration = 0l;
        String luaString = "";
        // create the LUA script string for our use:
        if(useJSON) { // using JSON data structure to contain/express the CAS target:
            luaString = "local changeTime = redis.call('TIME') local putold = redis.call('JSON.RESP', KEYS[1], '$.val') if not putold then redis.call('JSON.SET',KEYS[1],'$','{\"val\": '..ARGV[1]..'}') else redis.call('JSON.SET',KEYS[1],'$.oldval',putold[1]) redis.call('JSON.SET',KEYS[1],'$.val',ARGV[1]) end redis.call('JSON.SET',KEYS[1],'$.timestamp',changeTime[1]..':'..changeTime[2])";
        }else{ // using a hash to contain/express the CAS target:
            luaString = "local changeTime = redis.call('TIME') local putold ='nil' if redis.call('HEXISTS',KEYS[1],'val') == 1 then putold = redis.call('HGET', KEYS[1], 'val') end redis.call('HSET',KEYS[1],'oldval',putold,'val',ARGV[1],'timestamp',changeTime[1]..':'..changeTime[2])";
        }
        // create the List of keynames to pass along with the lua script:
        ArrayList<String> keynamesArray = new ArrayList<>();
        keynamesArray.add(keyname);
        ArrayList<String> values = new ArrayList<String>(); // we only have one value in this example
        try{
            Double.parseDouble(value);
        }catch(NumberFormatException nfe){ value = "\""+value+"\"";}
        values.add(value);
        long startTime = System.currentTimeMillis();
        // fire the lua script that adds the default value and stored any old value in the rollback attribute:
        jedisPooled.eval(luaString,keynamesArray,values);
        duration = System.currentTimeMillis()-startTime;
        return duration;
    }

    // Hash stores: oldval, val, timestamp
    private static long checkAndSetWithJedisPooled(JedisPooled jedisPooled, String keyname, String value,long versionID,boolean useJSON) {
        long newVersionID = 0l;
        // create the LUA script string for our use:
        String luaString = "";
        if(useJSON) {
            luaString = "local changeTime = redis.call('TIME') local putold = redis.call('JSON.RESP', KEYS[1], '$.val') local workingVersionID = redis.call('JSON.RESP', KEYS[1], '$.versionID') if not workingVersionID then workingVersionID = 1 else if workingVersionID[1]..'' == ARGV[2]..'' then workingVersionID = ARGV[2] + 1 else return workingVersionID end end if redis.call('EXISTS', KEYS[1]) == 0 then redis.call('JSON.SET', KEYS[1], '$', '{\"val\": ' .. ARGV[1] .. '}') end if putold then redis.call('JSON.SET', KEYS[1], '$.oldval', putold[1]) end redis.call('JSON.SET', KEYS[1], '$.timestamp', changeTime[1] .. ':' .. changeTime[2]) redis.call('JSON.SET', KEYS[1], '$.versionID', workingVersionID) if workingVersionID == ARGV[2] then workingVersionID = -1 else redis.call('JSON.SET', KEYS[1], '$.val', ARGV[1]) end return workingVersionID";
        }else {
            luaString = "local changeTime = redis.call('TIME') local workingVersionID = -1 if ARGV[2] == ''..0 then workingVersionID = 0 end local putold ='nil' if redis.call('HEXISTS',KEYS[1],'val') == 1 then putold = redis.call('HGET', KEYS[1], 'val') workingVersionID = redis.call('HGET',KEYS[1],'versionID') end if workingVersionID..'' == ''..ARGV[2] then workingVersionID = (ARGV[2]+1) redis.call('HSET',KEYS[1],'oldval',putold,'val',ARGV[1],'timestamp',changeTime[1]..':'..changeTime[2],'versionID',workingVersionID) end if workingVersionID..'' == ''..ARGV[2] then return -1 end return workingVersionID";
        }
        // create the List of keynames to pass along with the lua script:
        ArrayList<String> keynamesArray = new ArrayList<>();
        keynamesArray.add(keyname);
        ArrayList<String> values = new ArrayList<String>();
        values.add(value);
        values.add(""+versionID);
        // fire the lua script that adds the new value and stores any old value in the rollback attribute and retrieves the VersionID:
        String s ="";
        try{
            s = String.valueOf(jedisPooled.eval(luaString,keynamesArray,values));
            s = s.replace('[',' ');
            s = s.replace(']',' ');
            s = s.trim();
            System.out.println("\n\tResponse from CAS lua call: "+s);
            newVersionID = Long.parseLong(s);
        }catch(Throwable t){
            System.out.println("ERROR returned value from LUA ="+t.getMessage());
            newVersionID=-1l;
        }
        return newVersionID;
    }

    // Hash stores: oldval, desiredval, timestamp
    // Rollback only allows for one historical (old) value (this value is flopped with the desiredVal)
    // if oldval == 2 and desiredVal == 3 -- calling rollback will set oldval to 3 and desiredVal to 2
    private static void rollbackWithJedisPooled(JedisPooled jedisPooled,String keyname,boolean useJSON) {
        // create the LUA script string for our use:
        String luaString = "";
        if(useJSON) {
            luaString = "local changeTime = redis.call('TIME') local putdesired = redis.call('JSON.RESP', KEYS[1], '$.oldval') if not putdesired then return 'rollback not possible' else local putold = redis.call('JSON.RESP', KEYS[1], '$.val') redis.call('JSON.SET',KEYS[1],'$.oldval','\"'..putold[1]..'\"') redis.call('JSON.SET',KEYS[1],'$.val','\"'..putdesired[1]..'\"') redis.call('JSON.SET',KEYS[1],'$.timestamp',changeTime[1]..':'..changeTime[2]) end";
        }else{
            luaString = "local changeTime = redis.call('TIME') local putdesired = redis.call('HGET', KEYS[1], 'oldval') local putold = redis.call('HGET', KEYS[1], 'val') redis.call('HMSET',KEYS[1],'oldval',putold,'val',putdesired,'timestamp',changeTime[1]..':'..changeTime[2])";
        }
        // create the List of keynames to pass along with the lua script:
        ArrayList<String> keynames = new ArrayList<>();
        keynames.add(keyname);
        ArrayList<String> values = new ArrayList<>();
        // fire the lua script that rolls back the rollback attribute to its' previous state:
        jedisPooled.eval(luaString, keynames,values);
    }


    private static void compareNumbersWithJedisPooled(JedisPooled jedisPooled,int compareSize,boolean useJSON){
        System.out.println("\n\n> Comparing performance speed and memory usage between regular String and LUA-managed wrapper    \n");
        String simplekey = "simplekey";
        String rollbackKey = "rollbackKey";
        String payload = "0";
        jedisPooled.del(rollbackKey);
        jedisPooled.del(simplekey);
        long startTime = System.currentTimeMillis();
        jedisPooled.set(simplekey, payload);
        System.out.println("Writing a simple String with a value of '0' to Redis took " +
                (System.currentTimeMillis() - startTime) +
                " milliseconds (including network round trip)");
        System.out.println("Writing so that rollback is possible with a value of '0' to Redis took " +
                setWithJedisPooled(jedisPooled, rollbackKey, payload, useJSON) + " milliseconds (including network round trip)");
        System.out.println("The simple String takes up "+jedisPooled.memoryUsage(simplekey)+" bytes of memory");
        System.out.println("The rollback key takes up "+jedisPooled.memoryUsage(rollbackKey)+" bytes of memory");
        System.out.println("\n****** BIGGER VALUES FOR KEYS **********\n");
        // Make the payload (value for the keys) much larger:
        for(int x = 0;x<compareSize;x++){
            payload+="12345676890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ!@@#$%^&*()_+{},.<>/?";
            payload+=System.nanoTime(); // to ensure the value is different each time
        }
        startTime = System.currentTimeMillis();
        jedisPooled.set(simplekey, payload);
        System.out.println("Writing a simple String with a much bigger value size to Redis took " +
                (System.currentTimeMillis() - startTime) + " milliseconds (including network round trip)");
        System.out.println("Writing so that rollback is possible with a bigger value size to Redis took " +
                setWithJedisPooled(jedisPooled, rollbackKey, payload,useJSON) + " milliseconds (including network round trip)");
        //Call rollback so that the big value gets swapped into the backup slot:
        rollbackWithJedisPooled(jedisPooled,rollbackKey,useJSON);
        System.out.println("result of rollback is: "+getWithJedisPooled(jedisPooled,rollbackKey,useJSON));
        System.out.println("Writing again so that rollback is possible with a much bigger value size in Redis took " +
                setWithJedisPooled(jedisPooled, rollbackKey, payload, useJSON) + " milliseconds (including network round trip)");
        System.out.println("The simple String takes up "+jedisPooled.memoryUsage(simplekey)+" bytes of memory");
        System.out.println("The rollback key takes up "+jedisPooled.memoryUsage(rollbackKey)+" bytes of memory");
        System.out.println("result of rollback is: "+getWithJedisPooled(jedisPooled,rollbackKey,useJSON));
    }


    private static void testWithJedisPooled(JedisPooled jedisPooled, String keyname, boolean useJSON){
        System.out.println("Key called:"+keyname+" begins life with a value of 0");
        for(int x=0;x<10;x++) {
            setWithJedisPooled(jedisPooled, keyname, ""+x,useJSON);
        }
        System.out.println("After 10 iterations of updating the value it now equals: "+getWithJedisPooled(jedisPooled,keyname,useJSON));
        for(int y=0;y<3;y++){
            System.out.println("Calling Rollback [only 1 layer deep is implemented)");
            rollbackWithJedisPooled(jedisPooled,keyname,useJSON);
            System.out.println("\tRollback (undo) results in the value now equaling: "+getWithJedisPooled(jedisPooled,keyname,useJSON));
        }
        System.out.println("After 3 iterations of rollback (flip/flop/undo) for the value it now equals: "+getWithJedisPooled(jedisPooled,keyname,useJSON));
        String casKey = "caskey{1}";
        jedisPooled.del(casKey);
        System.out.println("\nTesting Check And Set behavior with new key named: "+casKey+" and versionID of 0");
        long versionID = 0;
        long newVersionID = checkAndSetWithJedisPooled(jedisPooled, casKey, "" + System.nanoTime(), versionID,useJSON);
        System.out.println("\tAfter update --> VersionID of "+casKey+" == "+newVersionID+ " value of "+casKey+" = "+getWithJedisPooled(jedisPooled,casKey,useJSON));
        if ((newVersionID != versionID + 1)) {
            System.out.println("\t[[[[[[{ ALERT }]]]]]]\n\tValue not updated! You need to refresh your local copy - current versionID is: " + newVersionID);
        }

        versionID = newVersionID;
        System.out.println("Testing Check And Set behavior with wrong/old versionID of "+(versionID-1));
        newVersionID = checkAndSetWithJedisPooled(jedisPooled, casKey, "" + System.nanoTime(), (versionID-1),useJSON);
        System.out.println("\tAfter update --> VersionID of "+casKey+" == "+newVersionID+ " value of "+casKey+" = "+getWithJedisPooled(jedisPooled,casKey,useJSON));
        if (newVersionID != versionID + 1)  {
            System.out.println("\t[[[[[[{ ALERT }]]]]]]\n\tValue not updated! You need to refresh your local copy - current versionID is: " + newVersionID);
        }

        System.out.println("Testing Check And Set behavior with versionID of "+newVersionID);
        versionID = newVersionID;
        newVersionID = checkAndSetWithJedisPooled(jedisPooled, casKey, "" + System.nanoTime(), versionID,useJSON);
        System.out.println("\tAfter update --> VersionID of "+casKey+" == "+newVersionID+ " value of "+casKey+" = "+getWithJedisPooled(jedisPooled,casKey,useJSON));
        if (newVersionID != versionID + 1)  {
            System.out.println("\t[[[[[[{ ALERT }]]]]]]\n\tValue not updated! You need to refresh your local copy - current versionID is: " + newVersionID);
        }
    }
}
