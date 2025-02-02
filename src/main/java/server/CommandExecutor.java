package server;

import conn.Frame;
import conn.TaggedConnection;

import java.io.IOException;
import java.util.*;

public class CommandExecutor implements Runnable {
    private int tag;
    private String command;
    private final DataManager data;
    private final TaggedConnection conn;
    private String[] commandArguments;
    private final Frame commandFrame;
    private final String client_username;

    public CommandExecutor(Frame commandFrame, String client_username, DataManager data, TaggedConnection conn) {
        this.commandFrame = commandFrame;
        this.client_username = client_username;
        this.data = data;
        this.conn = conn;
    }

    private void setCommand(String command) {
        this.command = command;
    }

    private void setTag(int tag) {
        this.tag = tag;
    }

    private void setCommandArguments(String[] commandArguments) {
        this.commandArguments = commandArguments;
    }

    @Override
    public void run() {
        setTag(this.commandFrame.tag);

        String[] commandTokens = new String(this.commandFrame.data).split(" ");
        setCommand(commandTokens[0]);
        setCommandArguments(Arrays.copyOfRange(commandTokens, 1, commandTokens.length));

        try {
            if (this.command.equals("put")) {
                this.handlePut(this.tag, this.commandArguments);
                //System.out.println("vou fazer o comando put para o " + this.client_username);
            } else if (this.command.equals("get")) {
                this.handleGet(this.tag, this.commandArguments);
                //System.out.println("vou fazer o comando get para o " + this.client_username);
            } else if (this.command.equals("multiget")) {
                //System.out.println("vou fazer o comando multiGet para o " + this.client_username);
                this.handleMultiGet(this.tag, this.commandArguments);
            } else if (this.command.equals("multiput")) {
                //System.out.println("vou fazer o comando multiPut para o " + this.client_username);
                this.handleMultiPut(this.tag, this.commandArguments);
            } else if (this.command.equals("getwhen")) {
                //System.out.println("vou fazer o comando getWhen para o " + this.client_username);
                this.handleGetWhen(this.tag, this.commandArguments);
            } else {
                this.conn.send(tag, ("Unsupported command: " + this.command).getBytes());
            }
        } catch (IOException e) {
            System.err.println("Error handling command (" + this.tag + ") from user "+ this.client_username +": " + e.getMessage());
            try {
                this.conn.send(tag, ("Error handling command (" + this.tag + ")").getBytes());
            } catch (IOException ex) {
                System.err.println("Failed to notify client of error: " + ex.getMessage());
            }
        }
    }

    private void handlePut(int tag, String[] commandTokens) throws IOException {
        if (commandTokens.length != 2) {
            this.conn.send(tag, "Invalid number of arguments for 'put'. Requires key and value.".getBytes());
            return;
        }

        String key = commandTokens[0];
        byte[] value = commandTokens[1].getBytes();

        if (this.data.put(key, value)) {
            this.conn.send(tag, ("Key '" + key + "' updated successfully.").getBytes());
        }
        else {
            this.conn.send(tag, "Value cannot be empty.".getBytes());
        }
    }

    private void handleGet(int tag, String[] commandTokens) throws IOException {
        if (commandTokens.length != 1) {
            this.conn.send(tag, "Invalid number of arguments for 'get'. Requires key.".getBytes());
            return;
        }

        String key = commandTokens[0];
        byte[] value = this.data.get(key);

        if (value != null) {
            this.conn.send(tag, value);
        } else {
            this.conn.send(tag, "".getBytes());
        }
    }

    private void handleMultiGet(int tag, String[] commandTokens) throws IOException {
        if (commandTokens.length < 2) {
            this.conn.send(tag, "Invalid arguments! Requires at least one key.".getBytes());
            return;
        }

        int n;
        try {
            n = Integer.parseInt(commandTokens[0]);
        } catch (NumberFormatException ex) {
            this.conn.send(tag, "Invalid number of keys specified.".getBytes());
            return;
        }

        if (commandTokens.length != 1 + n) {
            this.conn.send(tag, ("Invalid arguments! Command 'multiGet' requires " + n + " keys.").getBytes());
            return;
        }

        Set<String> keys = new HashSet<>(Arrays.asList(commandTokens).subList(1, commandTokens.length));
        Map<String, byte[]> results = this.data.multiGet(keys);

        for (String key : keys) {
            byte[] value = results.getOrDefault(key, null);
            if (value == null) {
                this.conn.send(tag, key.getBytes()); // return just key if no value found
            } else {
                this.conn.send(tag, (key + " " + new String(value)).getBytes()); // return key and value if found
            }
        }
    }


    private void handleMultiPut(int tag, String[] commandTokens) throws IOException {
        if (commandTokens.length < 2) {
            conn.send(tag, "Invalid arguments! Requires at least one key-value pair.".getBytes());
            return;
        }

        int n;
        try {
            n = Integer.parseInt(commandTokens[0]);
        } catch (NumberFormatException ex) {
            this.conn.send(tag, "Invalid number of key-value pairs specified.".getBytes());
            return;
        }

        if (commandTokens.length != 1 + (2 * n)) {
            this.conn.send(tag, ("Invalid arguments! Command 'multiPut' requires " + n + " key-value pairs.").getBytes());
            return;
        }

        Map<String, byte[]> mapValues = new HashMap<>();
        for (int i = 1; i < commandTokens.length; i += 2) {
            String key = commandTokens[i];
            String value = commandTokens[i + 1];
            mapValues.put(key, value.getBytes());
        }

        this.data.multiPut(mapValues);
        this.conn.send(tag, "All keys updated successfully.".getBytes());
    }

    private void handleGetWhen(int tag, String[] commandTokens) {
        try {
            if (commandTokens.length != 3) {
                this.conn.send(tag, "Invalid number of arguments for 'getWhen'. Requires key keyCond valueCond.".getBytes());
                return;
            }

            String key = commandTokens[0];
            String keyCond = commandTokens[1];
            String valueCond = commandTokens[2];

            // Use getWhen to wait for the condition to be met
            byte[] value = this.data.getWhen(key, keyCond, valueCond.getBytes());

            if (value != null) {
                this.conn.send(tag, value);
            } else {
                this.conn.send(tag, "".getBytes());
            }
        } catch (Exception e) {
            // Catch-all for unexpected exceptions
            System.err.println("Unexpected error while handling 'getWhen' for client: " + this.client_username + " - " + e.getMessage());
            e.printStackTrace(); // Log the full stack trace for debugging
            try {
                this.conn.send(tag, "Unexpected error occurred while processing 'getWhen'.".getBytes());
            } catch (IOException ex) {
                System.err.println("Failed to notify client of unexpected error: " + ex.getMessage());
            }
        }
    }

}
