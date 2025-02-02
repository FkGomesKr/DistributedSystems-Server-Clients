package server;

import java.io.IOException;
import java.io.EOFException;
import java.net.Socket;
import java.util.concurrent.ThreadPoolExecutor;

import conn.*;


class ClientHandler implements Runnable {
    private final Socket socket;
    private UserManager users;
    private DataManager data;
    private TaggedConnection conn;
    private String client_username;
    private ThreadPoolExecutor sharedCommandThreadPool;


    public ClientHandler(Socket socket, UserManager users, DataManager data, TaggedConnection conn, ThreadPoolExecutor commandThreadPool) {
        this.socket = socket;
        this.users = users;
        this.data = data;
        this.conn = conn;
        this.sharedCommandThreadPool = commandThreadPool;
    }

    private String getClient_username() {
        return client_username;
    }

    private void setClient_username(String client_username) {
        this.client_username = client_username;
    }

    @Override
    public void run() {
        boolean authenticated = false;
        try {
    
    
            int option = 0;
    
            // Check if client wants to register or login
            while (true) {

                Frame regLogFrame = this.conn.receive();

                option = regLogFrame.tag;

                // Parse username and password from received data
                String credentials = new String(regLogFrame.data);
                
                String[] parts = credentials.split(":");
                if (parts.length != 2) {
                    this.conn.send(option, "Invalid credentials format.".getBytes());
                    continue;
                }

                String username = parts[0];
                String pass = parts[1];

                if (option == 1) {  // ----------- Register
                    if (users.register(username, pass)) {
                        this.conn.send(option, ("New account created with username: " + username).getBytes());
                    } else {
                        this.conn.send(option, ("'" + username + "' already exists").getBytes());
                    }
                } else if (option == 2) {  // ------------- Login

                    int authentication_result = users.authenticate(username,pass);
                    if (authentication_result == 1) { // Successful login
                        conn.send(option, new byte[]{1});

                        setClient_username(username);
                        authenticated = true;
                        System.out.println(username + " authenticated!");
                        
                        // -------- Processing commands
                        while (true) {
                            Frame commandFrame = this.conn.receive();
                            if (commandFrame == null || commandFrame.tag == 0) break; // tag == 0 implies end command from client

                            // create CommandExecutor to handle the command - thread per command structure
                            sharedCommandThreadPool.submit(new CommandExecutor(commandFrame, client_username, data, conn));
                        }
                        break;
                    } else if (authentication_result == 0) { // Login - Invalid credentials
                        conn.send(option, new byte[]{0});
                    } else { // Login - User is already logged by another client
                        conn.send(option, new byte[]{2});
                    }

                }
            }
        } catch (EOFException e) {
            System.out.println("Client disconnected unexpectedly (EOF): " + e.getMessage());
        } catch (IOException e) {
            System.out.println("Client disconnected unexpectedly (IO): " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Client disconnected unexpectedly (Exception): " + e.getMessage());
        }
         finally {
            try {
                // Ensures user is logged out only if authenticated
                if (authenticated) {

                    users.logOut(getClient_username());
                    System.out.println(getClient_username()+" logged out. Number of active sessions: " + users.getActiveSessions());
                }
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}    
