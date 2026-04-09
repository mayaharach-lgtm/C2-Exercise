import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ClientHandler implements Runnable {

    private final int clientId;
    private final Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    
    private volatile boolean running = true;

    public ClientHandler(int clientId, Socket socket) {
        this.clientId = clientId;
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            // Greet client
            out.println("ID " + clientId);
            
            // Listen for client responses if needed (not heavily used in phase 1)
            String inputLine;
            while (running && (inputLine = in.readLine()) != null) {
                // If we get an explicit close
                if (inputLine.equals("QUIT")) {
                    break;
                }
            }
            
        } catch (IOException e) {
            // Usually triggered when socket closes unexpectedly, ignored for simplicity.
        } finally {
            cleanup();
        }
    }
    
    public void sendMessage(String msg) {
        if (out != null && running) {
            out.println(msg);
        }
    }
    
    public void disconnect() {
        sendMessage("KILL");
        running = false;
        cleanup();
    }

    private synchronized void cleanup() {
        Server.removeClient(clientId);
        try {
            if (out != null) out.close();
            if (in != null) in.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            // Ignored
        }
    }
}
