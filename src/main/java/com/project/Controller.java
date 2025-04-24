package com.project;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

public class Controller {

    @FXML
    private TextField textInput;

    @FXML
    private Button sendTextButton;

    @FXML
    private Button uploadImageButton;

    @FXML
    private TextArea responseArea;

    @FXML
    private Label thinkingLabel;

    @FXML
    private Button stopButton;

    // Placeholder methods for button actions
    @FXML
    private void handleSendText() {
        // Logic for sending text will go here
    }

    @FXML
    private void handleUploadImage() {
        // Logic for uploading an image will go here
    }

    @FXML
    private void handleStop() {
        // Logic for stopping the current request will go here
    }
}
