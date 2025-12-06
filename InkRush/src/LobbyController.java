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

// Import for socket communication, object streams, and managing asynchronous tasks with an executor
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LobbyController {

    @FXML
    private Button joinButton;

    @FXML
    private TextField playerNameField;

    // Used for clientID tracking and host-only check
    private int clientID = -1;

    // Fields for managing a socket connection with the input/output streams,
    // connection status, and a single-threaded executor for asynchronous tasks
    private Socket connection;
    private ObjectOutputStream output;
    private ObjectInputStream input;
    private boolean connected = false;
    private ExecutorService executor = Executors.newSingleThreadExecutor();

    @FXML
    private void initialize() {
        // Allows the join button to be initially disabled
        joinButton.setDisable(true);
        connectToServer();
    }
    /**
     * Connects to the server as soon as the lobby loads.
     */
    // Creates and runs a background task that connects to the server, initializes object streams,
    // sets the connection status, and will start listening for server messages
    private void connectToServer()
    {
        Runnable connectionTask = new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    // Adjusts the host or port as needed
                    connection = new Socket("localhost", 23596);
                    output = new ObjectOutputStream(connection.getOutputStream());
                    output.flush();
                    input = new ObjectInputStream(connection.getInputStream());

                    connected = true;

                    listenForServerMessages();

                }
                catch (IOException e) {
                    e.printStackTrace();
                    Platform.runLater(() -> System.out.println("Error connecting to server: " + e.getMessage()));
                }
            }
        };
        executor.execute(connectionTask);
    }

    /**
     * Continuously listens for messages from the server.
     */
    private void listenForServerMessages()
    {
        Runnable listenTask = new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    while (connected)
                    {
                        Message message = (Message)input.readObject();
                    }
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                    connected = false;
                }

            }
        };
        executor.execute(listenTask);
    }


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