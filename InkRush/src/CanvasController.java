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

    // --- INTEGRATION CHANGE START ---
    // Replaced playerNameField/nameButton with nameLabel
    @FXML
    private Label nameLabel;
    // --- INTEGRATION CHANGE END ---

    @FXML
    private Button sendGuessButton;

    @FXML
    private Label wordLabel;

    private static final String SERVER_HOST = "localhost"; // fix how to connect to the server, right now it's by writing out server's IP address
    private static final int SERVER_PORT = 23596;

    private Socket connection;
    private ObjectOutputStream output;
    private ObjectInputStream input;
    private ExecutorService executor;
    private String username = "Guest";
    private volatile boolean connected = false;

    //Track last point for both local and remote drawing
    private double lastX;
    private double lastY;
    private double remoteLastX;
    private double remoteLastY;
    private boolean remoteFirstPoint = true;

    // TODO Make logic to change between drawing styles (hardcoded for now)
    private static final double BRUSH_SIZE = 4.0;
    private static final String BRUSH_COLOR = "#000000"; // Black

    /**
     * Sets the player name from the Lobby.
     * This restores the connection between Lobby and Game.
     */
    public void setPlayerName(String name) {
        this.username = name;
        if (nameLabel != null) {
            nameLabel.setText(name);
        }
        displayMessage("Welcome, " + username + "!\n");
    }


    /**
     * Initializes the controller after FXML is loaded.
     * Sets up event handlers and connects to server.
     */
    @FXML
    public void initialize() {
        executor = Executors.newFixedThreadPool(1);
        setupDrawing();
        connectToServer();
    }

    /**
     * Sets up drawing for local drawing and broadcasts drawing data to server
     */
    private void setupDrawing() {
        var gc = drawingCanvas.getGraphicsContext2D();
        gc.setLineWidth(BRUSH_SIZE);
        gc.setStroke(Color.BLACK);

        // When mouse is pressed
        drawingCanvas.setOnMousePressed(event -> {
            lastX = event.getX();
            lastY = event.getY();
        });

        // When mouse is dragged so moving while clicking
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
            // Reset remote drawing tracking when local drawing stops
            if(connected) {
                remoteFirstPoint = false;
            }
        });
    }


    @FXML
    private void clearCanvas(){
        GraphicsContext gc = drawingCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, drawingCanvas.getWidth(), drawingCanvas.getHeight());

        //Reset remote drawing state
        remoteFirstPoint = true;

        if(connected) {
            Message clearMessage = Message.createClearMessage();
            sendToServer(clearMessage);
        }
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
                Message message = (Message) input.readObject();

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
    private void handleServerMessage(Message message) {
        String messageType = message.getMessageType();

        if (messageType.equals(Message.CONNECTED)) {
            // Server confirms connection with client ID
            int clientID = message.parseConnectedMessage();
            displayMessage("You are Client " + clientID + "\n");

        } else if (messageType.equals(Message.CHAT)) {
            // Chat message format: CHAT:username:message
            Message.ChatData chatData = message.parseChatMessage();
            String user = chatData.getUsername();
            String chatMessage = chatData.getMessage();
            displayMessage(user + ": " + chatMessage + "\n");

        } else if (messageType.equals(Message.DRAW)) {
            // TODO: Handle drawing messages
            // Format: DRAW:x,y,color,size
            Message.DrawData drawData = message.parseDrawMessage();
            drawRemotePoint(drawData);

        } else if (messageType.equals(Message.CLEAR)) {
            //Clear canvas when receiving clear from server
            Platform.runLater(() -> {
                GraphicsContext gc = drawingCanvas.getGraphicsContext2D();
                gc.clearRect(0, 0, drawingCanvas.getWidth(), drawingCanvas.getHeight());
                remoteFirstPoint = false;
            });
            displayMessage("[Canvas cleared]\n");

        } else {
            // Unknown message type - just display it
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

            // Parse color
            Color color = Color.web(drawData.getColor());
            gc.setStroke(color);
            gc.setLineWidth(drawData.getSize());

            double x = drawData.getX();
            double y = drawData.getY();

            //Calculate distance from last point
            double distance = Math.sqrt(Math.pow(x - remoteLastX, 2) + Math.pow(y - remoteLastY, 2));

            //If distance is too large (pen lifted) or first point, draw a dot
            //Threshold of 50 pixels

            if(remoteFirstPoint || distance > 9) {
                //First point - just draw a dot
                gc.fillOval(x - drawData.getSize() / 2, y - drawData.getSize() / 2,
                        drawData.getSize(), drawData.getSize());
                remoteFirstPoint = false;
            }else{
                gc.strokeLine(remoteLastX, remoteLastY, x, y);
            }

            remoteLastX = x;
            remoteLastY = y;
        });
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
        Message chatMessage = Message.createChatMessage(username, message);
        sendToServer(chatMessage);

        // Clear input field
        chatTextInput.clear();
    }

    /**
     * Sends a message to the server.
     * Thread-safe method that can be called from any thread.
     *
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
                sendToServer(Message.createTerminateMessage());
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