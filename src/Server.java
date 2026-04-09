import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class Server {

    private static final int PORT = 8080;
    private static final int MAX_THREADS = 100;
    
    // Thread pool of 100
    private static final ExecutorService pool = Executors.newFixedThreadPool(MAX_THREADS);
    
    // Store active connections
    public static final ConcurrentHashMap<Integer, ClientHandler> clients = new ConcurrentHashMap<>();
    private static final AtomicInteger clientIdCounter = new AtomicInteger(1);
    
    private static ServerSocket serverSocket;
    private static volatile boolean running = true;

    public static void main(String[] args) {
        
        System.out.println("Starting C2 Server...");
        try {
            serverSocket = new ServerSocket(PORT);
            System.out.println("Server listening on port " + PORT);
            
            // Start Admin CLI Thread
            Thread cliThread = new Thread(Server::runAdminCLI);
            cliThread.setDaemon(true);
            cliThread.start();
            
            // Accept connections loop
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    int clientId = clientIdCounter.getAndIncrement();
                    
                    ClientHandler handler = new ClientHandler(clientId, clientSocket);
                    clients.put(clientId, handler);
                    
                    pool.execute(handler);
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
        }
    }
    
    private static void runAdminCLI() {
        Scanner scanner = new Scanner(System.in);
        while (running) {
            // Slight delay so the prompt isn't interleaved randomly
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
            
            System.out.print("C2> ");
            if (!scanner.hasNextLine()) break;
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) continue;
            
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
                    running = false;
                    shutdown();
                    return;
                    
                default:
                    System.out.println("[ERROR] Unknown command. Available: status | kill <id|all> | echo <id|all> <msg> | exit");
            }
        }
        scanner.close();
    }
    
    public static void removeClient(int clientId) {
        clients.remove(clientId);
    }
    
    private static void shutdown() {
        System.out.println("Shutting down server...");
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
            for (ClientHandler handler : clients.values()) {
                handler.disconnect();
            }
            pool.shutdown();
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("Server shutdown complete.");
        System.exit(0);
    }
}
