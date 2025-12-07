import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class TestDatabase {
    public static void main(String[] args) {

        // 1. Define where the database file will be.
        // "./pictionaryTest" means "create a file named pictionaryTest in my project folder"
        String url = "jdbc:h2:./pictionaryTest";
        String user = "sa"; // Default username for H2
        String password = ""; // Default password is empty

        System.out.println("Attempting to connect...");

        // 2. Try to connect
        // The "try" block captures errors (like if the JAR isn't found)
        try (Connection conn = DriverManager.getConnection(url, user, password)) {

            // If we reach this line, the connection is OPEN and VALID.
            System.out.println("------------------------------------------");
            System.out.println("SUCCESS! Connected to the H2 database.");
            System.out.println("------------------------------------------");

        } catch (SQLException e) {
            // This runs only if something breaks
            System.out.println("ERROR: Could not connect.");
            e.printStackTrace();
        }
    }
}