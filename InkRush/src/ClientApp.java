import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Main application class for the InkRush game client.
 * Launches the JavaFX client GUI and connects to the game server.
 */
public class ClientApp extends Application {

    private CanvasController controller;

    /**
     * Main entry point for the application
     * @param args command line arguments (not used)
     */
    public static void main(String[] args) {
        launch(args);
    }

    /**
     * Starts the JavaFX application and displays the client window.
     *
     * @param stage the primary stage for this application
     * @throws Exception if FXML file cannot be loaded
     */
    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("Canvas.fxml"));
        Parent root = loader.load();

        // Get controller reference for cleanup on close
        controller = loader.getController();

        Scene scene = new Scene(root);
        stage.setTitle("InkRush - Draw & Guess");
        stage.setScene(scene);

        // Handle window close event
        stage.setOnCloseRequest(event -> {
            if (controller != null) {
                controller.disconnect();
            }
        });

        stage.show();
    }

    /**
     * Handles application shutdown.
     * Ensures clean disconnect from server.
     */
    @Override
    public void stop() {
        if (controller != null) {
            controller.disconnect();
        }
    }
}
