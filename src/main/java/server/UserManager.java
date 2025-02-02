package server;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;


public class UserManager {
    private Map<String, String> users = new HashMap<>();  // Store registered users with username and password
    private Map<String, String> loggedUsers = new HashMap<>(); // Store the users that are already logged in

    private Lock lock = new ReentrantLock();  
    private Queue<Condition> waitQueue = new LinkedList<>();

    private int activeSessions = 0;
    private int maxSessions;
    

    public UserManager(int maxSessions) {
        this.maxSessions=maxSessions;
    }

    // Get number of users logged in
    public int getActiveSessions() {
        lock.lock();
        try {
            return this.activeSessions;
        } finally {
            lock.unlock();
        }
    }

    // Registers a new user
    public boolean register(String username, String password) {
        lock.lock();
        try {
            if (users.containsKey(username)) {
                return false;  
            }
            users.put(username, password);
            return true;
        } finally {
            lock.unlock();
        }
    }


    // Authenticates an existing user
    public int authenticate(String username, String password) throws InterruptedException{
        lock.lock();
        try {
            if (loggedUsers.containsKey(username)) { // If the user in question is already logged by another client
                return 2;
            }
            if (users.containsKey(username) && users.get(username).equals(password)) {
                
                Condition c = lock.newCondition();
                waitQueue.add(c); // Waiting Queue - Stop race conditions

                while (waitQueue.peek() != c || activeSessions >= maxSessions) { // Wait till a user log out and its his turn
                    c.await();
                }
                
                // Remove condition from waiting queue
                waitQueue.poll();

                this.loggedUsers.put(username, password);
                this.activeSessions++; // New user logged
                return 1;

            }
            return 0;
        } finally {
            lock.unlock();
        }
    }

    // Log Out user
    public void logOut(String username) {
        lock.lock();
        try {
            this.loggedUsers.remove(username);
            this.activeSessions--;
            // Signal next thread that there is a free spot
            if (!waitQueue.isEmpty()) {
                waitQueue.peek().signal(); 
            } 
        } finally {
            lock.unlock();
        }

    }
}
