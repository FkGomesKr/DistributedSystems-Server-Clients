package client;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import server.Server;

import conn.Frame;
import conn.TaggedConnection;

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.data.category.DefaultCategoryDataset;

import java.io.File;
import java.io.IOException;
import java.util.List;


import static org.junit.jupiter.api.Assertions.*;

class ClientTests {

    private static Thread serverThread;

    @BeforeAll
    static void startServer() {
        serverThread = new Thread(() -> {
            try {
                Server server = new Server();
                server.start(); // Inicia o servidor
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        serverThread.start();

        // Esperar o servidor inicializar
        try {
            Thread.sleep(2000); // Ajuste se necessário
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @AfterAll
    static void stopServer() {
        if (serverThread != null && serverThread.isAlive()) {
            serverThread.interrupt(); // Interrompe a thread do servidor
        }
    }

    private void generateLineChart(String title, String xLabel, String yLabel, List<Integer> xValues, List<Double> yValues, String outputPath) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        for (int i = 0; i < xValues.size(); i++) {
            dataset.addValue(yValues.get(i), "Métricas", xValues.get(i).toString());
        }
    
        JFreeChart lineChart = ChartFactory.createLineChart(
                title,
                xLabel,
                yLabel,
                dataset
        );
    
        try {
            ChartUtils.saveChartAsPNG(new File(outputPath), lineChart, 800, 600);
            System.out.println("Gráfico salvo em: " + outputPath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    void testPutAndGetCommand() {
        Socket socket = null;
        TaggedConnection conn = null;

        try {
            socket = new Socket("127.0.0.1", 12345);
            conn = new TaggedConnection(socket);

            // Registo e login
            conn.send(new Frame(1, "userPutAndGet:password1".getBytes()));
            conn.receive(); // Ignorar resposta do registo
            conn.send(new Frame(2, "userPutAndGet:password1".getBytes())); // Login
            conn.receive(); // Ignorar resposta do login

            // Comando PUT
            String putCommand = "put key1 value1";
            conn.send(new Frame(3, putCommand.getBytes())); // Tag 3 = PUT
            Frame putResponse = conn.receive();
            String putMessage = new String(putResponse.data);

            assertEquals("Key 'key1' updated successfully.", putMessage, "PUT falhou.");

            // Comando GET
            String getCommand = "get key1";
            conn.send(new Frame(4, getCommand.getBytes())); // Tag 4 = GET
            Frame getResponse = conn.receive();
            String getMessage = new String(getResponse.data);

            assertEquals("value1", getMessage, "GET falhou.");


        } catch (Exception e) {
            throw new AssertionError("Erro no comando PUT/GET: " + e.getMessage());
        } finally {
            try {
                if (conn != null) {
                    conn.send(new Frame(0, "end".getBytes())); // Notificar o servidor
                    Thread.sleep(50); // Pequeno atraso para garantir o processamento
                    conn.close();
                }
                if (socket != null && !socket.isClosed()) socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Test
    void testMultipleClients() {
        ExecutorService executor = Executors.newFixedThreadPool(5);

        for (int i = 0; i < 5; i++) {
            int clientId = i;

            executor.submit(() -> {
                Socket socket = null;
                TaggedConnection conn = null;

                try {
                    socket = new Socket("127.0.0.1", 12345);
                    conn = new TaggedConnection(socket);

                    // Registo
                    conn.send(new Frame(1, ("userMultiCli" + clientId + ":pass" + clientId).getBytes()));
                    conn.receive();

                    // Login
                    conn.send(new Frame(2, ("userMultiCli" + clientId + ":pass" + clientId).getBytes()));
                    conn.receive();

                    // Comando PUT
                    conn.send(new Frame(3, ("put key" + clientId + " value" + clientId).getBytes()));
                    conn.receive();

                } catch (Exception e) {
                    throw new AssertionError("Erro no cliente " + clientId + ": " + e.getMessage());
                } finally {
                    try {
                        if (conn != null) {
                            conn.send(new Frame(0, "end".getBytes())); // Notificar o servidor
                            Thread.sleep(50); // Pequeno atraso para garantir o processamento
                            conn.close();
                        }
                        if (socket != null && !socket.isClosed()) socket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        executor.shutdown();
        try {
            executor.awaitTermination(1, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new AssertionError("Execução interrompida: " + e.getMessage());
        }
    }

    @Test
    void testMultiPutAndMultiGet() {
        Socket socket = null;
        TaggedConnection conn = null;

        try {
            socket = new Socket("127.0.0.1", 12345);
            conn = new TaggedConnection(socket);

            // Registo e login
            conn.send(new Frame(1, "userMulti:password".getBytes()));
            conn.receive(); // Ignorar resposta do registo
            conn.send(new Frame(2, "userMulti:password".getBytes())); // Login
            conn.receive(); // Ignorar resposta do login

            // Comando multiPut
            String multiPutCommand = "multiput 2 key1 value1 key2 value2";
            conn.send(new Frame(3, multiPutCommand.getBytes())); // Tag 3 = multiPut
            Frame response = conn.receive();
            String multiPutResponse = new String(response.data);
            assertEquals("All keys updated successfully.", multiPutResponse, "Falha no comando multiPut.");

            // Comando multiGet
            String multiGetCommand = "multiget 2 key1 key2";
            conn.send(new Frame(4, multiGetCommand.getBytes())); // Tag 4 = multiGet

            // Receber múltiplas mensagens
            StringBuilder multiGetResponses = new StringBuilder();
            for (int i = 0; i < 2; i++) { // Esperar 2 respostas (número de chaves)
                response = conn.receive();
                multiGetResponses.append(new String(response.data)).append(" ");
            }

            String combinedResponse = multiGetResponses.toString().trim();
            assertTrue(combinedResponse.contains("key1 value1") && combinedResponse.contains("key2 value2"),
                    "Falha no comando multiGet.");

        } catch (Exception e) {
            throw new AssertionError("Erro nos comandos multiPut/multiGet: " + e.getMessage());
        } finally {
            try {
                if (conn != null) {
                    conn.send(new Frame(0, "end".getBytes())); // Notificar o servidor
                    Thread.sleep(50); // Pequeno atraso para garantir o processamento
                    conn.close();
                }
                if (socket != null && !socket.isClosed()) socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }


    @Test
    void testInvalidInputs() {
        Socket socket = null;
        TaggedConnection conn = null;

        try {
            socket = new Socket("127.0.0.1", 12345);
            conn = new TaggedConnection(socket);

            // Registo e login
            conn.send(new Frame(1, "userInvalid:password".getBytes()));
            conn.receive(); // Ignorar resposta do registo
            conn.send(new Frame(2, "userInvalid:password".getBytes())); // Login
            conn.receive(); // Ignorar resposta do login

            // Comando inválido
            String invalidCommand = "invalidCommand";
            conn.send(new Frame(5, invalidCommand.getBytes())); // Tag 5 = comando inválido
            Frame response = conn.receive();
            String errorResponse = new String(response.data);
            assertEquals("Unsupported command: invalidCommand", errorResponse, "Comando inválido não tratado corretamente.");


        } catch (Exception e) {
            throw new AssertionError("Erro no teste de inputs inválidos: " + e.getMessage());
        } finally {
            try {
                if (conn != null) {
                    conn.send(new Frame(0, "end".getBytes())); // Notificar o servidor
                    Thread.sleep(50); // Pequeno atraso para garantir o processamento
                    conn.close();
                }
                if (socket != null && !socket.isClosed()) socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Test
    void testHighConcurrency() {
        int NUM_CLIENTS = 50;
        ExecutorService executor = Executors.newFixedThreadPool(NUM_CLIENTS);

        for (int i = 0; i < NUM_CLIENTS; i++) {
            int clientId = i;

            executor.submit(() -> {
                Socket socket = null;
                TaggedConnection conn = null;

                try {
                    socket = new Socket("127.0.0.1", 12345);
                    conn = new TaggedConnection(socket);

                    // Registo e login
                    conn.send(new Frame(1, ("userHigh" + clientId + ":pass" + clientId).getBytes()));
                    conn.receive();
                    conn.send(new Frame(2, ("userHigh" + clientId + ":pass" + clientId).getBytes()));
                    conn.receive();

                    // Comando PUT
                    conn.send(new Frame(3, ("put key" + clientId + " value" + clientId).getBytes()));
                    conn.receive();


                } catch (Exception e) {
                    throw new AssertionError("Erro no cliente " + clientId + ": " + e.getMessage());
                } finally {
                    try {
                        if (conn != null) {
                            conn.send(new Frame(0, "end".getBytes())); // Notificar o servidor
                            Thread.sleep(50); // Pequeno atraso para garantir o processamento
                            conn.close();
                        }
                        if (socket != null && !socket.isClosed()) socket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        executor.shutdown();
        try {
            executor.awaitTermination(2, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new AssertionError("Execução interrompida: " + e.getMessage());
        }
    }

    @Test
    void testWorkload() {
        int[] clientCounts = {10, 50, 100, 500, 1000}; // Diferentes números de clientes
        List<Integer> clients = new ArrayList<>();
        List<Double> totalTimes = new ArrayList<>();
        List<Double> averageResponseTimes = new ArrayList<>();

        for (int numClients : clientCounts) {
            ExecutorService executor = Executors.newFixedThreadPool(250);
            List<Double> clientTimes = Collections.synchronizedList(new ArrayList<>());
            long startTime = System.nanoTime();

            for (int i = 0; i < numClients; i++) {
                int clientId = i;
                executor.submit(() -> {
                    long clientStartTime = System.nanoTime();
                    Socket socket = null;
                    TaggedConnection conn = null;

                    try {
                        socket = new Socket("127.0.0.1", 12345);
                        conn = new TaggedConnection(socket);

                        // Registo e Login
                        conn.send(new Frame(1, ("userWorkload" + clientId + ":pass" + clientId).getBytes()));
                        conn.receive();
                        conn.send(new Frame(2, ("userWorkload" + clientId + ":pass" + clientId).getBytes()));
                        conn.receive();

                        for (int j = 0; j < 100; j++) { // Operações MIXED
                            conn.send(new Frame(3, ("put key" + clientId + " value" + clientId).getBytes()));
                            conn.receive();
                            conn.send(new Frame(4, ("get key" + clientId).getBytes()));
                            conn.receive();
                        }
                    } catch (IOException e) {
                        System.err.printf("Erro no cliente %d: %s%n", clientId, e.getMessage());
                    } finally {
                        long clientEndTime = System.nanoTime();
                        synchronized (clientTimes) {
                            clientTimes.add((clientEndTime - clientStartTime) / 1e6); // Tempo em ms
                        }
                        try {
                            if (conn != null) {
                                conn.send(new Frame(0, "end".getBytes())); // Notificar o servidor
                                Thread.sleep(50); // Pequeno atraso para garantir o processamento
                                conn.close();
                            }
                            if (socket != null && !socket.isClosed()) socket.close();
                        } catch (IOException | InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                });
            }

            executor.shutdown();
            try {
                executor.awaitTermination(2, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                throw new AssertionError("Execução interrompida: " + e.getMessage());
            }

            long endTime = System.nanoTime();
            double totalTime = (endTime - startTime) / 1e6;
            double avgResponseTime = clientTimes.stream().mapToDouble(Double::doubleValue).average().orElse(0);

            clients.add(numClients);
            totalTimes.add(totalTime);
            averageResponseTimes.add(avgResponseTime);

            System.out.printf("Número de clientes: %d, Tempo total: %.2f ms, Tempo médio: %.2f ms%n",
                    numClients, totalTime, avgResponseTime);
        }

        // Gerar gráficos
        generateLineChart("Workload - Tempo Total", "Número de Clientes", "Tempo Total (ms)",
                clients, totalTimes, "workload_total_time.png");
        generateLineChart("Workload - Tempo Médio", "Número de Clientes", "Tempo Médio de Resposta (ms)",
                clients, averageResponseTimes, "workload_avg_response_time.png");
    }



    @Test
    void testScalability() {
        int[] clientCounts = {10, 50, 100, 500, 1000, 10000};
        List<Integer> clients = new ArrayList<>();
        List<Double> totalTimes = new ArrayList<>();
        List<Double> averageResponseTimes = new ArrayList<>();

        for (int numClients : clientCounts) {
            ExecutorService executor = Executors.newFixedThreadPool(250);
            long[] responseTimes = new long[numClients];
            long startTime = System.nanoTime();

            for (int i = 0; i < numClients; i++) {
                int clientId = i;
                executor.submit(() -> {
                    long opStartTime = System.nanoTime();
                    Socket socket = null;
                    TaggedConnection conn = null;

                    try {
                        socket = new Socket("127.0.0.1", 12345);
                        conn = new TaggedConnection(socket);

                        // Registo e Login
                        conn.send(new Frame(1, ("userScalability" + clientId + ":pass" + clientId).getBytes()));
                        conn.receive();
                        conn.send(new Frame(2, ("userScalability" + clientId + ":pass" + clientId).getBytes()));
                        conn.receive();

                        // Operações PUT e GET
                        conn.send(new Frame(3, ("put key" + clientId + " value" + clientId).getBytes()));
                        conn.receive();
                        conn.send(new Frame(4, ("get key" + clientId).getBytes()));
                        conn.receive();

                    } catch (IOException e) {
                        System.err.printf("Erro no cliente %d: %s%n", clientId, e.getMessage());
                    } finally {
                        long opEndTime = System.nanoTime();
                        responseTimes[clientId] = opEndTime - opStartTime;
                        try {
                            if (conn != null){
                                conn.send(new Frame(0, "end".getBytes())); // Notificar o servidor
                                Thread.sleep(50); // Pequeno atraso para garantir o processamento
                                conn.close();
                            }
                            if (socket != null && !socket.isClosed()) socket.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                });
            }

            executor.shutdown();
            try {
                executor.awaitTermination(2, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                throw new AssertionError("Execução interrompida: " + e.getMessage());
            }

            long endTime = System.nanoTime();
            double totalTime = (endTime - startTime) / 1e6;
            double avgResponseTime = Arrays.stream(responseTimes).average().orElse(0) / 1e6;

            clients.add(numClients);
            totalTimes.add(totalTime);
            averageResponseTimes.add(avgResponseTime);

            System.out.printf("Número de clientes: %d, Tempo total: %.2f ms, Tempo de resposta médio: %.2f ms%n",
                    numClients, totalTime, avgResponseTime);
        }

        // Gerar gráficos
        generateLineChart("Escalabilidade - Tempo Total", "Número de Clientes", "Tempo Total (ms)",
                clients, totalTimes, "scalability_total_time.png");
        generateLineChart("Escalabilidade - Tempo Médio", "Número de Clientes", "Tempo Médio de Resposta (ms)",
                clients, averageResponseTimes, "scalability_avg_response_time.png");
    }






}
