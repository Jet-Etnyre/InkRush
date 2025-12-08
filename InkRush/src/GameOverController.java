import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Controller for the Game Over screen with confetti animation!
 */
public class GameOverController {

    @FXML private Canvas confettiCanvas;

    @FXML private Label winnerLabel;

    @FXML private Label place1Icon;
    @FXML private Label place1Name;
    @FXML private Label place1Score;

    @FXML private Label place2Icon;
    @FXML private Label place2Name;
    @FXML private Label place2Score;

    @FXML private Label place3Icon;
    @FXML private Label place3Name;
    @FXML private Label place3Score;

    @FXML private Label countdownLabel;
    @FXML private Button restartButton;
    @FXML private Button closeButton;

    private int countdown = 10;
    private List<Confetti> confettiList = new ArrayList<>();
    private AnimationTimer confettiTimer;
    private Random random = new Random();

    /**
     * Confetti particle class
     */
    private class Confetti {
        double x, y;
        double vx, vy;
        Color color;
        double size;
        double rotation;
        double rotationSpeed;

        Confetti(double x, double y) {
            this.x = x;
            this.y = y;
            this.vx = (random.nextDouble() - 0.5) * 4;
            this.vy = random.nextDouble() * 3 + 2;
            this.size = random.nextDouble() * 8 + 4;
            this.rotation = random.nextDouble() * 360;
            this.rotationSpeed = (random.nextDouble() - 0.5) * 10;

            // Random bright colors
            Color[] colors = {
                Color.web("#ffb84d"),
                Color.web("#1E90FF"),
                Color.web("#FFD700"),
                Color.web("#FF69B4"),
                Color.web("#00FA9A"),
                Color.web("#FF6347")
            };
            this.color = colors[random.nextInt(colors.length)];
        }

        void update() {
            x += vx;
            y += vy;
            rotation += rotationSpeed;
            vy += 0.15; // gravity
        }

        boolean isOffScreen() {
            return y > 600 || x < -20 || x > 520;
        }
    }

    @FXML
    public void initialize() {
        // Start confetti animation
        startConfetti();
    }

    /**
     * Starts the confetti animation
     */
    private void startConfetti() {
        GraphicsContext gc = confettiCanvas.getGraphicsContext2D();

        // Generate initial confetti
        for (int i = 0; i < 100; i++) {
            confettiList.add(new Confetti(random.nextDouble() * 500, -random.nextDouble() * 200));
        }

        confettiTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                // Clear canvas
                gc.clearRect(0, 0, 500, 600);

                // Update and draw confetti
                for (int i = confettiList.size() - 1; i >= 0; i--) {
                    Confetti c = confettiList.get(i);
                    c.update();

                    // Remove if off screen
                    if (c.isOffScreen()) {
                        confettiList.remove(i);
                        // Add new one at top
                        confettiList.add(new Confetti(random.nextDouble() * 500, -10));
                    } else {
                        // Draw confetti piece
                        gc.save();
                        gc.translate(c.x, c.y);
                        gc.rotate(c.rotation);
                        gc.setFill(c.color);
                        gc.fillRect(-c.size/2, -c.size/2, c.size, c.size);
                        gc.restore();
                    }
                }
            }
        };

        confettiTimer.start();
    }

    /**
     * Sets the leaderboard data
     */
    public void setLeaderboardData(String leaderboardData) {
        try {
            String[] parts = leaderboardData.split(":");

            if (parts.length >= 2) {
                winnerLabel.setText("🏆 WINNER: " + parts[0] + " 🏆");
                place1Name.setText(parts[0]);
                place1Score.setText(parts[1] + " pts");
            }

            if (parts.length >= 4) {
                place2Name.setText(parts[2]);
                place2Score.setText(parts[3] + " pts");
            } else {
                place2Name.setText("---");
                place2Score.setText("---");
            }

            if (parts.length >= 6) {
                place3Name.setText(parts[4]);
                place3Score.setText(parts[5] + " pts");
            } else {
                place3Name.setText("---");
                place3Score.setText("---");
            }

        } catch (Exception e) {
            System.err.println("[ERROR] Failed to parse leaderboard: " + e.getMessage());
        }
    }

    /**
     * Starts countdown
     */
    public void startCountdown() {
        new Thread(() -> {
            while (countdown > 0) {
                final int current = countdown;
                Platform.runLater(() -> countdownLabel.setText("Closing in " + current + " seconds..."));

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    break;
                }
                countdown--;
            }

            Platform.runLater(() -> closeGame());
        }).start();
    }

    @FXML
    private void onRestartClicked() {
        // Stop confetti
        if (confettiTimer != null) confettiTimer.stop();

        // Send restart message to server
        System.out.println("[GAME] Restart clicked!");
        // TODO: Send RESTART message to server

        Stage stage = (Stage) restartButton.getScene().getWindow();
        stage.close();
    }

    @FXML
    private void onCloseClicked() {
        closeGame();
    }

    private void closeGame() {
        if (confettiTimer != null) confettiTimer.stop();

        Stage stage = (Stage) closeButton.getScene().getWindow();
        stage.close();
        System.exit(0);
    }
}