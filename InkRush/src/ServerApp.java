import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * Main application class for InkRush game server.
 */
public class ServerApp extends Application {
    /**
     * Entry point of application
     * @param args
     */
    public static void main(String[] args) {
       launch(args);
    }

    /**
     * Starts the JavaFX application and displays the server window
     * @param stage Stage for the application
     * @throws Exception thrown if fxml cannot be loaded
     */
    @Override
    public void start(Stage stage) throws Exception {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("Server.fxml"));
            Parent root = loader.load();

            ServerController controller = loader.getController();

            Scene scene = new Scene(root);
            stage.setTitle("InkRush Server");
            stage.setScene(scene);
            stage.show();

            controller.runServer();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Handles application shutdown.
     * Ensures server resources are properly closed.
     */
    @Override
    public void stop(){
        System.exit(0);
    }
}

