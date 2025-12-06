import javafx.fxml.FXML;
import javafx.application.Platform;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextArea;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Controller for the InkRush game client.
 * Handles user input, server communication, and GUI updates.
 *
 * NETWORK COMMUNICATION:
 * - Connects to server on initialization
 * - Sends messages via ObjectOutputStream
 * - Receives messages via background thread with ObjectInputStream
 * - All GUI updates use Platform.runLater() for thread safety
 */
public class CanvasController {
    @FXML
    private Button chatButton;

    @FXML
    private TextArea chatTextArea;

    @FXML
    private TextField chatTextInput;

    @FXML
    private Button clearButton;

    @FXML
    private Canvas drawingCanvas;

    @FXML
    private Button nameButton;

    @FXML
    private TextField playerNameField;

    @FXML
    private Button sendGuessButton;

    @FXML
    private Label wordLabel;

    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 23596;

    private Socket connection;
    private ObjectOutputStream output;
    private ObjectInputStream input;
    private ExecutorService executor;
    private String username = "Guest";
    private volatile boolean connected = false;
    private double lastX;
    private double lastY;


    /**
     * Initializes the controller after FXML is loaded.
     * Sets up event handlers and connects to server.
     */
    @FXML
    public void initialize() {
        executor = Executors.newFixedThreadPool(1);
        setupLocalDrawing();
        connectToServer();
    }

    private void setupLocalDrawing() {
        var gc = drawingCanvas.getGraphicsContext2D();

        // When mouse is pressed
        drawingCanvas.setOnMousePressed(event -> {
            lastX = event.getX();
            lastY = event.getY();
        });

        // When mouse is dragged so moving while clicking
        drawingCanvas.setOnMouseDragged(event -> {
            double x = event.getX();
            double y = event.getY();
            // brush size
            gc.setLineWidth(4);
            gc.strokeLine(lastX, lastY, x, y);

            lastX = x;
            lastY = y;
        });
        drawingCanvas.setOnMouseReleased(event -> {});
    }

    /**
     * Connects to the server and starts listening for messages.
     * Runs connection in background thread to avoid blocking GUI.
     */
    private void connectToServer() {
        Runnable connectionTask = new Runnable() {
            @Override
            public void run() {
                try {
                    // Connect to server
                    connection = new Socket(SERVER_HOST, SERVER_PORT);
                    displayMessage("Connected to server at " + SERVER_HOST + ":" + SERVER_PORT + "\n");

                    // Set up streams
                    output = new ObjectOutputStream(connection.getOutputStream());
                    output.flush();

                    input = new ObjectInputStream(connection.getInputStream());

                    connected = true;
                    displayMessage("Ready to play!\n");

                    // Start listening for messages from server
                    processServerMessages();

                } catch (IOException ioException) {
                    displayMessage("Error connecting to server: " + ioException.getMessage() + "\n");
                    ioException.printStackTrace();
                }
            }
        };

        executor.execute(connectionTask);
    }

    /**
     * Continuously listens for messages from the server.
     * Runs in background thread - blocks at readObject() waiting for messages.
     *
     * MESSAGE TYPES HANDLED:
     * - CONNECTED:id -> Server confirms connection
     * - CHAT:username:message -> Display chat message
     * - DRAW:x,y,color,size -> Draw point on canvas
     * - CLEAR -> Clear canvas
     */
    private void processServerMessages() {
        while (connected) {
            try {
                // Blocks here waiting for server message
                String message = (String) input.readObject();

                // Handle different message types
                handleServerMessage(message);

            } catch (IOException ioException) {
                if (connected) {
                    displayMessage("Lost connection to server\n");
                    connected = false;
                }
                break;
            } catch (ClassNotFoundException classNotFoundException) {
                displayMessage("Received unknown message type\n");
            }
        }
    }

    /**
     * Handles messages received from the server.
     * Routes messages based on their type prefix.
     *
     * @param message the message from the server
     */
    private void handleServerMessage(String message) {
        if (message.startsWith("CONNECTED:")) {
            // Server confirms connection with client ID
            String[] parts = message.split(":");
            if (parts.length >= 2) {
                String clientID = parts[1];
                displayMessage("You are Client " + clientID + "\n");
            }

        } else if (message.startsWith("CHAT:")) {
            // Chat message format: CHAT:username:message
            String[] parts = message.split(":", 3);
            if (parts.length >= 3) {
                String user = parts[1];
                String chatMessage = parts[2];
                displayMessage(user + ": " + chatMessage + "\n");
            } else {
                displayMessage(message + "\n");
            }

        } else if (message.startsWith("DRAW:")) {
            // TODO: Handle drawing messages
            // Format: DRAW:x,y,color,size
            displayMessage("[Drawing received]\n");

        } else if (message.equals("CLEAR")) {
            // TODO: Clear canvas
            displayMessage("[Canvas cleared]\n");

        } else {
            // Unknown message type - just display it
            displayMessage("[SERVER] " + message + "\n");
        }
    }

    /**
     * Sets the username for this player.
     * Called when nameButton is clicked.
     * Updates local username and notifies server.
     */
    @FXML
    private void setUsername() {
        String newUsername = playerNameField.getText().trim();

        if (newUsername.isEmpty()) {
            displayMessage("Username cannot be empty!\n");
            return;
        }

        username = newUsername;
        displayMessage("Username set to: " + username + "\n");

        // Disable name field after setting username
        playerNameField.setEditable(false);
        nameButton.setDisable(true);

    }

    /**
     * Sends a chat message to the server.
     * Called when chatButton is clicked or Enter is pressed in chatTextInput.
     * Message is broadcast to all connected clients by the server.
     */
    @FXML
    private void sendServerChat() {
        String message = chatTextInput.getText().trim();

        if (message.isEmpty()) {
            return;
        }

        if (!connected) {
            displayMessage("Not connected to server!\n");
            return;
        }

        // Format: CHAT:username:message
        String formattedMessage = "CHAT:" + username + ":" + message;
        sendToServer(formattedMessage);

        // Clear input field
        chatTextInput.clear();
    }

    /**
     * Sends a message to the server.
     * Thread-safe method that can be called from any thread.
     *
     * @param message the message to send
     */
    private void sendToServer(String message) {
        if (!connected || output == null) {
            displayMessage("Cannot send - not connected to server\n");
            return;
        }

        try {
            synchronized (output) {
                output.writeObject(message);
                output.flush();
            }
        } catch (IOException ioException) {
            displayMessage("Error sending message: " + ioException.getMessage() + "\n");
        }
    }

    /**
     * Displays a message in the chat text area.
     * Thread-safe - uses Platform.runLater() for GUI updates.
     *
     * @param message the message to display
     */
    private void displayMessage(String message) {
        Platform.runLater(() -> {
            chatTextArea.appendText(message);
        });
    }

    /**
     * Closes the connection to the server.
     * Should be called when application is closing.
     */
    public void disconnect() {
        connected = false;

        try {
            if (output != null) {
                sendToServer("TERMINATE");
                output.close();
            }
            if (input != null) {
                input.close();
            }
            if (connection != null) {
                connection.close();
            }
            if (executor != null) {
                executor.shutdownNow();
            }
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }
}
