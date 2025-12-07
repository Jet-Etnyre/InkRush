import java.io.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WordBank {
    // Connection Constant
    private static final String DB_URL = "jdbc:h2:./pictionaryDB";
    private static final String USER = "sa";
    private static final String PASS = "";
    // Store the path to the words file
    private String dictionaryFilePath;

    public WordBank(String filePath) {
        this.dictionaryFilePath = filePath;
        try{
            //Force load the driver
            Class.forName("org.h2.Driver");

            // Initialize Database Structure
            createTable();

            // load sample data only if the table is empty
            if(isTableEmpty()){
                populateSampleData(this.dictionaryFilePath);
            }
        }catch(ClassNotFoundException e){
            e.printStackTrace();
        }
    }

    // game logic
    public List<String> getThreeWords(){
        List<String> resultWords = new ArrayList<>();
        List<String> categories = getAllCategories();

        // Safety check
        if (categories.size() < 3) {
            System.out.println("Error: Not enough categories in database!");
            return resultWords;
        }

        // Shuffle and pick top 3 categories
        Collections.shuffle(categories);
        List<String> selectedCategories = categories.subList(0, 3);

        // Get one random word from each selected category
        for (String category : selectedCategories) {
            String word = getRandomWordFromCategory(category);
            if (word != null) {
                resultWords.add(word);
            }
        }

        return resultWords;
    }

    // private helper methods
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

    private String getRandomWordFromCategory(String category) {
        // H2 Specific Syntax: ORDER BY RAND() picks a random row
        String sql = "SELECT text FROM Words WHERE category = ? ORDER BY RAND() LIMIT 1";

        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASS);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

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

    // --- SETUP METHODS ---

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

    private void populateSampleData(String filename) {
        System.out.println("Loading words from resources: " + filename);

        // The "/" means "root of the resources folder"
        InputStream is = getClass().getResourceAsStream("/" + filename);

        if (is == null) {
            System.out.println("CRITICAL ERROR: Could not find '" + filename + "' in resources!");
            return;
        }

        try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            String line;
            int count = 0;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(":");
                if (parts.length == 2) {
                    addWord(parts[1].trim(), parts[0].trim());
                    count++;
                }
            }
            System.out.println("SUCCESS: Loaded " + count + " words.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

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


    // test main
    public static void main(String[] args) {
        String filePath;
        filePath = args[0];
        System.out.println("Starting WordBank with file: " + filePath);

        WordBank wb = new WordBank(filePath);

        System.out.println("Testing WordBank Logic...");

        // Test 1: Ask for 3 words
        List<String> gameWords = wb.getThreeWords();
        System.out.println("Generated Game Words: " + gameWords);

        // Test 2: Run it again to prove they are random
        List<String> gameWords2 = wb.getThreeWords();
        System.out.println("Generated Game Words (Round 2): " + gameWords2);
    }
}

