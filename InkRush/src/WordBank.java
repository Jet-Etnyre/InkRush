import java.io.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The WordBank class manages all database interactions for the Pictionary game.
 * Responsibilities:
 * 1. Establishes a connection to the embedded H2 database.
 * 2. Initializes the database table structure if it doesn't exist.
 * 3. Populates the database with initial words from a resource file (words.txt).
 * 4. Provides game logic to retrieve random words from distinct categories.

 */
public class WordBank {

    // Database Connection Constants
    // "jdbc:h2:./pictionaryDB" creates a file named pictionaryDB.mv.db in the project root
    private static final String DB_URL = "jdbc:h2:./pictionaryDB";
    private static final String USER = "sa";
    private static final String PASS = "";

    // The filename of the text file containing the initial word list (stored in resources)
    private String dictionaryFilePath;

    /**
     * Constructor for WordBank.
     * Initializes the database connection and prepares the data.
     *
     * @param filePath The name of the file in the 'resources' folder to load words from (e.g., "words.txt").
     */
    public WordBank(String filePath) {
        this.dictionaryFilePath = filePath;
        try {
            // 1. Force load the H2 driver to ensure it is available
            Class.forName("org.h2.Driver");

            // 2. Create the 'Words' table if it does not exist yet
            createTable();

            // 3. Check if the table is empty. If so, fill it with data from the text file.
            if (isTableEmpty()) {
                populateSampleData(this.dictionaryFilePath);
            }
        } catch (ClassNotFoundException e) {
            System.err.println("CRITICAL ERROR: H2 Driver not found.");
            e.printStackTrace();
        }
    }

    // --- GAME LOGIC ---

    /**
     * Generates a list of 3 random words for a game round.
     * <p>
     * Logic:
     * 1. Retrieves all unique categories from the database.
     * 2. Shuffles them and picks the top 3.
     * 3. Selects one random word from each of those 3 categories.
     * </p>
     *
     * @return A List of 3 distinct strings (e.g., ["Lion", "Toaster", "Running"]).
     */
    public List<String> getThreeWords() {
        List<String> resultWords = new ArrayList<>();
        List<String> categories = getAllCategories();

        // Safety check: Ensure we have enough categories to play
        if (categories.size() < 3) {
            System.out.println("Error: Not enough categories in database!");
            return resultWords;
        }

        // Shuffle categories to ensure randomness every time
        Collections.shuffle(categories);

        // Select the first 3 categories after shuffling
        List<String> selectedCategories = categories.subList(0, 3);

        // Fetch one random word for each selected category
        for (String category : selectedCategories) {
            String word = getRandomWordFromCategory(category);
            if (word != null) {
                resultWords.add(word);
            }
        }

        return resultWords;
    }

    // --- PRIVATE HELPER METHODS (JDBC Queries) ---

    /**
     * Retrieves a list of all unique categories currently stored in the database.
     *
     * @return A list of category names (e.g., "Animals", "Places").
     */
    private List<String> getAllCategories() {
        List<String> cats = new ArrayList<>();
        String sql = "SELECT DISTINCT category FROM Words";

        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASS);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                cats.add(rs.getString("category"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return cats;
    }

    /**
     * Selects a single random word from a specific category.
     * Uses H2's "ORDER BY RAND()" syntax for efficient randomization.
     *
     * @param category The category to search within (e.g., "Animals").
     * @return The text of the random word, or null if error.
     */
    private String getRandomWordFromCategory(String category) {
        String sql = "SELECT text FROM Words WHERE category = ? ORDER BY RAND() LIMIT 1";

        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASS);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            // Securely set the category parameter
            pstmt.setString(1, category);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("text");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    // --- SETUP & INFRASTRUCTURE METHODS ---

    /**
     * Creates the 'Words' table structure if it doesn't already exist.
     * Schema:
     * - id: Auto-incrementing Integer (Primary Key)
     * - text: The word itself (e.g., "Giraffe")
     * - category: The category (e.g., "Animals")
     */
    private void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS Words (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "text VARCHAR(255), " +
                "category VARCHAR(255))";

        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASS);
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Checks if the 'Words' table is currently empty.
     * Used to determine if we need to run the initial data population.
     *
     * @return true if table has 0 rows, false otherwise.
     */
    private boolean isTableEmpty() {
        String sql = "SELECT COUNT(*) AS count FROM Words";
        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASS);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt("count") == 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return true;
    }

    /**
     * Reads words from a text file inside the 'resources' folder and inserts them into the database.
     * <p>
     * Uses {@code getResourceAsStream} to ensure the file can be read even when
     * the application is packaged as a JAR file.
     * </p>
     *
     * @param filename The name of the file to read (e.g., "words.txt").
     */
    private void populateSampleData(String filename) {
        System.out.println("Loading words from resources: " + filename);

        // Access the file from the classpath (resources root)
        InputStream is = getClass().getResourceAsStream("/" + filename);

        if (is == null) {
            System.err.println("CRITICAL ERROR: Could not find '" + filename + "' in resources!");
            return;
        }

        try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            String line;
            int count = 0;

            // Read line by line. Expected format: "Category:Word"
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(":");
                if (parts.length == 2) {
                    addWord(parts[1].trim(), parts[0].trim());
                    count++;
                }
            }
            System.out.println("SUCCESS: Loaded " + count + " words into database.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Helper method to insert a single word into the database.
     *
     * @param text     The word text.
     * @param category The category of the word.
     */
    private void addWord(String text, String category) {
        String sql = "INSERT INTO Words (text, category) VALUES (?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASS);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, text);
            pstmt.setString(2, category);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Main method for testing the WordBank functionality independently.
     * Requires 'words.txt' to be present in the resources folder.
     * Usage: java WordBank <filename>
     */
    public static void main(String[] args) {
        String filePath;

        // Defensive check: Ensure an argument is provided
        if (args.length > 0) {
            filePath = args[0];
        } else {
            // Default fallback for testing convenience
            filePath = "words.txt";
        }

        System.out.println("Starting WordBank with file: " + filePath);

        WordBank wb = new WordBank(filePath);

        System.out.println("Testing WordBank Logic...");

        // Test 1: Generate a set of words
        List<String> gameWords = wb.getThreeWords();
        System.out.println("Generated Game Words: " + gameWords);

        // Test 2: Generate a second set to verify randomness
        List<String> gameWords2 = wb.getThreeWords();
        System.out.println("Generated Game Words (Round 2): " + gameWords2);
    }
}