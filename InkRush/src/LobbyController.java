import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import java.io.IOException;

public class LobbyController {

    @FXML
    private Button joinButton;

    @FXML
    private TextField playerNameField;

    // Used for clientID tracking and host-only check
    private int clientID = -1;

    @FXML
    public void onJoinClicked() {
        // Only host can start game
        if (clientID != 1) {
            System.out.println("Only the host can start the game.");
            return;
        }

        try {
            String username = playerNameField.getText().trim();

            if (username.isEmpty()) {
                System.out.println("Please enter a name!");
                return;
            }

            FXMLLoader loader = new FXMLLoader(getClass().getResource("Canvas.fxml"));
            Parent gameRoot = loader.load();

            // Get the controller and set the data
            final CanvasController gameController = loader.getController();
            gameController.setPlayerName(username);

            Stage stage = (Stage) joinButton.getScene().getWindow();

            // ANONYMOUS CLASS (No Lambda)
            stage.setOnCloseRequest(new EventHandler<WindowEvent>() {
                @Override
                public void handle(WindowEvent event) {
                    gameController.disconnect();
                }
            });

            stage.setScene(new Scene(gameRoot));
            stage.setTitle("InkRush - Game: " + username);

        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }
}