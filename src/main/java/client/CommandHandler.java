package client;

import java.io.IOException;

class CommandHandler implements Runnable {
    private final Demultiplexer m;
    private final int tag;
    private final String command;
    private final String[] arguments;

    public CommandHandler(Demultiplexer m, int tag, String command, String... arguments) {
        this.m = m;
        this.tag = tag;
        this.command = command;
        this.arguments = arguments;
    }

    @Override
    public void run() {
        try {
            if (this.command.equals("put")) {
                this.handlePut();
            } else if (this.command.equals("get")) {
                this.handleGet();
            } else if (this.command.equals("multiget")) {
                this.handleMultiGet();
            } else if (this.command.equals("multiput")) {
                this.handleMultiPut();
            } else if (this.command.equals("getwhen")) {
                this.handleGeTWhen();
            }
            else {
                System.err.println("Unsupported command: " + command);
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("Error handling command (" + tag + "): " + e.getMessage());
        }
    }

    private void handlePut() throws IOException, InterruptedException {
        if (arguments.length != 2) {
            System.out.println("(" + tag + ") Invalid number of arguments for 'put'.");
            return;
        }
        

        String data = command + " " + arguments[0] + " " + arguments[1];
        System.out.println("(" + tag + ") Sending '"+ data +"' command.");
        m.send(tag, data.getBytes());

        byte[] response = m.receive(tag);
        String responseString = new String(response);
        System.out.println("(" + tag + ") " + responseString);
    }

    private void handleGet() throws IOException, InterruptedException {
        if (arguments.length != 1) {
            System.out.println("(" + tag + ") Invalid number of arguments for 'get'.");
            return;
        }

        String data = command + " " + arguments[0];
        System.out.println("(" + tag + ") Sending '"+ data +"' command.");
        m.send(tag, data.getBytes());


        byte[] response = m.receive(tag);
        String responseString = new String(response);


        if (responseString.isEmpty()) {
            System.out.println("(" + tag + ") Key '"+ arguments[0] +"' not found.");
        } else {
            System.out.println("(" + tag + ") Value of key " + arguments[0] + ": " + responseString);
        }
    }

    private void handleMultiPut() throws IOException, InterruptedException {
        if (arguments.length < 3) {
            System.out.println("(" + tag + ") Invalid number of arguments for 'multiPut'.");
            return;
        }
    
        int n;
        try {
            n = Integer.parseInt(arguments[0]); // Get first argument with number of pairs
        } catch (NumberFormatException ex) {
            System.out.println("(" + tag + ") Invalid number of pairs specified for 'multiPut'.");
            return;
        }
    
        if (arguments.length != 1 + (2 * n)) {  // Check if n matches the number of pair key value
            System.out.println("(" + tag + ") Invalid arguments! Command 'multiPut' requires " + n + " key-value pairs.");
            return;
        }
    

        StringBuilder dataBuilder = new StringBuilder(command);
        dataBuilder.append(" ").append(n); 
        for (int i = 1; i < arguments.length; i++) {
            dataBuilder.append(" ").append(arguments[i]);
        }
        System.out.println("(" + tag + ") Sending '"+ dataBuilder +"' command.");
    
        // Send to server
        m.send(tag, dataBuilder.toString().getBytes());
    
        // Receive response
        byte[] response = m.receive(tag);
        String responseString = new String(response);
    
        System.out.println("(" + tag + ") " + responseString);
    }
    

    private void handleMultiGet() throws IOException, InterruptedException {
        if (arguments.length < 2) {
            System.out.println("(" + tag + ") Invalid number of arguments for 'multiGet'.");
            return;
        }
    
        int n;
        try {
            n = Integer.parseInt(arguments[0]); // get number of keys
        } catch (NumberFormatException ex) {
            System.out.println("(" + tag + ") Invalid number of keys specified for 'multiGet'.");
            return;
        }
    
        if (arguments.length != 1 + n) { // check if n matches
            System.out.println("(" + tag + ") Invalid arguments! Command 'multiGet' requires " + n + " keys.");
            return;
        }
    
        StringBuilder dataBuilder = new StringBuilder(command);
        dataBuilder.append(" ").append(n); 
        for (int i = 1; i < arguments.length; i++) {
            dataBuilder.append(" ").append(arguments[i]);
        }
        System.out.println("(" + tag + ") Sending '"+ dataBuilder +"' command.");
    
        // Send to server
        m.send(tag, dataBuilder.toString().getBytes());

        for (int i = 0; i < n; i ++) {
            byte[] response = m.receive(tag);
            String responseString = new String(response);
            String[] pairs = responseString.split(" ");

            if (pairs.length == 1) {
                System.out.println("(" + tag + ") Value not found to Key: " + pairs[0]);
            }
            else if (pairs.length == 2) {
                System.out.println("(" + tag + ") Key: " + pairs[0] + ", Value: " + pairs[1]);
            }
            else {
                System.out.println("(" + tag + ") Error");
            }
        } 
    }

    private void handleGeTWhen() throws IOException, InterruptedException {
        if (arguments.length != 3) {
            System.out.println("(" + tag + ") Invalid number of arguments for 'getWhen'.");
            return;
        }

        String data = command + " " + arguments[0] + " " + arguments[1] + " " + arguments[2];
        System.out.println("(" + tag + ") Sending '"+ data +"' command.");
        m.send(tag, data.getBytes());


        byte[] response = m.receive(tag);
        String responseString = new String(response);


        if (responseString.isEmpty()) {
            System.out.println("(" + tag + ") Condition met but Key '"+ arguments[0] +"' not found.");
        } else {
            System.out.println("(" + tag + ") Condition met, value of key " + arguments[0] + ": " + responseString + ".");
        }
    }
    
}
