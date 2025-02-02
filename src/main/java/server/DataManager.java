package server;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class DataManager {
    private Map<String, byte[]> dataMap = new HashMap<>(); // Map <keyname,data>
    private Lock l_manager = new ReentrantLock();
    private Map<String,Condition> condMap = new HashMap<>(); // Map of conditions to each key

    // Single Write 
    public boolean put(String key, byte[] value) {
        l_manager.lock();
        try {
            if (value.length==0) return false;
            this.dataMap.put(key, value);
            
            if (this.condMap.containsKey(key)) {
                Condition cond = this.condMap.get(key);
                cond.signalAll(); // Notify all waiting threads of this key 
            }
            else {
                // if doesn't exist create a new entry on map with a new condition
                Condition cond = l_manager.newCondition();
                this.condMap.put(key, cond);
            }

            return true;
        } finally {
            l_manager.unlock();
        }
    }

    // Single Read
    public byte[] get(String key) {
        l_manager.lock();
        try {

            return this.dataMap.getOrDefault(key, null);
        } finally {
            l_manager.unlock();
        }
    }

    // Multi Write
    public void multiPut(Map<String, byte[]> mapValues) {
        l_manager.lock();
        try {
            for (Map.Entry<String, byte[]> e : mapValues.entrySet()) {
                String key = e.getKey();
                this.dataMap.put(key, e.getValue());

                if (this.condMap.containsKey(key)) {
                    Condition cond = this.condMap.get(key);
                    cond.signalAll(); // Notify all waiting threads of this key 
                }
                else {
                    // if doesn't exist creates a new entry on conditions map with a new condition
                    Condition cond = l_manager.newCondition();
                    this.condMap.put(key, cond);
                }
            }
            
        } finally {
            l_manager.unlock();
        }
    }

    // Multi Read
    public Map<String, byte[]> multiGet(Set<String> keys) {
        l_manager.lock();
        try {
            Map<String, byte[]> res = new HashMap<>();
            for (String key : keys) {
                if (this.dataMap.containsKey(key)) {
                    res.put(key, this.dataMap.get(key));
                }
            }
            return res;
        } finally {
            l_manager.unlock();
        }
    }

    // Conditional get
    public byte[] getWhen(String key, String keyCond, byte[] valueCond) throws InterruptedException{
        l_manager.lock();

        try {

            // Create or retrieve the condition associated with `keyCond`
            Condition cond = condMap.computeIfAbsent(keyCond, k -> l_manager.newCondition());

            // Check the condition of `keyCond` (value on map equals to 'valueCond') and wait if necessary
            while (!Arrays.equals(dataMap.get(keyCond), valueCond)) {
                cond.await(); // Wait until the condition is satisfied
            }

            // Return value of 'key'
            return this.dataMap.getOrDefault(key, null);            
        } finally {
            l_manager.unlock();
        }
    }
}
