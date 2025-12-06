import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextArea;

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

}
