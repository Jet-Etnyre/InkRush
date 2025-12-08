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

    // Store canvas dimensions for performance
    private double width;
    private double height;

    private class Confetti {
        double x, y;
        double vx, vy;
        Color color;
        double size;
        double rotation;
        double rotationSpeed;

        Confetti(double startX, double startY) {
            this.x = startX;
            this.y = startY;
            // Spread X velocity slightly more
            this.vx = (random.nextDouble() - 0.5) * 6;
            this.vy = random.nextDouble() * 3 + 2;
            this.size = random.nextDouble() * 8 + 4;
            this.rotation = random.nextDouble() * 360;
            this.rotationSpeed = (random.nextDouble() - 0.5) * 10;

            Color[] colors = {
                Color.web("#ffb84d"), Color.web("#1E90FF"), Color.web("#FFD700"),
                Color.web("#FF69B4"), Color.web("#00FA9A"), Color.web("#FF6347")
            };
            this.color = colors[random.nextInt(colors.length)];
        }

        void update() {
            x += vx;
            y += vy;
            rotation += rotationSpeed;
            vy += 0.1; // Slight gravity
        }

        // Dynamic check based on current screen size
        boolean isOffScreen() {
            return y > height || x < -50 || x > width + 50;
        }
    }

    @FXML
    public void initialize() {
        // 1. Bind Canvas to the parent container so it resizes with the window
        if (confettiCanvas.getParent() instanceof javafx.scene.layout.Region) {
            javafx.scene.layout.Region parent = (javafx.scene.layout.Region) confettiCanvas.getParent();
            confettiCanvas.widthProperty().bind(parent.widthProperty());
            confettiCanvas.heightProperty().bind(parent.heightProperty());
        }

        // 2. Initialize dimensions
        width = confettiCanvas.getWidth();
        height = confettiCanvas.getHeight();

        // 3. Update dimensions if they change
        confettiCanvas.widthProperty().addListener((obs, oldVal, newVal) -> width = newVal.doubleValue());
        confettiCanvas.heightProperty().addListener((obs, oldVal, newVal) -> height = newVal.doubleValue());

        startConfetti();
    }

    private void startConfetti() {
        GraphicsContext gc = confettiCanvas.getGraphicsContext2D();

        // Initial burst
        for (int i = 0; i < 100; i++) {
            confettiList.add(new Confetti(random.nextDouble() * width, -random.nextDouble() * 200));
        }

        confettiTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                gc.clearRect(0, 0, width, height);

                for (int i = confettiList.size() - 1; i >= 0; i--) {
                    Confetti c = confettiList.get(i);
                    c.update();

                    if (c.isOffScreen()) {
                        confettiList.remove(i);
                        confettiList.add(new Confetti(random.nextDouble() * width, -20));
                    } else {
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

    public void setLeaderboardData(String leaderboardData) {
        try {
            if (leaderboardData == null) return;

            String[] parts = leaderboardData.split(":");

            // Winner (1st place) - FIXED: Now properly shows their medal
            if (parts.length >= 2) {
                String winnerName = parts[0];
                String winnerScore = parts[1];

                // Update winner display at the top
                winnerLabel.setText(winnerName);

                // Update first place in leaderboard WITH the gold medal icon
                place1Icon.setText("🥇");
                place1Name.setText(winnerName);
                place1Score.setText(winnerScore);
            }

            // 2nd place
            if (parts.length >= 4) {
                place2Icon.setText("🥈");
                place2Name.setText(parts[2]);
                place2Score.setText(parts[3]);
            } else {
                place2Icon.setText("");
                place2Name.setText("---");
                place2Score.setText("---");
            }

            // 3rd place
            if (parts.length >= 6) {
                place3Icon.setText("🥉");
                place3Name.setText(parts[4]);
                place3Score.setText(parts[5]);
            } else {
                place3Icon.setText("");
                place3Name.setText("---");
                place3Score.setText("---");
            }

        } catch (Exception e) {
            System.err.println("Leaderboard Error: " + e.getMessage());
        }
    }

    public void startCountdown() {
        new Thread(() -> {
            while (countdown > 0) {
                final int current = countdown;
                Platform.runLater(() -> countdownLabel.setText("Returning to game in " + current + "s..."));
                try { Thread.sleep(1000); } catch (InterruptedException e) { break; }
                countdown--;
            }
            Platform.runLater(this::closeGame);
        }).start();
    }

    @FXML
    private void onRestartClicked() {
        if (confettiTimer != null) confettiTimer.stop();
        closeGame();
    }

    @FXML
    private void onCloseClicked() {
        if (confettiTimer != null) confettiTimer.stop();
        Platform.runLater(() -> {
            Stage stage = (Stage) closeButton.getScene().getWindow();
            stage.close();
            Platform.exit();
            System.exit(0);
        });
    }

    private void closeGame() {
        if (confettiTimer != null) confettiTimer.stop();
        Platform.runLater(() -> {
            Stage stage = (Stage) closeButton.getScene().getWindow();
            if(stage != null) stage.close();
        });
    }
}