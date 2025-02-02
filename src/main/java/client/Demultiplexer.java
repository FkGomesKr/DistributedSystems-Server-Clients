package client;

import java.util.*;
import java.io.IOException;
import java.util.concurrent.locks.*;

import conn.*;

public class Demultiplexer implements AutoCloseable {
    private TaggedConnection conn; // Conn handler with frames
    private Lock l = new ReentrantLock(); 
    private Map<Integer,Entry> map = new HashMap<>(); // Maps Tag-Entry 
    private IOException ex = null; // Stores IOException

    // class to hold bytes for a specific tag while not needed
    private class Entry {
        Condition cond = l.newCondition(); // Condition variable for waiting and notification 
        Deque<byte[]> queue = new ArrayDeque<>(); // Store received data for this tag
        int waiters = 0; // Number of threads waiting for messages on this tag
    }

    // Retrieves entry or creates a new one
    private Entry get(int tag) {
        Entry e = map.get(tag);
        if (e == null) {
            e = new Entry();
            map.put(tag,e);
        }
        return e;
    }

    public Demultiplexer(TaggedConnection conn) {
        this.conn = conn;
    }

    // Starts a background thread to handle frames received from the TaggedConnection
    public void start() {
        new Thread(() -> {
            try {
            for(;;) {
                Frame f = conn.receive(); // Receive the frame
                l.lock(); // needed to modify shared resources
                try {
                    // Get the entry and add data received to entry
                    Entry e = this.get(f.tag); 
                    e.queue.add(f.data);
                    e.cond.signal(); // Notify one thread waiting that is no more empty
                } finally {
                    l.unlock();
                }
            }
            } catch (IOException ex) { // handle error
                l.lock();
                try {
                    this.ex = ex;
                    for (Entry es : map.values()) {
                        es.cond.signalAll(); // Notify all threads waiting for any tag that occurred an exception
                    }
                } finally {
                    l.unlock();
                }
            }
        }).start();
    }

    // Sends a frame using conn
    public void send (Frame frame) throws IOException {
        conn.send(frame);
    }

    // Sends a tag and data using conn
    public void send(int tag, byte[] data) throws IOException {
        conn.send(tag,data);
    }

    // Receives a message for the specified tag
    public byte[] receive(int tag) throws IOException, InterruptedException {
        l.lock();
        try {
            Entry e = this.get(tag);

            e.waiters++;
            while(e.queue.isEmpty() && this.ex == null) {
                e.cond.await(); // Wait until data is available or an exception occurs
            }
            e.waiters--;

            byte[] b = e.queue.poll();
            
            // If no threads are waiting and the queue is empty, remove entry
            if (e.waiters == 0 && e.queue.isEmpty()) { 
                map.remove(tag);
            }
            if (b != null) return b;
            else {
                throw this.ex;
            }
        } finally {
            l.unlock();
        }
    }

    // Closes tagged conn
    public void close() throws IOException {
        conn.close();
    }
}