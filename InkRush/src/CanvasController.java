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
import java.net.InetSocketAddress;

import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


import javafx.scene.control.Alert;
import javafx.stage.Stage;
import javafx.application.Platform;

/**
 * Controller for the InkRush game client.
 * Handles user input, server communication, and GUI updates.
 *
 * NETWORK COMMUNICATION:
 * - Connects to server on initialization using dynamic IP from Lobby
 * - Sends messages via ObjectOutputStream
 * - Receives messages via background thread with ObjectInputStream
 * - All GUI updates use Platform.runLater() for thread safety
 *
 * ERROR HANDLING:
 * - Implements graceful failure for connection timeouts (e.g., Server Full).
 * - Handles UnknownHostException for invalid IP addresses.
 */
public class CanvasController {
    @FXML private Button chatButton;
    @FXML private TextArea chatTextArea;
    @FXML private TextField chatTextInput;
    @FXML private Button clearButton;
    @FXML private Canvas drawingCanvas;

    @FXML private Label nameLabel; // Matches teammate's file
    @FXML private Label wordLabel;

    //Use dynamic IP logic
    private static final int SERVER_PORT = 23596;
    private String serverIP = "localhost"; // Default, overwritten by Lobby

    private Socket connection;
    private ObjectOutputStream output;
    private ObjectInputStream input;
    private ExecutorService executor;
    private String username = "Guest";
    private volatile boolean connected = false;

    // Drawing tracking variables (Teammate's logic)
    private double lastX;
    private double lastY;
    private double remoteLastX;
    private double remoteLastY;
    private boolean remoteFirstPoint = true;

    // Hardcoded brush styles for now
    private static final double BRUSH_SIZE = 4.0;
    private static final String BRUSH_COLOR = "#000000";

    /**
     * Sets the connection information (Name and IP) from the Lobby.
     * Automatically triggers the connection attempt.
     * @param name The player's username
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
     * Sets up event handlers for drawing.
     */
    @FXML
    public void initialize() {
        executor = Executors.newFixedThreadPool(1);
        setupDrawing();
    }

    /**
     * Sets up drawing for local drawing and broadcasts drawing data to server.
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

    /**
     * Clears the drawing canvas and resets the drawing related state.
     * This method will remove all graphics from the canvas, and resets local and
     * remote coordinate tracking, and notifies the server to clear the
     * canvas for all connected players if the client is connected.
     */
    @FXML
    private void clearCanvas() {
        // Get graphics context and clear the entire canvas
        GraphicsContext gc = drawingCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, drawingCanvas.getWidth(), drawingCanvas.getHeight());

        // Reset drawing tracking variables
        lastX = 0;
        lastY = 0;
        remoteFirstPoint = true;   // So next remote drawing starts fresh
        remoteLastX = 0;
        remoteLastY = 0;

        // If connected, tell server to clear canvas for all players
        if (connected) {
            Message clearMessage = Message.createClearMessage(); // Make sure your Message class has this
            sendToServer(clearMessage);
        }
    }


    /**
     * Connects to the server and starts listening for messages.
     * Runs connection in background thread to avoid blocking GUI.
     * Uses a timeout to detect if the server is full or unreachable.
     */
    private void connectToServer() {
        Runnable connectionTask = new Runnable() {
            @Override
            public void run() {
                try {
                    displayMessage("Attempting connection to " + serverIP + "...\n");

                    // Create an unconnected socket
                    connection = new Socket();

                    // 1. CONNECTION TIMEOUT (3s): Fails if Server is down/unreachable
                    connection.connect(new InetSocketAddress(serverIP, SERVER_PORT), 3000);

                    // 2. READ TIMEOUT (2s): Fixes the "Hanging" issue if server is full
                    connection.setSoTimeout(2000);

                    output = new ObjectOutputStream(connection.getOutputStream());
                    output.flush();

                    // This throws SocketTimeoutException if server accepts but ignores us (Full)
                    input = new ObjectInputStream(connection.getInputStream());

                    // 3. RESET TIMEOUT: Once connected, we wait forever for messages
                    connection.setSoTimeout(0);

                    connected = true;
                    displayMessage("SUCCESS: Connected to " + serverIP + "\n");
                    displayMessage("Welcome, " + username + "!\n");

                    // Start listening for messages from server
                    processServerMessages();

                } catch (UnknownHostException e) {
                    closeSocketOnError();
                    displayMessage("\n[ERROR] Invalid Host: " + serverIP + "\n");
                    displayMessage("Please check the IP address format.\n");

                } catch (SocketTimeoutException e) {
                    // 1. Clean up the internal socket first
                    closeSocketOnError();

                    // 2. Show the popup and close the window
                    closeWindowOnError(
                            "Connection Timed Out",
                            "The server did not respond in time (3s).\n" +
                                    "Likely cause: The Server is FULL or not running."
                    );

                } catch (IOException e) {
                    // Catch other IO errors (like Connection Refused)
                    closeSocketOnError();
                    displayMessage("\n[ERROR] Connection failed!\n");
                    displayMessage("Server response: " + e.getMessage() + "\n");
                }
            }
        };

        executor.execute(connectionTask);
    }

    /**
     * Helper to force-close the socket if connection fails.
     * This ensures we don't leave half-open resources hanging.
     */
    private void closeSocketOnError() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (IOException e) {
            // Ignored because we are already handling an error
        }
    }

    /**
     * Shows an error popup and then force-closes the application window.
     * Uses Platform.runLater with an anonymous inner class.
     */
    private void closeWindowOnError(final String header, final String content) {
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                // 1. Create a popup Alert
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Connection Error");
                alert.setHeaderText(header);
                alert.setContentText(content);

                // 2. Wait for the user to click "OK"
                alert.showAndWait();

                // 3. Get the current window (Stage) and close it
                if (chatTextArea.getScene() != null) {
                    Stage stage = (Stage) chatTextArea.getScene().getWindow();
                    stage.close();
                }

                // 4. Ensure background threads are killed
                disconnect();
            }
        });
    }
    /**
     * Continuously listens for messages from the server.
     * Runs in background thread - blocks at readObject() waiting for messages.
     *
     * All message types to be handled are in Message
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
            //Threshold of 9 pixels
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