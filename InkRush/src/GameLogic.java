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
    private static final int[] = GUESS_POINTS = {200, 150, 100, 50}; // the 1st, 2nd, 3rd, and 4th guesser
    private static final int POINTS_DRAWER_BONUS = 50; // drawer gets points when someone guesses

    private String currentWord;
    private int currentDrawerID;
    private long roundStartTime;
    private boolean roundActive;
    private Random random;

    // player tracking
}
