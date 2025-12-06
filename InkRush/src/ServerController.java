import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

//import for the server GUI
import javafx.fxml.FXML;
import javafx.scene.control.TextArea;
import javafx.application.Platform;
import org.w3c.dom.Text;

/**
 * Controller for InkRush game Server.
 * Manages up to 5 client connections with dedicated display areas for each client to show incoming game states.
 * Uses executor service for multithreaded client handling with JavaFX Task patter.
 * <p>
 * All GUI update are handled through the JavaFX Application Thread. The sockservers are threads for each client, performing tasks
 * and communicating back with the JFXAT to update the server GUI
 */
public class ServerController {
    private static final int PORT = 23596;
    private static final int MAX_CLIENTS = 5;

    @FXML
    private TextArea displayField1;

    @FXML
    private TextArea displayField2;

    @FXML
    private TextArea displayField3;

    @FXML
    private TextArea displayField4;

    @FXML
    private TextArea displayField5;

    private TextArea[] displayFields;
    private ExecutorService executor; // will run players
    private ServerSocket server; // server socket
    private SockServer[] sockServer; // Array of objects to be threaded
    private int counter = 1; // counter of number of connections
    private int nClientsActive = 0;
    private GameLogic gameLogic;

    /**
     * Initialize the controller after FXML is loaded.
     * Sets up the display field array and ExecutorService
     */
    @FXML
    public void initialize() {
        displayFields = new TextArea[]{
                null, // index 0
                displayField1,
                displayField2,
                displayField3,
                displayField4,
                displayField5
        };

        // Initialize display fields
        for (int i = 1; i <= MAX_CLIENTS; i++) {
            displayFields[i].setEditable(false);
            displayFields[i].setText("Waiting for client " + i + " . . . \n");
        }

        sockServer = new SockServer[MAX_CLIENTS + 1];
        executor = Executors.newFixedThreadPool(MAX_CLIENTS);
        gameLogic = new GameLogic(); // added the initializer for new gamelogic
    }


    /**
     * Starts the server in a background thread.
     * Accept up to 5 client connections.
     */
    public void runServer() {
        Runnable serverTask = new Runnable() {
            @Override
            public void run() {
                try {
                    server = new ServerSocket(PORT, MAX_CLIENTS);
                    displayMessageToAll("[Server] Server started on port " + PORT + "\n");

                    //Only create MAX_CLIENTS clients
                    while (counter <= MAX_CLIENTS) {
                        try {
                            sockServer[counter] = new SockServer(counter, displayFields[counter]);
                            sockServer[counter].waitForConnection();

                            synchronized (ServerController.this) {
                                nClientsActive++;
                            }

                            executor.execute(sockServer[counter]);
                        } catch (EOFException e) {
                            displayMessageToAll("[SERVER] Connection Terminated \n");
                        } finally {
                            counter++;
                        }
                    }

                    displayMessageToAll("[Server] Server full - " + MAX_CLIENTS + " clients connected.");
                } catch (IOException ioException) {
                    displayMessageToAll("[Server] Error: " + ioException.getMessage());
                }
            }
        };

        executor.execute(serverTask);
    }

    /**
     * Broadcasts message only to drawer and players who already guessed.
     *
     * @param message Message to broadcast
     */
    private void broadcastToGuessers(Message message) {
        int drawerID = gameLogic.getCurrentDrawerID();

        for (int i = 1; i <= MAX_CLIENTS; i++) {
            if (sockServer[i] != null && sockServer[i].alive) {
                if (i == drawerID || gameLogic.hasGuessedCorrectly(i)) {
                    sockServer[i].sendData(message);
                }
            }
        }
    }

    /**
     * Broadcasts a message to all active clients.
     * Thread-safe method for sending to all clients.
     *
     * @param message Message to be broadcasted
     */
    private void broadcastMessage(Message message) {
        for (int i = 1; i <= MAX_CLIENTS; i++) {
            if (sockServer[i] != null && sockServer[i].alive) {
                sockServer[i].sendData(message);
            }
        }
    }

    /**
     * Broadcasts to all clients except one.
     * Used to display drawing on guesser screens.
     *
     * @param message   String to be broadcasted
     * @param excludeID Index of client not to send info to
     */
    private void broadcastExcept(Message message, int excludeID) {
        for (int i = 1; i <= MAX_CLIENTS; i++) {
            if (i != excludeID && sockServer[i] != null && sockServer[i].alive) {
                sockServer[i].sendData(message);
            }
        }
    }

