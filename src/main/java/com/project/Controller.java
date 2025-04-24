package com.project;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextFlow;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;

public class Controller {

    @FXML
    private TextField textInput;

    @FXML
    private Button sendTextButton;

    @FXML
    private Button uploadImageButton;

    @FXML
    private VBox responseContainer;

    @FXML
    private Label thinkingLabel;

    @FXML
    private Button stopButton;

    @FXML
    private ScrollPane responseScrollPane; // Add this to reference the ScrollPane directly

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private String pendingImageBase64 = null; // Holds the Base64-encoded image until the user sends a prompt

    // Add a field to track the ongoing request
    private CompletableFuture<?> ongoingRequest;

    @FXML
    private void handleSendText() {
        String inputText = textInput.getText();

        if (inputText != null && !inputText.isEmpty()) {
            addMessage("User: " + inputText, "-fx-text-fill: blue; -fx-font-size: 16px;");
            textInput.clear();

            thinkingLabel.setText("Thinking...");
            thinkingLabel.setVisible(true);

            if (pendingImageBase64 != null) {
                // Send the image and prompt to the AI
                sendImageToAIWithPrompt(pendingImageBase64, inputText);

                // Clear the pending image
                pendingImageBase64 = null;
            } else {
                // Send the text prompt to the AI
                sendTextToAI(inputText);
            }
        }
    }

    @FXML
    private void handleStop() {
        if (ongoingRequest != null && !ongoingRequest.isDone()) {
            // Cancel the ongoing request
            ongoingRequest.cancel(true);

            // Update the UI to indicate the request has been stopped
            Platform.runLater(() -> {
                thinkingLabel.setVisible(false);
                addMessage("Request stopped by the user.", "-fx-text-fill: orange; -fx-font-size: 16px;");
            });

            // Allow the user to make the next request
            ongoingRequest = null;
        }
    }

