import javafx.fxml.FXML;
import javafx.application.Platform;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextArea;
import javafx.scene.paint.Color;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Controller for the InkRush game client (Game Screen).
 * Handles user input, server communication (TCP), and GUI updates.
 *
 * NETWORK COMMUNICATION:
 * - Connects to server using IP passed from Lobby
 * - Sends messages via ObjectOutputStream
 * - Receives messages via background thread with ObjectInputStream
 * - All GUI updates use Platform.runLater() for thread safety
 */
public class CanvasController {
    @FXML private Button chatButton;
    @FXML private TextArea chatTextArea;
    @FXML private TextField chatTextInput;
    @FXML private Button clearButton;
    @FXML private Canvas drawingCanvas;

    // The Label that displays the player's name (passed from Lobby)
    @FXML private Label nameLabel;

    @FXML private Button sendGuessButton;
    @FXML private Label wordLabel;

    // Connection settings
    private static final int SERVER_PORT = 23596;
    private String serverIP = "localhost"; // Default, overwritten by Lobby

    // Networking fields
    private Socket connection;
    private ObjectOutputStream output;
    private ObjectInputStream input;
    private ExecutorService executor;
    private String username = "Guest";
    private volatile boolean connected = false;

    // Drawing tracking variables
    private double lastX;
    private double lastY;
    private double remoteLastX;
    private double remoteLastY;
    private boolean remoteFirstPoint = true;
    private static final double BRUSH_SIZE = 4.0;
    private static final String BRUSH_COLOR = "#000000";

    /**
     * Sets the connection information (Name and IP) from the Lobby.
     * Automatically triggers the connection attempt.
     * * @param name The player's username
     * @param ip The IP address to connect to
     */
    public void setConnectionInfo(String name, String ip) {
        this.username = name;
        this.serverIP = ip;

        if (nameLabel != null) {
            nameLabel.setText(name);
        }

        // Start the connection attempt now that we have the IP
        connectToServer();
    }

    /**
     * Initializes the controller after FXML is loaded.
     * Sets up local drawing event handlers.
     */
    @FXML
    public void initialize() {
        executor = Executors.newFixedThreadPool(1);
        setupDrawing();
        // Note: connectToServer() is NOT called here anymore.
        // It is called by setConnectionInfo() after Lobby passes the data.
    }

    /**
     * Sets up drawing events for local canvas.
     * Broadcasts drawing data to the server if connected.
     */
    private void setupDrawing() {
        var gc = drawingCanvas.getGraphicsContext2D();
        gc.setLineWidth(BRUSH_SIZE);
        gc.setStroke(Color.BLACK);

        drawingCanvas.setOnMousePressed(event -> {
            lastX = event.getX();
            lastY = event.getY();
        });

        drawingCanvas.setOnMouseDragged(event -> {
            double x = event.getX();
            double y = event.getY();

            gc.strokeLine(lastX, lastY, x, y);

            if(connected) {
                Message drawMessage = Message.createDrawMessage(lastX, lastY, BRUSH_COLOR, BRUSH_SIZE);
                sendToServer(drawMessage);
            }

            lastX = x;
            lastY = y;
        });

        drawingCanvas.setOnMouseReleased(event -> {
            if(connected) {
                remoteFirstPoint = false;
            }
        });
    }

    /**
     * Connects to the server and starts listening for messages.
     * Runs connection in background thread to avoid blocking GUI.
     * Handles UnknownHostException and IOException gracefully.
     */
    private void connectToServer() {
        Runnable connectionTask = new Runnable() {
            @Override
            public void run() {
                try {
                    displayMessage("Attempting connection to " + serverIP + "...\n");

                    // Connect to server
                    connection = new Socket(serverIP, SERVER_PORT);

                    output = new ObjectOutputStream(connection.getOutputStream());
                    output.flush();
                    input = new ObjectInputStream(connection.getInputStream());

                    connected = true;
                    displayMessage("SUCCESS: Connected to " + serverIP + "\n");
                    displayMessage("Welcome, " + username + "!\n");

                    // Start listening for messages
                    processServerMessages();

                } catch (UnknownHostException e) {
                    // Graceful Error: Bad IP format
                    displayMessage("\n[ERROR] Could not find server: " + serverIP + "\n");
                    displayMessage("Please restart and check the IP address format.\n");

                } catch (IOException e) {
                    // Graceful Error: Server full, down, or blocked
                    displayMessage("\n[ERROR] Connection failed!\n");
                    displayMessage("Server response: " + e.getMessage() + "\n");

                    displayMessage("Possible causes:\n");
                    displayMessage("1. The Server is FULL (Game already started)\n");
                    displayMessage("2. The Server is not running on " + serverIP + "\n");
                    displayMessage("3. A Firewall is blocking the connection\n");
                }
            }
        };
        executor.execute(connectionTask);
    }

