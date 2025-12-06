import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * GameLogic class manages the core game mechanics for the game.
 * Handles word selection, guess validation, scoring, rounds, and timing.
 *
 * The way the Game Flow works is as follows:
 * 1- Round starts and drawer gets selected as well as word getting chosen
 * 2- Timer starts tracking round duration
 * 3- Players submit guesses
 * 4- GameLogic validates the guesses against the current word, correct guess gets awarded points
 * 6- Round ends after time expires or correct guess
 * 7- Next drawer gets selected and the whole flow repeats
 */
public class GameLogic {
    private static final int ROUND_DURATION_SECONDS = 60;
    private static final int[] GUESS_POINTS = {200, 150, 100, 50}; // the 1st, 2nd, 3rd, and 4th guesser
    private static final int POINTS_DRAWER_BONUS = 50; // drawer gets points when someone guesses

    private String currentWord;
    private int currentDrawerID;
    private long roundStartTime;
    private boolean roundActive;
    private Random random;

    // player tracking
    private Map<Integer, PlayerInfo> players;
    private List<Integer> playerOrder;
    private int currentDrawerIndex;

    // track who has guessed correctly this round
    private List<Integer> correctGuessers;
    private int guessCount;

    // word bank
    // TODO: change this later, not the final implementation
     private String[] wordBank = {
        "elephant", "guitar", "pizza", "rainbow", "rocket",
        "butterfly", "mountain", "computer", "dragon", "sunset",
        "penguin", "castle", "bicycle", "treasure", "lightning"
    };

    /**
     * Creates a new GameLogic instance.
     * Initializes player tracking and game state.
     */
    public GameLogic() {
        players = new HashMap<>();
        playerOrder = new ArrayList<>();
        correctGuessers = new ArrayList<>();
        random = new Random();
        roundActive = false;
        currentDrawerIndex = 0;
        guessCount = 0;
    }

    /**
     * Registers a new player in the game.
     * @param clientID the client's unique ID
     * @param username the player's username
     */
    public void addPlayer(int clientID, String username) {
        PlayerInfo player = new PlayerInfo(clientID, username);
        players.put(clientID, player);
        playerOrder.add(clientID);
    }

    /**
     * Removes a player from the game
     * @param clientID the clident ID to remove
     */
    public void removePlayer(int clientID) {
        players.remove(clientID);
        playerOrder.remove(Integer.valueOf(clientID));
    }

    /**
     * Starts a new round with the next drawer.
     * Selects a random word and starts the timer.
     * Automatically rotates through all players as drawers.
     * @return the drawer's client ID or -1 if not enough players
     */
    public int startNewRound() {
        if (playerOrder.isEmpty()) {
            return -1;
        }
        // select next drawer (rotates through all players)
        currentDrawerID = playerOrder.get(currentDrawerIndex);

        // select random word
        currentWord = selectRandomWord();

        // start timer
        roundStartTime = System.currentTimeMillis();
        roundActive = true;

        // reset guess tracking for new round
        correctGuessers.clear();
        guessCount = 0;

        return currentDrawerID;
    }

    /**
     * Selects a random word from the word bank.
     * @return randomly selected word
     */
    private String selectRandomWord() {
        int index = random.nextInt(wordBank.length);
        return wordBank[index];
    }

    /**
     * Checks if a guess is correct.
     * This is going to be case-insensitive comparison with the current word.
     * @param guess the player's guess
     * @return true if gues matches current word, false otherwise
     */
    public boolean checkGuess(String guess) {
        if (!roundActive || currentWord == null) {
            return false;
        }
        return currentWord.equalsIgnoreCase(guess.trim());
    }

    /**
     * Awards points to a player for a correct guess based on guess order.
     * First correct guess gets the most points, decreasing for later guessers.
     * Also awards bonus points to the drawer.
     * @param guesserID to the ID of player who guessed correctly
     * @return the points awarded to the guesser
     */
    public int awardPoints(int guesserID) {
        // check if this player already guessed correctly
        if (correctGuessers.contains(guesserID)) {
            return 0;
        }
        // check if guesser is the drawer (can't guess your own word!)
        if (guesserID == currentDrawerID) {
            return 0; // drawer can't score by guessing
        }

        PlayerInfo guesser = players.get(guesserID);
        PlayerInfo drawer = players.get(currentDrawerID);

        if (guesser == null) {
            return 0;
        }

        // calculate points based on guess order
        int pointsAwarded = 0;
        if (guessCount < GUESS_POINTS.length) {
            pointsAwarded = GUESS_POINTS[guessCount];
        } else {
            // if more than 4 guessers, give minimum points
            pointsAwarded = GUESS_POINTS[GUESS_POINTS.length - 1];
        }

        // award points to guesser
        guesser.addScore(pointsAwarded);

        // award bonus to drawer (gets points for each correct guess)
        if (drawer != null) {
            drawer.addScore(POINTS_DRAWER_BONUS);
        }

        // track this guesser
        correctGuessers.add(guesserID);
        guessCount++;

        return pointsAwarded;
    }

