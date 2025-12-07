import javafx.fxml.FXML;
import javafx.application.Platform;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
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

//using Interpolation to solve the losing packet while drawing issue
import javafx.animation.AnimationTimer;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

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
    private Label nameLabel; // Matches teammate's file
    @FXML
    private Label wordLabel;

    // NEW FXML controls (must match your FXML fx:id names)
    @FXML private ColorPicker colorPicker;
    @FXML private Slider sizeSlider;
    @FXML private Label sizeValueLabel;

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
    private volatile boolean canDraw = false;

    // Dynamic brush state (replaces hardcoded constants)
    private Color currentColor = Color.BLACK;
    private double currentBrushSize = 4.0;

    // Animation Queue: Stores points to be drawn smoothly
    private Queue<Message.DrawData> pointQueue = new ConcurrentLinkedQueue<>();
    private AnimationTimer drawingLoop;

    // pointer tracer
    private double currentAnimX;
    private double currentAnimY;

    // How fast the animation catches up
    private static final double SMOOTHING_SPEED = 1;


    /**
     * Sets the connection information (Name and IP) from the Lobby.
     * Automatically triggers the connection attempt.
     *
     * @param name The player's username
     * @param ip   The IP address to connect to
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
     * Sets up event handlers for drawing and UI controls.
     */
    @FXML
    public void initialize() {
        executor = Executors.newFixedThreadPool(1);

        // Initialize color + size UI state (if controls are present)
        if (colorPicker != null) {
            colorPicker.setValue(currentColor);
            colorPicker.setOnAction(e -> changeColor());
        }

        if (sizeSlider != null) {
            sizeSlider.setMin(1);
            sizeSlider.setMax(20);
            sizeSlider.setValue(currentBrushSize);
            sizeSlider.valueProperty().addListener((obs, oldVal, newVal) -> changeBrushSize());
        }

        if (sizeValueLabel != null) {
            sizeValueLabel.setText("Brush: " + (int) currentBrushSize + "px");
        }

        setupDrawing();

        //start the animation loop
        startSmoothDrawingLoop();
    }

    /**
     * Called when colorPicker changes.
     */
    @FXML
    private void changeColor() {
        if (colorPicker != null) {
            currentColor = colorPicker.getValue();
        }
    }

    /**
     * Called when sizeSlider changes.
     */
    @FXML
    private void changeBrushSize() {
        if (sizeSlider != null) {
            currentBrushSize = sizeSlider.getValue();
            if (sizeValueLabel != null) {
                sizeValueLabel.setText("Brush: " + (int) currentBrushSize + "px");
            }
        }
    }

    /**
     * Sets up drawing for local drawing and broadcasts drawing data to server.
     */
    private void setupDrawing() {
        var gc = drawingCanvas.getGraphicsContext2D();
        gc.setLineWidth(currentBrushSize);
        gc.setStroke(currentColor);

        // When mouse is pressed
        drawingCanvas.setOnMousePressed(event -> {
            if(!canDraw) {
                return;
            }
            lastX = event.getX();
            lastY = event.getY();

            // Ensure GC uses current brush when starting
            gc.setLineWidth(currentBrushSize);
            gc.setStroke(currentColor);
        });

        // When mouse is dragged so moving while clicking
        drawingCanvas.setOnMouseDragged(event -> {
            if(!canDraw) {
                return;
            }
            double x = event.getX();
            double y = event.getY();

            // Use dynamic brush values
            gc.setLineWidth(currentBrushSize);
            gc.setStroke(currentColor);

            gc.strokeLine(lastX, lastY, x, y);

            if(connected) {
                // send hex color + size to server
                Message drawMessage = Message.createDrawMessage(
                        lastX,
                        lastY,
                        colorToHex(currentColor),
                        currentBrushSize
                );
                sendToServer(drawMessage);
            }

            lastX = x;
            lastY = y;
        });

        drawingCanvas.setOnMouseReleased(event -> {
            // Reset remote drawing tracking when local drawing stops
            if(!canDraw) {
                return;
            }
            if(connected) {
                remoteFirstPoint = false;
            }
        });
    }

    //start the timer
    private void startSmoothDrawingLoop() {
        drawingLoop = new AnimationTimer() {
            @Override
            public void handle(long now) {
                processAnimationQueue();
            }
        };
        drawingLoop.start();
    }

    private void processAnimationQueue() {
        if (pointQueue.isEmpty()) return;

        GraphicsContext gc = drawingCanvas.getGraphicsContext2D();
        gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        gc.setLineJoin(javafx.scene.shape.StrokeLineJoin.ROUND);

        Message.DrawData target = pointQueue.peek();

        // Initialize starting position if this is the first point
        if (remoteFirstPoint) {
            currentAnimX = target.getX();
            currentAnimY = target.getY();
            remoteFirstPoint = false;

            // Draw initial dot
            gc.setFill(Color.web(target.getColor()));
            gc.fillOval(currentAnimX - target.getSize()/2, currentAnimY - target.getSize()/2,
                    target.getSize(), target.getSize());
            pointQueue.poll();
            return;
        }

        // Calculate distance
        double dx = target.getX() - currentAnimX;
        double dy = target.getY() - currentAnimY;
        double distance = Math.sqrt(dx * dx + dy * dy);

        // If very close, snap to it
        if (distance < 1.0) {
            currentAnimX = target.getX();
            currentAnimY = target.getY();
            pointQueue.poll();
            return;
        }

        // If huge jump (pen lift), snap instantly
        if (distance > 100) {
            currentAnimX = target.getX();
            currentAnimY = target.getY();
            remoteFirstPoint = true;
            pointQueue.poll();
            return;
        }

        // MOVE SMOOTHLY towards the target
        double moveX = currentAnimX + (dx * SMOOTHING_SPEED);
        double moveY = currentAnimY + (dy * SMOOTHING_SPEED);

        gc.setStroke(Color.web(target.getColor()));
        gc.setLineWidth(target.getSize());
        gc.strokeLine(currentAnimX, currentAnimY, moveX, moveY);

        currentAnimX = moveX;
        currentAnimY = moveY;
    }

    /**
     * Clears the drawing canvas and resets the drawing related state.
     */
    @FXML
    private void clearCanvas() {
        if(!canDraw) {
            displayMessage("Only the drawer can clear the canvas!\n");
            return;
        }

        GraphicsContext gc = drawingCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, drawingCanvas.getWidth(), drawingCanvas.getHeight());

        lastX = 0;
        lastY = 0;
        remoteFirstPoint = true;
        remoteLastX = 0;
        remoteLastY = 0;

        // If connected, tell server to clear canvas for all players
        if (connected) {
            Message clearMessage = Message.createClearMessage(); // Make sure your Message class has this
            sendToServer(clearMessage);
        }
    }

    /**
     * Converts a JavaFX Color to #RRGGBB hex string.
     */
    private String colorToHex(Color c) {
        int r = (int) Math.round(c.getRed() * 255);
        int g = (int) Math.round(c.getGreen() * 255);
        int b = (int) Math.round(c.getBlue() * 255);
        return String.format("#%02X%02X%02X", r, g, b);
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

                    connection = new Socket();
                    connection.connect(new InetSocketAddress(serverIP, SERVER_PORT), 3000);
                    connection.setSoTimeout(2000);

                    output = new ObjectOutputStream(connection.getOutputStream());
                    output.flush();

                    input = new ObjectInputStream(connection.getInputStream());
                    connection.setSoTimeout(0);

                    connected = true;
                    displayMessage("SUCCESS: Connected to " + serverIP + "\n");

                    Message usernameMsg = Message.createUsernameMessage(username);
                    sendToServer(usernameMsg);

                    displayMessage("Welcome, " + username + "!\n");

                    // Start listening for messages from server
                    processServerMessages();

                } catch (UnknownHostException e) {
                    closeSocketOnError();
                    displayMessage("\n[ERROR] Invalid Host: " + serverIP + "\n");
                    displayMessage("Please check the IP address format.\n");

                } catch (SocketTimeoutException e) {
                    closeSocketOnError();
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
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Connection Error");
                alert.setHeaderText(header);
                alert.setContentText(content);

                alert.showAndWait();

                // Gets the current window (Stage) and close it
                if (chatTextArea.getScene() != null) {
                    Stage stage = (Stage) chatTextArea.getScene().getWindow();
                    stage.close();
                }

                // Makes sure background threads are killed
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

    private void drawRemotePoint(Message.DrawData drawData) {
        // Just add to queue - the AnimationTimer handles the actual drawing
        pointQueue.add(drawData);
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

        }else if (messageType.equals(Message.DRAWER_ASSIGNED)) {
            canDraw = true;
            Platform.runLater(() -> {
                if (wordLabel != null) {
                    wordLabel.setText("YOU ARE DRAWING!");
                }
                drawingCanvas.setStyle("-fx-cursor: crosshair;");
            });
            displayMessage("*** YOU ARE THE DRAWER! ***\n");
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
            displayMessage("[SERVER] " + message.toString() + "\n");
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
        Message chatMessage = Message.createGuessMessage(username, message);
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