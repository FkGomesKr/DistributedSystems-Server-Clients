package conn;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.*;

class TaggedConnectionTest {

    @Test
    void testSendAndReceive() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            int port = serverSocket.getLocalPort();

            Thread serverThread = new Thread(() -> {
                try (Socket socket = serverSocket.accept();
                     TaggedConnection conn = new TaggedConnection(socket)) {
                    Frame frame = conn.receive();
                    conn.send(frame.tag, frame.data);
                } catch (IOException ignored) {
                }
            });
            serverThread.start();

            try (Socket clientSocket = new Socket("localhost", port);
                 TaggedConnection conn = new TaggedConnection(clientSocket)) {
                conn.send(1, "test".getBytes());
                Frame response = conn.receive();
                assertEquals(1, response.tag);
                assertEquals("test", new String(response.data));
            }
        }
    }
}
