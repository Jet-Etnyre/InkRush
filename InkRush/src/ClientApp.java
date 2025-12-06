import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Main application class for the InkRush game client.
 * Launches the Lobby Screen.
 */
public class ClientApp extends Application {

    /**
     * Main entry point for the application
     * @param args command line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }

    /**
     * Starts the JavaFX application and displays the Lobby window.
     */
    @Override
    public void start(Stage stage) throws Exception {
        // Load the Lobby FXML
        FXMLLoader loader = new FXMLLoader(getClass().getResource("Lobby.fxml"));
        Parent root = loader.load();

        // We don't need to get the controller here anymore because
        // ClientApp doesn't need to manage the Lobby's internal logic.

        Scene scene = new Scene(root);
        stage.setTitle("InkRush - Lobby");
        stage.setScene(scene);
        stage.show();
    }

    /**
     * Handles application shutdown.
     */
    @Override
    public void stop() {
        // System.exit(0) ensures all background threads (like the server listener)
        // are killed when the window closes.
        System.exit(0);
    }
}