    /**
     * Displays a message in specific client's TextArea.
     * Thread-safe GUI update using Platform.runLater().
     *
     * @param clientID client display field to update (1 to MAX_CLIENTS)
     * @param message
     */
    private void displayMessage(final int clientID, final String message) {
        if (clientID < 1 || clientID > MAX_CLIENTS) {
            return;
        }

        Platform.runLater(() -> {
            displayFields[clientID].appendText(message);
        });
    }

    /**
     * Displays a message to all client fields in server gui
     *
     * @param message String to be displayed
     */
    private void displayMessageToAll(final String message) {
        Platform.runLater(() -> {
            for (int i = 1; i <= MAX_CLIENTS; i++) {
                displayFields[i].appendText(message);
            }
        });
    }

    /* This new Inner Class implements Runnable and objects instantiated from this
     * class will become server threads each serving a different client
     */

    /**
     * SockServer inner class implements Runnable for handling a single client connection.
     * Each client gets its own dedicated TextArea for displaying activity.
     * Workflow:
     * 1. Client connects, waitForConnection completes
     * 2. ExecutorService while run() method runs in background thread
     * 3. getStreams sets up the I/O with server/client
     * 4. processConnection() loop waiting for receiving messages
     * 5. Client disconnects, closeConnection() cleans up
     */
    private class SockServer implements Runnable {
        private ObjectOutputStream output; // output stream to client
        private ObjectInputStream input; // input stream from client
        private Socket connection; // connection to client
        private final int myConID;
        private final TextArea myDisplay;
        private volatile boolean alive = false;

        /**
         * Creates the handler for specific client
         *
         * @param counterIn    connection id of client
         * @param displayField TextArea associated with client
         */
        public SockServer(int counterIn, TextArea displayField) {
            myConID = counterIn;
            myDisplay = displayField;
        }

        /**
         * Main execution method running in background thread.
         * Handles entire client connection workflow.
         */
        public void run() {
            alive = true;
            try {
                getStreams(); // get input & output streams
                processConnection(); // process connection
            } // end try
            catch (EOFException eofException) {
                displayMessage(myConID, "Client " + myConID + " terminated connection");
            } catch (Exception e) {
                displayMessage(myConID, "Error:  " + e.getMessage() + "\n");
            } finally {
                synchronized (ServerController.this) {
                    nClientsActive--;
                }
                closeConnection(); //  close connection
            }
        }

        /**
         * Wait for client to connect.
         * Called before task execution.
         *
         * @throws IOException if connection fails
         */
        private void waitForConnection() throws IOException {
            displayMessage(myConID, "Waiting for Client " + myConID + " . . .\n");
            connection = server.accept(); // allow server to accept connection
            displayMessage(myConID, "Client " + myConID + " connected from: " +
                    connection.getInetAddress().getHostName());
        }

        /**
         * Sets up input and output streams for communication
         *
         * @throws IOException if stream creation fails
         */
        private void getStreams() throws IOException {
            // set up output stream for objects
            output = new ObjectOutputStream(connection.getOutputStream());
            output.flush(); // flush output buffer to send header information

            // set up input stream for objects
            input = new ObjectInputStream(connection.getInputStream());

            displayMessage(myConID, "Streams ready for Client " + myConID + " . . .\n");
        }

        /**
         * Main message processing loop.
         * Runs continuously, blocking at readObject() until messages arrive
         * Message flow:
         * 1. Client draws and sends info message
         * 2. this method retrieves it and logs to display field
         * 3. handleGameMessage() processes it
         * 4. broadcastExcept() sends to other clients
         * 5. Loop continues, waiting for next message
         *
         * @throws IOException if communication fails
         */
        private void processConnection() throws IOException {
            //Send CONNECTED Message to client
            sendData(Message.createConnectedMessage(myConID));
            displayMessage(myConID, "Client " + myConID + " is ready to play \n");

            while (alive) {
                try {
                    //Blocks here waiting for client message
                    Message message = (Message) input.readObject();

                    // Check for termination
                    if (message.getMessageType().equals(Message.TERMINATE)) {
                        displayMessage(myConID, "Client " + myConID + " terminated connection");
                        break;
                    }

                    // Log received message to this client's display
                    displayMessage(myConID, "RECV: " + message + "\n");

                    // Process the game message
                    handleGameMessage(message);

                } catch (ClassNotFoundException e) {
                    displayMessage(myConID, "ERROR: Unknown object type \n");
                }
            }
        }

