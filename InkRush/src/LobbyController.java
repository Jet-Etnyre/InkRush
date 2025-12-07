import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.scene.media.AudioClip; // Import for sound effects
import javafx.scene.media.Media;     // Import for music
import javafx.scene.media.MediaPlayer; // Import for music player

import java.io.IOException;

/**
 * Controller for the Lobby screen.
 * Handles user input for Username and Server IP address.
 * Validates input and transitions to the Game (Canvas) screen.
 */
public class LobbyController {

    @FXML
    private Button joinButton;

    @FXML
    private TextField playerNameField;

    @FXML
    private TextField ipAddressField;

    @FXML
    private ListView<String> playersListView;

    /**
     * Initializes the controller class.
     * Sets default values and prepares the UI.
     */
    @FXML
    public void initialize() {
        // Set a default IP for easier testing, or leave empty
        if (ipAddressField.getText().isEmpty()) {
            ipAddressField.setText("localhost");
        }

        // Ensure button is enabled so users can attempt to join
        joinButton.setDisable(false);
    }

    /**
     * Handles the "Join Game" button click.
     * Validates input, loads the game screen, passes connection info, and switches scenes.
     */
    @FXML
    public void onJoinClicked() {
        try {
            String username = playerNameField.getText().trim();
            String serverIP = ipAddressField.getText().trim();

            // 1. Basic Validation
            if (username.isEmpty()) {
                System.out.println("Please enter a name!");
                return;
            }
            if (serverIP.isEmpty()) {
                System.out.println("Please enter an IP address!");
                return;
            }

            // 2. Load the Game (Canvas) FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("Canvas.fxml"));
            Parent gameRoot = loader.load();

            // 3. Get the controller and pass the Connection Info
            CanvasController gameController = loader.getController();
            gameController.setConnectionInfo(username, serverIP);

            // 4. Switch the Scene
            Stage stage = (Stage) joinButton.getScene().getWindow();

            // Ensure the game disconnects from server when the window is closed
            stage.setOnCloseRequest(new EventHandler<WindowEvent>() {
                @Override
                public void handle(WindowEvent event) {
                    gameController.disconnect();
                }
            });

            stage.setScene(new Scene(gameRoot));
            stage.setTitle("InkRush - Game: " + username);

        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Error loading game screen: " + e.getMessage());
        }
    }
}