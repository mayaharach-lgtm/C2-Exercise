public class Simulation {

    public static void main(String[] args) {
        int clientCount = 200;
        System.out.println("Starting Simulation: Spawning " + clientCount + " clients...");
        
        for (int i = 0; i < clientCount; i++) {
            Thread clientThread = new Thread(() -> {
                Client client = new Client();
                client.start();
            });
            clientThread.start();
            
            // tiny sleep just to avoid overwhelming local network stack instantly in simple tests
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        
        System.out.println("All 200 clients spawned.");
    }
}