        /**
         * Handles different type of game messages.
         * Routes messages accordingly based on type.
         * Message Protocol:
         * - CHAT:username:message -> broadcast to all clients
         * - DRAW:x,y,color,size -> Broadcast to all except drawer
         * - GUESS:username:word -> Check if correct, update scores
         * - CLEAR -> Clear all canvases
         *
         * @param message String message to handle
         */
        private void handleGameMessage(Message message) {
            String messageType = message.getMessageType();

            //Chat message, broadcast to all
            if (messageType.equals(Message.CHAT)) {
                Message.ChatData chatData = message.parseChatMessage();
                displayMessage(myConID, "Broadcasting chat from " + chatData.getUsername() + "\n");
                broadcastMessage(message);
            } else if (messageType.equals(Message.DRAW)) {
                displayMessage(myConID, "Broadcasting drawing point\n");
                broadcastExcept(message, myConID);
            } else if (messageType.equals(Message.GUESS)) {
                Message.GuessData guessData = message.parseGuessMessage();
                String username = guessData.getUsername();
                String guess = guessData.getGuess();

                displayMessage(myConID, username + ": " + guess + "\n");

                // Drawer can always chat
                if (myConID == gameLogic.getCurrentDrawerID()) {
                    Message drawerChat = Message.createChatMessage(username, guess);
                    broadcastMessage(drawerChat);
                    return;
                }

                // Already guessed - hide from non-guessers
                if (gameLogic.hasGuessedCorrectly(myConID)) {
                    broadcastToGuessers(Message.createChatMessage(username, guess));
                    return;
                }

                // Check if correct
                if (gameLogic.checkGuess(guess)) {
                    int points = gameLogic.awardPoints(myConID);

                    Message toGuesser = Message.createChatMessage("SYSTEM",
                            "You guessed the word! +" + points + " points");
                    sockServer[myConID].sendData(toGuesser);

                    Message toOthers = Message.createChatMessage("SYSTEM",
                            username + " guessed the word!");
                    broadcastExcept(toOthers, myConID);

                    if (gameLogic.getGuessCount() >= gameLogic.getPlayerCount() - 1) {
                        Message endMsg = Message.createChatMessage("SYSTEM",
                                "Everyone guessed! Word was: " + gameLogic.getCurrentWord());
                        broadcastMessage(endMsg);

                        gameLogic.endRound();
                    }
                } else {
                    Message wrongGuess = Message.createChatMessage(username, guess);
                    broadcastMessage(wrongGuess);
                }
            } else if (messageType.equals(Message.CLEAR)) {
                displayMessage(myConID, "Broadcasting canvas clear\n");
                broadcastMessage(message);
            } else {
                displayMessage(myConID, "UNKNOWN MESSAGE TYPE: " + message + "\n");
            }
        }

        /**
         * Closes connection and cleans up resources
         */
        private void closeConnection() {
            displayMessage(myConID, "\nTerminating connection " + myConID + "\n");
            displayMessage(myConID, "\nNumber of connections = " + nClientsActive + "\n");
            alive = false;

            try {
                if (output != null) {
                    output.close();
                }
                if (input != null) {
                    input.close();
                }
                if (connection != null) {
                    connection.close();
                }
            } catch (IOException e) {
                displayMessage(myConID, "Error closing connection " + e.getMessage() + "\n");
            }
        }

        /**
         * Sends data to this specific client.
         * Synchronized to prevent concurrent write conflicts
         *
         * @param message String message to be sent
         */
        private void sendData(Message message) {
            try // send object to client
            {
                synchronized (output) {
                    output.writeObject(message);
                    output.flush();
                }
                displayMessage(myConID, "SENT: " + message.toString() + "\n");
            } catch (IOException ioException) {
                displayMessage(myConID, "ERROR sending: " + ioException.getMessage() + "\n");
            }
        }
    }
}