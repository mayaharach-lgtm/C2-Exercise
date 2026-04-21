import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Manages database operations for the C2 server.
 *
 * This class handles all interactions with the SQLite database, providing:
 * - Asynchronous logging of system events and command histories
 * - Thread-safe database initialization and connection management
 * - Separate tables for system logs and command/response tracking
 * - Graceful shutdown of the database executor
 *
 * All database operations are submitted to a single-threaded executor to prevent
 * blocking the main server thread and ensure data consistency.
 */

public class DatabaseManage {
    private static final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();
    private static final String URL = "jdbc:sqlite:c2_project.db";
    public static final int SERVER_ID = 0;

    public static void initialize() {
        try (Connection conn = DriverManager.getConnection(URL)) {
            if (conn != null) {
                Statement stmt = conn.createStatement();
                
                //logs table
                String sqlLogs = "CREATE TABLE IF NOT EXISTS system_logs (" +
                                 "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                                 "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP," +
                                 "event TEXT NOT NULL" +
                                 ");";
                
                // commands table
                String sqlCommands = "CREATE TABLE IF NOT EXISTS commands_history (" +
                                     "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                                     "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP," +
                                     "client_id INTEGER," +
                                     "command TEXT," +
                                     "response TEXT" +
                                     ");";
                
                stmt.execute(sqlLogs);
                stmt.execute(sqlCommands);
                System.out.println("[DB] Database initialized and tables created.");
                logEvent("System initialized with Server ID: " + SERVER_ID);
            }
        } catch (SQLException e) {
            System.err.println("[DB Error] " + e.getMessage());
        }
    }

    //Returns connection to DB
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL);
    }

    //Submit logEvent
    public static void logEvent(String event) {
        dbExecutor.execute(() -> {
            String sql = "INSERT INTO system_logs(event) VALUES(?)";
            try (Connection conn = getConnection(); 
                java.sql.PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, event);
                pstmt.executeUpdate();
            } 
            catch (SQLException e) {
                System.err.println("[DB Log Error] " + e.getMessage());
            }
        });
    }

    //Submit command\response
    public static void logCommand(int clientId, String command, String response) {
        dbExecutor.execute(() -> {
            String sql = "INSERT INTO commands_history(client_id, command, response) VALUES(?, ?, ?)";
            try (Connection conn = getConnection();
                 java.sql.PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, clientId);
                pstmt.setString(2, command);
                pstmt.setString(3, response);
                pstmt.executeUpdate();
            } 
            catch (SQLException e) {
                System.err.println("[DB Command Log Error] " + e.getMessage());
            }
        });
    }

    public static void close() {
        dbExecutor.shutdown();
        try {
            if (!dbExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                dbExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            dbExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
