import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class Client {

    private static final String HOST = "127.0.0.1";
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
            
            String serverMsg;
            while ((serverMsg = in.readLine()) != null) {
                if (serverMsg.startsWith("ID ")) {
                    myId = Integer.parseInt(serverMsg.substring(3).trim());
                    System.out.println("[Client " + myId + "] Connected to server.");
                } 
                else if (serverMsg.startsWith("ECHO ")) {
                    String msg = serverMsg.substring(5);
                    System.out.println("[Client " + myId + "] Echo: " + msg);
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
        try {
            if (out != null) out.close();
            if (in != null) in.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } 
        catch (IOException e) {
            throw new RuntimeException("Failed to close client streams", e);
        }
    }

    public static void main(String[] args) {
        Client client = new Client();
        client.start();
    }
}