    /**
     * Checks if a player has already guessed correctly this round.
     * @param clientID the client ID to check
     * @return true if already guessed correctly, false otherwise
     */
    public boolean hasGuessedCorrectly(int clientID) {
        return correctGuessers.contains(clientID);
    }

    /**
     * Gets the current guess count (how many have guessed correctly)
     * @return number of correct guessers so far
     */
    public int getGuessCount() {
        return guessCount;
    }

    /**
     * Ends the current round.
     * Advances to the next drawer in rotation.
     * Clears round state and guess tracking.
     */
    public void endRound() {
        roundActive = false;
        currentWord = null;
        correctGuessers.clear();
        guessCount = 0;

        // move to next drawer (cycles through: 0 -> 1 -> 2 -> ... -> N-1 -> 0)
        currentDrawerIndex = (currentDrawerIndex + 1) % playerOrder.size();
    }

    /**
     * Gets the ID of who will draw in the next round.
     * Useful for previewing next drawer without ending current round.
     * @return the next drawer's client ID
     */
    public int getNextDrawerID() {
        if (playerOrder.isEmpty()) {
            return -1;
        }
        int nextIndex = (currentDrawerIndex + 1) % playerOrder.size();
        return playerOrder.get(nextIndex);
    }

    /**
     * Gets the time remaining in the current round.
     * @return seconds remaining, of 0 if round not active
     */
    public int getTimeRemaining() {
        if (!roundActive) {
            return 0;
        }

        long currentTime = System.currentTimeMillis();
        long elapsedMillis = currentTime - roundStartTime;
        long elapsedSeconds = elapsedMillis / 1000;

        int remaining = ROUND_DURATION_SECONDS - (int) elapsedSeconds;
        return Math.max(0, remaining);
    }

    /**
     * Checks if the round time has expired
     * @return true if time is up, false otherwise
     */
    public boolean  isTimeUp() {
        return roundActive && getTimeRemaining() <= 0;
    }

    /**
     * Gets the current word being drawn
     * @return the current word
     */
    public String getCurrentWord() {
        return currentWord;
    }

    /**
     * Gets a hint for the current word (underscores with length)
     * @return hint string with word length
     */
    public String getWordHint() {
        if (currentWord == null) {
            return "";
        }

        StringBuilder hint = new StringBuilder();
        for (int i = 0; i < currentWord.length(); i++) {
            hint.append("_ ");
        }
        return hint.toString().trim();
    }

    /**
     * Gets the current drawer's ID
     * @return drawer's client ID
     */
    public int getCurrentDrawerID() {
        return currentDrawerID;
    }

    /**
     * Checks if a round is currently active.
     * @return true if round is active, false otherwise
     */
    public boolean isRoundActive() {
        return roundActive;
    }

    /**
     * Gets a player's current score
     * @param clientID the client ID
     * @return the player's score, or 0 if not found
     */
    public int getPlayerScore(int clientID) {
        PlayerInfo player = players.get(clientID);
        return player != null ? player.getScore() : 0;
    }

    /**
     * Gets a player's username.
     * @param clientID the client ID
     * @return the player's username, or null if not found
     */
    public String getPlayerUsername(int clientID) {
        PlayerInfo player = players.get(clientID);
        return player != null ? player.getUsername() : null;
    }

    /**
     * Gets the leaderboard sorted by score (highest first)
     * @return list of players sorted by score descending
     */
    public List<PlayerInfo> getLeaderboard() {
        List<PlayerInfo> leaderboard = new ArrayList<>(players.values());

        // Sort by score descending
        leaderboard.sort((p1, p2) -> Integer.compare(p2.getScore(), p1.getScore()));

        return leaderboard;
    }

    /**
     * Gets the number of active players
     * @return number of players
     */
    public int getPlayerCount() {
        return players.size();
    }

    /**
     * Resets all player scores to zero.
     */
    public void resetScores() {
        for (PlayerInfo player : players.values()) {
            player.resetScore();
        }
    }

    /**
     * Inner class representing a player's information.
     */
    public static class PlayerInfo {
        private int clientID;
        private String username;
        private int score;

        /**
         * Creates a new player info object
         * @param clientID the client's unique ID
         * @param username the player's username
         */
        public PlayerInfo(int clientID, String username) {
            this.clientID = clientID;
            this.username = username;
            this.score = 0;
        }

        /**
         * Gets the client ID
         * @return the client ID
         */
        public int getClientID() {
            return clientID;
        }

        /**
         * Gets the username
         * @return the username
         */
        public String getUsername() {
            return username;
        }

        /**
         * Gets the current score
         * @return the score
         */
        public int getScore() {
            return score;
        }

        /**
         * Adds points to the player's score
         * @param points the points to add
         */
        public void addScore(int points) {
            score += points;
        }

        /**
         * Resets the score to zero.
         */
        public void resetScore() {
            score = 0;
        }
    }
}