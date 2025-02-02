package client;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.Socket;
import java.util.Arrays;

import conn.Frame;
import conn.TaggedConnection;


/* 
 * Client - Will handle the inputs from the user, 
 *          sending commands and receiveing their output from the server 
 */
public class Client { 

    private static final String SERVER_ADDRESS = "127.0.0.1"; // Server address
    private static final int SERVER_PORT = 12345; // Server port
    private Socket socket;
    private int tag = 0;

    public Client() {
        try {
            this.socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
            System.out.println("Connection with server estabilished...");

        } catch (IOException e) {
            System.err.println("Error initializing client: " + e.getMessage());
        }
    }

    public void start() {
        try (BufferedReader systemIn = new BufferedReader(new InputStreamReader(System.in));
             TaggedConnection conn = new TaggedConnection(socket);
             Demultiplexer m = new Demultiplexer(conn)) {

            // ----------- Register and Login ------------------

            System.out.println("=== Data Manager ===");

            
            // Prompt for register or login option
            while (true) {

                int option = 0;
                
                System.out.println("Choose an option, write 1 or 2.");  // Register/Login prompt
                System.out.println("1. Register");
                System.out.println("2. Login");
                System.out.println("Your Option:");

                String regOrLogOption = systemIn.readLine();
                if (regOrLogOption == null) {
                    break;
                }
                System.out.println();


                if (regOrLogOption.equals("1")) { // Register
                    System.out.println("= Create New Account =");
                    option = 1;
    
                } else if (regOrLogOption.equals("2")) { // Login
                    System.out.println("= Login =");
                    option = 2;

                } else { // Invalid option
                    System.out.println("Invalid option! Please enter 1 or 2.");
                    System.out.println();
                    continue;
                }

                // Prompt for username
                System.out.println("Username: ");
                String username = systemIn.readLine();
                if (username == null) break;
                else if (username.isEmpty()) {
                    System.out.println("Username required!");
                    continue;
                }

                // Prompt for password
                System.out.println("Password: ");
                String password = systemIn.readLine();
                if (password == null) break;
                else if (password.isEmpty()) {
                    System.out.println("Password required!");
                    continue;
                }

                // Send option and credentials as a single frame
                conn.send(option, (username + ":" + password).getBytes());

                if (option==2) System.out.println("Waiting for server to authenticate user...");

                Frame regLogFrame = conn.receive(); // Receive server response
                
                // ------------ Create new account ----------------
                if (option == 1) {
                    String response = new String(regLogFrame.data);
                    System.out.println(response);
                    System.out.println();
                }

                // ------------- Log in --------------
                if (option == 2) {
                    
                    if (regLogFrame.data.length > 0 && regLogFrame.data[0] == 1) { // If authenticated start data management

                        // Start demultiplexer to send commands
                        m.start();

                        System.out.println("Logged in successfully!");
                        System.out.println();
                        

                        String userInput;

                        System.out.println("Enter a command ('help' to list commands):");

                        while (true) {

                            System.out.println();

                            userInput = systemIn.readLine(); // Read command
                            if (userInput == null) break;
                            

                            if (userInput.equals("help")) { // Help commands - list all commands
                                String helpMessage = "[INFO] List of commands:\n" +
                                                    "[INFO] - help: List all commands.\n" +
                                                    "[INFO] - put <key> <value>: Adds or updates a single key-value pair in the server.\n" +
                                                    "[INFO] - get <key>: Retrieves the value associated with the given key, or returns null if the key does not exist.\n" +
                                                    "[INFO] - multiput <n> <key> <value>...: Adds or updates n key-value pairs in the server.\n" +
                                                    "[INFO] - multiget <n> <key>...: Retrieves n values for the specified keys and returns them as a map.\n" +
                                                    "[INFO] - end: End program\n";

                                System.out.println(helpMessage);
                                continue;
                            }

                            // Parse
                            String[] tokens = userInput.split(" ");
                            if (tokens.length == 0 || tokens[0].isEmpty()) {
                                System.out.println("Invalid command!");
                                System.out.println();
                                continue;
                            }

                            String command = tokens[0].toLowerCase();

                            String[] rest = Arrays.copyOfRange(tokens, 1, tokens.length);

                            // ---- Command Serializable ----

                            // END CLIENT
                            if (command.equals("end")) { 
                                m.send(0, "".getBytes());
                                break;
                            }
                            else { // Send Commands
                                Thread commandThread;
                                this.tag++; 
                                commandThread = new Thread(new CommandHandler(m, tag, command, rest));
                                // Start the command thread
                                commandThread.start();

                            }
                        }
                        break;
                    } else if (regLogFrame.data.length > 0 && regLogFrame.data[0] == 2) {
                        System.out.println("There's another client already logged in this account!");
                    }
                    else {
                        System.out.println("Invalid credentials!");
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        finally {
            // close connection
            shutdown();
        }
    }


    private void shutdown() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            System.out.println("Connection closed.");
        } catch (IOException e) {
            System.err.println("Error closing server: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        Client client = new Client();
        client.start();
    }
}
