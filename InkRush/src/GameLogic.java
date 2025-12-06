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


}