    /**
     * Continuously listens for messages from the server.
     * Runs in background thread - blocks at readObject() waiting for messages.
     */
    private void processServerMessages() {
        while (connected) {
            try {
                Message message = (Message) input.readObject();
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
     * @param message the message from the server
     */
    private void handleServerMessage(Message message) {
        String messageType = message.getMessageType();

        if (messageType.equals(Message.CONNECTED)) {
            int clientID = message.parseConnectedMessage();
            displayMessage("You are Client " + clientID + "\n");

        } else if (messageType.equals(Message.CHAT)) {
            Message.ChatData chatData = message.parseChatMessage();
            displayMessage(chatData.getUsername() + ": " + chatData.getMessage() + "\n");

        } else if (messageType.equals(Message.DRAW)) {
            Message.DrawData drawData = message.parseDrawMessage();
            drawRemotePoint(drawData);

        } else if (messageType.equals(Message.CLEAR)) {
            Platform.runLater(() -> {
                GraphicsContext gc = drawingCanvas.getGraphicsContext2D();
                gc.clearRect(0, 0, drawingCanvas.getWidth(), drawingCanvas.getHeight());
                remoteFirstPoint = false;
            });
            displayMessage("[Canvas cleared]\n");

        } else {
            displayMessage("[SERVER] " + message.toString() + "\n");
        }
    }

    /**
     * Draws a point received from another client
     * Thread safe, uses platform.runlater() for gui updates
     * @param drawData DrawData Message containing coordinates color, size
     */
    private void drawRemotePoint(Message.DrawData drawData) {
        Platform.runLater(() -> {
            GraphicsContext gc = drawingCanvas.getGraphicsContext2D();
            Color color = Color.web(drawData.getColor());
            gc.setStroke(color);
            gc.setLineWidth(drawData.getSize());

            double x = drawData.getX();
            double y = drawData.getY();

            double distance = Math.sqrt(Math.pow(x - remoteLastX, 2) + Math.pow(y - remoteLastY, 2));

            if(remoteFirstPoint || distance > 9) {
                gc.fillOval(x - drawData.getSize() / 2, y - drawData.getSize() / 2,
                        drawData.getSize(), drawData.getSize());
                remoteFirstPoint = false;
            } else {
                gc.strokeLine(remoteLastX, remoteLastY, x, y);
            }
            remoteLastX = x;
            remoteLastY = y;
        });
    }

    @FXML
    private void clearCanvas(){
        GraphicsContext gc = drawingCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, drawingCanvas.getWidth(), drawingCanvas.getHeight());
        remoteFirstPoint = true;

        if(connected) {
            Message clearMessage = Message.createClearMessage();
            sendToServer(clearMessage);
        }
    }

    /**
     * Sends a chat message to the server.
     */
    @FXML
    private void sendServerChat() {
        String message = chatTextInput.getText().trim();
        if (message.isEmpty()) return;
        if (!connected) {
            displayMessage("Not connected to server!\n");
            return;
        }
        Message chatMessage = Message.createChatMessage(username, message);
        sendToServer(chatMessage);
        chatTextInput.clear();
    }

    /**
     * Sends a message to the server.
     * Thread-safe method that can be called from any thread.
     * @param message the message to send
     */
    private void sendToServer(Message message) {
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
                sendToServer(Message.createTerminateMessage());
                output.close();
            }
            if (input != null) input.close();
            if (connection != null) connection.close();
            if (executor != null) executor.shutdownNow();
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }
}