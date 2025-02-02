package server;

public class ServerManager {
    private static Thread serverThread;

    public static void startServer() {
        if (serverThread == null || !serverThread.isAlive()) {
            serverThread = new Thread(() -> {
                try {
                    Server server = new Server(); // Inicializa o servidor
                    server.start(); // Inicia o servidor
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            serverThread.start();

            // Esperar o servidor inicializar
            try {
                Thread.sleep(2000); // Ajustar se necessário
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public static void stopServer() {
        if (serverThread != null && serverThread.isAlive()) {
            serverThread.interrupt(); // Para o servidor
        }
    }
}
