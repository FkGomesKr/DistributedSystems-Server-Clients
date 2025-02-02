package conn;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class TaggedConnection implements AutoCloseable {

    private final DataOutputStream dataOut;
    private final DataInputStream dataIn;
    private final Socket socket;
    private final Lock writeLock = new ReentrantLock();
    private final Lock readLock = new ReentrantLock();

    public TaggedConnection(Socket socket) throws IOException {
        this.socket = socket;
        this.dataOut = new DataOutputStream(this.socket.getOutputStream());
        this.dataIn = new DataInputStream(this.socket.getInputStream());
    }

    public void send(Frame frame) throws IOException {
        send(frame.tag, frame.data);
    }

    public void send(int tag, byte[] data) throws IOException {
        writeLock.lock();
        try {
            dataOut.writeInt(tag); 
            dataOut.writeInt(data.length); 
            dataOut.write(data); 
            dataOut.flush(); 
        } finally {
            writeLock.unlock();
        }
    }

    public Frame receive() throws IOException, EOFException {
        readLock.lock();
        try {
            int tag = dataIn.readInt();
            int length = dataIn.readInt(); 
            byte[] data = new byte[length];
            dataIn.readFully(data);
            return new Frame(tag, data);
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public void close() throws IOException {
        try {
            socket.close();
        } catch (IOException e) {
            throw new IOException("Failed to close the connection: " + e.getMessage(), e);
        }
    }
}