    private void sendTextToAI(String userInput) {
        // Create the JSON payload
        String jsonPayload = String.format("{\"model\": \"mistral\", \"prompt\": \"%s\"}", userInput);

        // Build the HTTP request
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:11434/api/generate"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        // Add a placeholder for the LLM's response
        TextFlow llmMessageFlow = new TextFlow();
        llmMessageFlow.setStyle("-fx-padding: 10; -fx-background-color: #f4f4f4; -fx-background-radius: 5;");
        Platform.runLater(() -> responseContainer.getChildren().add(llmMessageFlow));

        // Send the request asynchronously and store the CompletableFuture
        ongoingRequest = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofLines())
                .thenAccept(response -> {
                    response.body().forEach(line -> {
                        try {
                            // Parse the JSON object
                            var jsonObject = new org.json.JSONObject(line);

                            // Extract the "response" field and append it to the LLM's message
                            if (jsonObject.has("response")) {
                                String chunk = jsonObject.getString("response");

                                // Handle the response chunk
                                Platform.runLater(() -> {
                                    if (chunk.equals("\n")) {
                                        // Add a line break if the chunk is a newline character
                                        llmMessageFlow.getChildren().add(new javafx.scene.text.Text(System.lineSeparator()));
                                    } else {
                                        // Add the text chunk as a Text node
                                        javafx.scene.text.Text textNode = new javafx.scene.text.Text(chunk);
                                        textNode.setStyle("-fx-fill: green; -fx-font-size: 16px;");
                                        llmMessageFlow.getChildren().add(textNode);
                                    }
                                });
                            }
                        } catch (org.json.JSONException e) {
                            Platform.runLater(() -> addMessage("LLM: Error parsing response.", "-fx-text-fill: red; -fx-font-size: 16px;"));
                        }
                    });

                    // Hide the "thinking" label once the response is complete
                    Platform.runLater(() -> thinkingLabel.setVisible(false));
                })
                .exceptionally(e -> {
                    if (e instanceof java.util.concurrent.CancellationException) {
                        // Handle cancellation gracefully without showing an error message
                        Platform.runLater(() -> addMessage("Request was canceled.", "-fx-text-fill: orange; -fx-font-size: 16px;"));
                    } else {
                        // Handle other exceptions
                        Platform.runLater(() -> {
                            addMessage("LLM: Error communicating with the AI.", "-fx-text-fill: red; -fx-font-size: 16px;");
                            thinkingLabel.setVisible(false);
                        });
                    }
                    return null;
                });
    }

    private void addMessage(String message, String style) {
        // Create a TextFlow for better text handling
        TextFlow messageFlow = new TextFlow();
        messageFlow.setStyle("-fx-padding: 10; -fx-background-color: #f4f4f4; -fx-background-radius: 5;");
        messageFlow.setMaxWidth(responseContainer.getWidth() - 20); // Adjust width to fit container
        messageFlow.setPrefWidth(responseContainer.getWidth() - 20);

        // Add a listener to adjust the TextFlow's width when the container is resized
        responseContainer.widthProperty().addListener((obs, oldVal, newVal) -> {
            messageFlow.setMaxWidth(newVal.doubleValue() - 20);
            messageFlow.setPrefWidth(newVal.doubleValue() - 20);
        });

        // Create a Text node for the message
        javafx.scene.text.Text textNode = new javafx.scene.text.Text(message);
        textNode.setStyle(style);
        textNode.setWrappingWidth(responseContainer.getWidth() - 40); // Adjust wrapping width
        messageFlow.getChildren().add(textNode);

        // Add the TextFlow to the response container
        Platform.runLater(() -> responseContainer.getChildren().add(messageFlow));

        // Scroll to the bottom to show the latest message
        Platform.runLater(() -> responseScrollPane.setVvalue(1.0));
    }

    @FXML
    private void handleUploadImage() {
        // Create a FileChooser
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select an Image");

        // Set file extension filters to allow only image files
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp")
        );

        // Open the file chooser dialog
        Stage stage = (Stage) responseContainer.getScene().getWindow(); // Get the current stage
        File selectedFile = fileChooser.showOpenDialog(stage);

        // Handle the selected file
        if (selectedFile != null) {
            try {
                // Convert the image to Base64 and store it
                pendingImageBase64 = encodeImageToBase64(selectedFile);

                // Display the image in the chat
                addImageMessage(selectedFile.getAbsolutePath(), pendingImageBase64);
            } catch (IOException e) {
                addMessage("Error processing the image.", "-fx-text-fill: red; -fx-font-size: 16px;");
            }
        } else {
            addMessage("No image selected.", "-fx-text-fill: gray; -fx-font-size: 16px;");
        }
    }

    // Helper method to encode an image to Base64
    private String encodeImageToBase64(File imageFile) throws IOException {
        try (FileInputStream fileInputStream = new FileInputStream(imageFile)) {
            byte[] imageBytes = fileInputStream.readAllBytes();
            return Base64.getEncoder().encodeToString(imageBytes);
        }
    }

    // Method to add an image message to the chat
    private void addImageMessage(String imagePath, String base64Image) {
        // Create a Label for the "User:" prefix
        Label userLabel = new Label("User:");
        userLabel.setStyle("-fx-text-fill: blue; -fx-font-size: 16px;");

        // Create an ImageView to display a small square thumbnail of the image
        Image image = new Image("file:" + imagePath);
        ImageView imageView = new ImageView(image);
        imageView.setFitWidth(100); // Set a fixed width for the thumbnail
        imageView.setFitHeight(100); // Set a fixed height for the thumbnail
        imageView.setPreserveRatio(false); // Disable aspect ratio preservation for a square thumbnail

        // Group the Label and ImageView in a VBox
        VBox imageMessageBox = new VBox(5); // Add spacing between the label and image
        imageMessageBox.getChildren().addAll(userLabel, imageView);

        // Add the VBox to the response container
        Platform.runLater(() -> responseContainer.getChildren().add(imageMessageBox));

        // Scroll to the bottom to show the latest message
        Platform.runLater(() -> responseScrollPane.setVvalue(1.0));
    }

    private void sendImageToAIWithPrompt(String base64Image, String prompt) {
        // Create the JSON payload
        String jsonPayload = String.format(
            "{\"model\": \"llava\", \"prompt\": \"%s\", \"images\": [\"%s\"]}",
            prompt, base64Image
        );

        // Build the HTTP request
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:11434/api/generate"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        // Add a placeholder for the LLM's response
        Label llmMessageLabel = new Label("LLM: ");
        llmMessageLabel.setStyle("-fx-text-fill: green; -fx-font-size: 16px;");
        Platform.runLater(() -> responseContainer.getChildren().add(llmMessageLabel));

        // Send the request asynchronously and store the CompletableFuture
        ongoingRequest = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofLines())
                .thenAccept(response -> {
                    response.body().forEach(line -> {
                        try {
                            // Parse the JSON object
                            var jsonObject = new org.json.JSONObject(line);

                            // Extract the "response" field and append it to the LLM's message
                            if (jsonObject.has("response")) {
                                String chunk = jsonObject.getString("response");

                                // Handle the response chunk
                                Platform.runLater(() -> {
                                    if (chunk.contains("\n")) {
                                        // Split the chunk by \n and add each line as a separate Text node
                                        String[] lines = chunk.split("\n");
                                        for (int i = 0; i < lines.length; i++) {
                                            llmMessageLabel.setText(llmMessageLabel.getText() + lines[i]);
                                            if (i < lines.length - 1) {
                                                llmMessageLabel.setText(llmMessageLabel.getText() + "\n");
                                            }
                                        }
                                    } else {
                                        // Append the chunk directly if it doesn't contain \n
                                        llmMessageLabel.setText(llmMessageLabel.getText() + chunk);
                                    }
                                });
                            }
                        } catch (org.json.JSONException e) {
                            Platform.runLater(() -> addMessage("LLM: Error parsing response.", "-fx-text-fill: red; -fx-font-size: 16px;"));
                        }
                    });

                    // Hide the "thinking" label once the response is complete
                    Platform.runLater(() -> thinkingLabel.setVisible(false));
                })
                .exceptionally(e -> {
                    if (e instanceof java.util.concurrent.CancellationException) {
                        // Handle cancellation gracefully
                        Platform.runLater(() -> addMessage("Request was canceled.", "-fx-text-fill: orange; -fx-font-size: 16px;"));
                    } else {
                        // Handle other exceptions
                        Platform.runLater(() -> {
                            addMessage("LLM: Error communicating with the AI.", "-fx-text-fill: red; -fx-font-size: 16px;");
                            thinkingLabel.setVisible(false);
                        });
                    }
                    return null;
                });
    }
}
