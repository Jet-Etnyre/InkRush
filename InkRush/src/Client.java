// JavaFX version of Client.java

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.Socket;

public class Client extends Application {
    private TextField enterField;    // Enters information from user
    private TextArea displayArea;    // Display information to user
    private ObjectOutputStream output; // Output stream to server
    private ObjectInputStream input;   // Input stream from server
    private String message = "";       // Message from server
    private String chatServer;         // Host server for this application
    private Socket client;             // Socket to communicate with server

    @Override
    public void start(Stage primaryStage) {
        // Get host from command-line args if provided, otherwise default to localhost
        Parameters params = getParameters();
        if (!params.getRaw().isEmpty()) {
            chatServer = params.getRaw().get(0);
        } else {
            chatServer = "127.0.0.1";
        }

        primaryStage.setTitle("Client");

        // Text field at the top
        enterField = new TextField();
        enterField.setEditable(false);
        enterField.setOnAction(event -> {
            String text = enterField.getText();
            sendData(text);
            enterField.clear();
        });

        // Text area in the center, wrapped in a scroll pane
        displayArea = new TextArea();
        displayArea.setEditable(false);
        displayArea.setWrapText(true);

        ScrollPane scrollPane = new ScrollPane(displayArea);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);

        BorderPane root = new BorderPane();
        root.setTop(enterField);
        root.setCenter(scrollPane);

        Scene scene = new Scene(root, 400, 250);
        primaryStage.setScene(scene);
        primaryStage.show();

        // Make sure we close the connection when the window is closed
        primaryStage.setOnCloseRequest(event -> {
            closeConnection();
            Platform.exit();
        });

        // Run the client networking logic on a background thread
        Thread clientThread = new Thread(this::runClient);
        clientThread.setDaemon(true);
        clientThread.start();
    }

    // Connect to server and process messages from server
    public void runClient() {
        try {
            connectToServer();   // Create a Socket to make connection
            getStreams();        // Get the input and output streams
            processConnection(); // Process connection
        } catch (EOFException eofException) {
            displayMessage("\nClient terminated connection");
        } catch (IOException ioException) {
            ioException.printStackTrace();
            displayMessage("\nI/O error: " + ioException.getMessage());
        } finally {
            closeConnection();   // Close connection
        }
    }

    // Connect to server
    private void connectToServer() throws IOException {
        displayMessage("Attempting connection\n");

        // Create Socket to make connection to server
        client = new Socket(InetAddress.getByName(chatServer), 23555);

        // Display connection information
        displayMessage("Connected to: " +
                client.getInetAddress().getHostName());
    }

    // Get streams to send and receive data
    private void getStreams() throws IOException {
        // Set up output stream for objects
        output = new ObjectOutputStream(client.getOutputStream());
        output.flush(); // Flush output buffer to send header information

        // Set up input stream for objects
        input = new ObjectInputStream(client.getInputStream());

        displayMessage("\nGot I/O streams\n");
    }

    // Process connection with server
    private void processConnection() throws IOException {
        // Enable enterField so client user can send messages
        setTextFieldEditable(true);

        do {
            try {
                message = (String) input.readObject(); // read new message
                displayMessage("\n" + message);        // display message
            } catch (ClassNotFoundException classNotFoundException) {
                displayMessage("\nUnknown object type received");
            }
        } while (!"SERVER>>> TERMINATE".equals(message));
    }

    // Close streams and socket
    private void closeConnection() {
        displayMessage("\nClosing connection");
        setTextFieldEditable(false); // disable enterField

        try {
            if (output != null) {
                output.close();
            }
            if (input != null) {
                input.close();
            }
            if (client != null && !client.isClosed()) {
                client.close();
            }
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }

    // Send message to server
    private void sendData(String message) {
        if (output == null) {
            displayMessage("\nCannot send message: not connected to server");
            return;
        }

        try {
            output.writeObject("CLIENT>>> " + message);
            output.flush(); // flush data to output
            displayMessage("\nCLIENT>>> " + message);
        } catch (IOException ioException) {
            displayMessage("\nError writing object");
        }
    }

    // Manipulates displayArea on the JavaFX Application Thread
    // Mentioned in 23.11 in the textbook, Platform must be used for MT in JavaFX since scene graph in JavaFX is not thread safe.
    // This technique is called thread confinement.
    private void displayMessage(final String messageToDisplay) {
        Platform.runLater(() -> displayArea.appendText(messageToDisplay));
    }

    // Manipulates enterField on the JavaFX Application Thread
    private void setTextFieldEditable(final boolean editable) {
        Platform.runLater(() -> enterField.setEditable(editable));
    }

    public static void main(String[] args) {
        // Optionally pass host as argument: e.g. `java Client 192.168.0.5`
        launch(args);
    }
}

