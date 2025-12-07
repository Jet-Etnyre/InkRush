import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WordBank {
    // Connetcion Constant
    private static final String DB_URL = "jdbc:h2:./pictionaryDB";
    private static final String USER = "sa";
    private static final String PASS = "";

    public WordBank(){
        try{
            //Force load the driver
            Class.forName("org.h2.Driver");

            // Initialize Database Structure
            createTable();

            // load sample data only if the table is empty
            if(isTableEmpty()){
                populateSampleData();
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

    private void populateSampleData() {
        System.out.println("Database is empty. Populating with sample words...");

        // 5 Categories, 3 words each
        addWord("Giraffe", "Animals");
        addWord("Lion", "Animals");
        addWord("Elephant", "Animals");

        addWord("Toaster", "Objects");
        addWord("Hammer", "Objects");
        addWord("Umbrella", "Objects");

        addWord("Running", "Actions");
        addWord("Swimming", "Actions");
        addWord("Cooking", "Actions");

        addWord("Hospital", "Places");
        addWord("School", "Places");
        addWord("Airport", "Places");

        addWord("Batman", "Characters");
        addWord("Spiderman", "Characters");
        addWord("Joker", "Characters");
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
        WordBank wb = new WordBank();

        System.out.println("Testing WordBank Logic...");

        // Test 1: Ask for 3 words
        List<String> gameWords = wb.getThreeWords();
        System.out.println("Generated Game Words: " + gameWords);

        // Test 2: Run it again to prove they are random
        List<String> gameWords2 = wb.getThreeWords();
        System.out.println("Generated Game Words (Round 2): " + gameWords2);
    }
}

