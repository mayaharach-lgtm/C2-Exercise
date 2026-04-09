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
            
            sendMessage("ID " + clientId);
            
            String inputLine;
            while (running && (inputLine = in.readLine()) != null) {
                String decrypted = CryptoUtils.decrypt(inputLine);
                if (decrypted == null) continue;
                if (decrypted.equals("QUIT")) {
                    break;
                }
            }
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to close client streams", e);
        } finally {
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
}
