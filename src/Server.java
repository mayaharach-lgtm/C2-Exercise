import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class Server {

    private static final int PORT = 8080;
    private static final int MAX_THREADS = 40;
    private static final ExecutorService pool = Executors.newFixedThreadPool(MAX_THREADS);
    // Store active connections
    public static final ConcurrentHashMap<Integer, ClientHandler> clients = new ConcurrentHashMap<>();
    private static final AtomicInteger clientIdCounter = new AtomicInteger(1); //Atomic integer for safe concurrency
    private static ServerSocket serverSocket;
    private static volatile boolean running = true;
    private static Thread adminThread;

    public static void main(String[] args) {
        
        System.out.println("Starting Server...");
        try {
            serverSocket = new ServerSocket(PORT);
            System.out.println("Server listening on port " + PORT);
            
            // Admin thread handles CLI commands
            adminThread = new Thread(Server::runAdminCLI);
            adminThread.start();
            
            // Accept connections loop
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    int clientId = clientIdCounter.getAndIncrement();
                    ClientHandler handler = new ClientHandler(clientId, clientSocket);
                    clients.put(clientId, handler);
                    pool.execute(handler);
                    System.out.println("\n[INFO] Client " + clientId + " connected.");
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
                            processCommand(input);
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
                    break;
                    
                case "kill":
                    if (parts.length < 2) {
                        System.out.println("[ERROR] Usage: kill <clientId|all>");
                    } else if (parts[1].equalsIgnoreCase("all")) {
                        int killed = 0;
                        for (ClientHandler handler : clients.values()) {
                            handler.disconnect();
                            killed++;
                        }
                        System.out.println("[INFO] Sent disconnect to " + killed + " clients.");
                    } else {
                        try {
                            int id = Integer.parseInt(parts[1]);
                            ClientHandler handler = clients.get(id);
                            if (handler != null) {
                                handler.disconnect();
                                System.out.println("[INFO] Killed client " + id);
                            } else {
                                System.out.println("[ERROR] Client " + id + " not found.");
                            }
                        } catch (NumberFormatException e) {
                            System.out.println("[ERROR] Invalid Client ID format.");
                        }
                    }
                    break;
                    
                case "echo":
                    if (parts.length < 3) {
                        System.out.println("[ERROR] Usage: echo <clientId|all> <message>");
                    } else {
                        String target = parts[1];
                        String msg = parts[2];
                        if (target.equalsIgnoreCase("all")) {
                            for (ClientHandler handler : clients.values()) {
                                handler.sendMessage("ECHO " + msg);
                            }
                            System.out.println("[INFO] Sent echo to all active clients.");
                        } else {
                            try {
                                int id = Integer.parseInt(target);
                                ClientHandler handler = clients.get(id);
                                if (handler != null) {
                                    handler.sendMessage("ECHO " + msg);
                                    System.out.println("[INFO] Sent echo to client " + id);
                                } else {
                                    System.out.println("[ERROR] Client " + id + " not found.");
                                }
                            } catch (NumberFormatException e) {
                                System.out.println("[ERROR] Invalid Client ID format.");
                            }
                        }
                    }
                    break;
                    
                case "exit":
                System.out.println("Initiating shutdown...");
                    running = false;
                    shutdown();
                break;
                    
                default:
                    System.out.println("[ERROR] Unknown command. Available: status | kill <id|all> | echo <id|all> <msg> | exit");
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
        System.out.println("Server shutdown complete.");
        System.exit(0);
    }
}
