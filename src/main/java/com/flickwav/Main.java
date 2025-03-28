package com.flickwav;

import com.mpatric.mp3agic.ID3v2;
import com.mpatric.mp3agic.Mp3File;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.scene.media.*;

import java.io.ByteArrayInputStream;
import java.io.File;

public class Main extends Application {
    private MediaPlayer mediaPlayer;
    private MediaView mediaView;
    private ImageView placeholderImage;
    private Slider progressSlider; // Progress bar

    private void updateVolumeSliderStyle(Slider slider, double percentage) {
        Platform.runLater(() -> {
            slider.lookup(".track").setStyle("-fx-background-color: linear-gradient(to right, #00cc00 " + percentage + "%, #ccc " + percentage + "%);");
        });
    }

    @Override
    public void start(Stage stage) {

        String defaultTitle = "Flickwav - Media Player";
        stage.setTitle(defaultTitle);

        // Menu Bar
        MenuBar menuBar = new MenuBar();
        Menu fileMenu = new Menu("File");
        MenuItem openFileItem = new MenuItem("Open File");
        fileMenu.getItems().add(openFileItem);

        MenuItem closeFileItem = new MenuItem("Close File");
        fileMenu.getItems().add(closeFileItem);

        closeFileItem.setDisable(true);
        menuBar.getMenus().add(fileMenu);

        // Media View and Placeholder
        mediaView = new MediaView();
        mediaView.setVisible(false); // Initially hidden

        mediaView.setPreserveRatio(true);
        mediaView.setFitWidth(900);  // Adjust based on your UI needs
        mediaView.setFitHeight(600);

        String placeholderPath = getClass().getResource("/placeholder.jpg") != null ?
                getClass().getResource("/placeholder.jpg").toString() : null;

        placeholderImage = placeholderPath != null ? new ImageView(new Image(placeholderPath)) : new ImageView();
        placeholderImage.setFitWidth(900);
        placeholderImage.setFitHeight(600);
        placeholderImage.setPreserveRatio(true);

        if (placeholderPath == null) {
            placeholderImage.setImage(new Image("/placeholder.jpg"));
        }

        // StackPane to overlay mediaView and placeholderImage
        StackPane mediaContainer = new StackPane(placeholderImage, mediaView);
        VBox.setVgrow(mediaContainer, Priority.ALWAYS); // Allows video to scale properly

        // Create ProgressBar (for green fill)
        ProgressBar progressBar = new ProgressBar(0);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.setStyle("-fx-accent: #00cc00;");

        // Create Slider (for user interaction)
        progressSlider = new Slider();
        progressSlider.setMin(0);
        progressSlider.setValue(0);
        progressSlider.setMaxWidth(Double.MAX_VALUE);
        progressSlider.getStyleClass().add("transparent-slider"); // New CSS class

        // Stack them (ProgressBar behind Slider)
        StackPane progressContainer = new StackPane();
        progressContainer.getChildren().addAll(progressBar, progressSlider);

        // Speed Selection Dropdown (ComboBox)
        ComboBox<String> speedComboBox = new ComboBox<>();
        speedComboBox.getItems().addAll("0.5x", "1x", "1.5x", "2x"); // Available speeds
        speedComboBox.setValue("1x"); // Default speed

        speedComboBox.setOnAction(e -> {
            if (mediaPlayer != null) {
                String selectedSpeed = speedComboBox.getValue();
                double speed = Double.parseDouble(selectedSpeed.replace("x", "")); // Convert to double
                mediaPlayer.setRate(speed);
            }
        });

        // Volume Slider
        Slider volumeSlider = new Slider();
        volumeSlider.setMin(0);
        volumeSlider.setMax(100);
        volumeSlider.setValue(50); // Default volume: 50%
        volumeSlider.setShowTickMarks(true);
        volumeSlider.setShowTickLabels(true);
        volumeSlider.setMajorTickUnit(50);
        volumeSlider.setBlockIncrement(5);
        volumeSlider.setMaxWidth(150);

        // Apply initial fill color when app starts
        Platform.runLater(() -> updateVolumeSliderStyle(volumeSlider, 50));

        // Adjust MediaPlayer Volume & Update Slider Fill
        volumeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (mediaPlayer != null) {
                mediaPlayer.setVolume(newVal.doubleValue() / 100.0); // Convert 0-100 to 0-1 range
            }
            updateVolumeSliderStyle(volumeSlider, newVal.doubleValue()); // Update fill color dynamically
        });

        // Controls with Icons
        Button playButton = new Button("▶");
        Button pauseButton = new Button("⏸");
        Button stopButton = new Button("⏹");

        HBox controls = new HBox(10, playButton, pauseButton, stopButton, speedComboBox, new Label("🔊"), volumeSlider);
        controls.setStyle("-fx-padding: 10; -fx-alignment: center;");

        // Layout
        BorderPane root = new BorderPane();
        root.setTop(menuBar);
        root.setCenter(mediaContainer);

        VBox bottomLayout = new VBox(progressContainer, controls);
        bottomLayout.setSpacing(5);
        bottomLayout.setStyle("-fx-padding: 10; -fx-alignment: center;");

        root.setBottom(bottomLayout);

        // File Open Action
        openFileItem.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Media Files", "*.mp3", "*.wav", "*.aac", "*.mp4", "*.avi"));
            File file = fileChooser.showOpenDialog(stage);

            if (file != null) {
                if (mediaPlayer != null) {
                    mediaPlayer.stop();
                }

                String filePath = file.toURI().toString();
                Media media = new Media(filePath);
                mediaPlayer = new MediaPlayer(media);
                mediaView.setMediaPlayer(mediaPlayer);

                stage.setTitle("Flickwav - " + file.getName());
                closeFileItem.setDisable(false);

                // Determine file type (MP3 or Video)
                boolean isAudio = file.getName().toLowerCase().endsWith(".mp3");

                if (isAudio) {
                    // Show placeholderImage, hide mediaView
                    placeholderImage.setVisible(true);
                    mediaView.setVisible(false);

                    // Extract album art
                    try {
                        Mp3File mp3File = new Mp3File(file);
                        if (mp3File.hasId3v2Tag()) {
                            ID3v2 tag = mp3File.getId3v2Tag();
                            byte[] albumImageData = tag.getAlbumImage();
                            if (albumImageData != null) {
                                Image albumImage = new Image(new ByteArrayInputStream(albumImageData));
                                if (!albumImage.isError()) {
                                    Platform.runLater(() -> placeholderImage.setImage(albumImage));
                                } else {
                                    System.out.println("Error loading album image.");
                                }
                            }
                        }
                    } catch (Exception ex) {
                        System.out.println("Error extracting album art: " + ex.getMessage());
                    }

                } else {
                    // Show mediaView, hide placeholderImage
                    placeholderImage.setVisible(false);
                    mediaView.setVisible(true);
                }

                mediaPlayer.currentTimeProperty().addListener((obs, oldTime, newTime) -> {
                    if (!progressSlider.isValueChanging() && mediaPlayer != null) {
                        double progress = newTime.toSeconds() / mediaPlayer.getTotalDuration().toSeconds();
                        progressSlider.setValue(newTime.toSeconds());
                        progressBar.setProgress(progress); // Update ProgressBar
                    }
                });

                mediaPlayer.setOnReady(() -> progressSlider.setMax(media.getDuration().toSeconds()));

                // Allow seeking
                progressSlider.setOnMousePressed(event -> {
                    if (mediaPlayer != null) {
                        mediaPlayer.seek(javafx.util.Duration.seconds(progressSlider.getValue()));
                    }
                });

                progressSlider.setOnMouseReleased(event -> {
                    if (mediaPlayer != null) {
                        mediaPlayer.play(); // Resume playback
                    }
                });

                progressSlider.setOnMouseDragged(event -> {
                    if (mediaPlayer != null) {
                        mediaPlayer.seek(javafx.util.Duration.seconds(progressSlider.getValue()));
                    }
                });

                mediaPlayer.setOnEndOfMedia(() -> {
                    mediaPlayer.stop(); // Stop the media
                    mediaPlayer.seek(javafx.util.Duration.ZERO); // Reset to the beginning
                    progressSlider.setValue(0); // Reset progress slider
                    progressBar.setProgress(0); // Reset green progress bar
                });


                mediaPlayer.play();
            }
        });

        // Close File Action
        closeFileItem.setOnAction(e -> {
            if (mediaPlayer != null) {
                mediaPlayer.stop();
                mediaPlayer.dispose();
                mediaPlayer = null;
            }

            stage.setTitle(defaultTitle); // Reset title
            placeholderImage.setVisible(true);
            mediaView.setVisible(false);
            placeholderImage.setImage(new Image("/placeholder.jpg"));

            progressSlider.setValue(0);
            progressBar.setProgress(0);

            closeFileItem.setDisable(true); // Disable close button
        });

        // Button Actions
        playButton.setOnAction(e -> { if (mediaPlayer != null) mediaPlayer.play(); });
        pauseButton.setOnAction(e -> { if (mediaPlayer != null) mediaPlayer.pause(); });
        stopButton.setOnAction(e -> { if (mediaPlayer != null) mediaPlayer.stop(); });

        // Scene Setup
        Scene scene = new Scene(root, 1000, 750);
        stage.setScene(scene);
        stage.setTitle("Flickwav - Media Player");
        scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
        stage.show();

        // Keyboard Controls: Space and Enter to Play/Pause
        scene.setOnKeyPressed(event -> {
            if (mediaPlayer != null) {
                switch (event.getCode()) {
                    case SPACE, ENTER -> {
                        MediaPlayer.Status status = mediaPlayer.getStatus();
                        if (status == MediaPlayer.Status.PLAYING) {
                            mediaPlayer.pause();
                        } else if (status == MediaPlayer.Status.PAUSED || status == MediaPlayer.Status.READY) {
                            mediaPlayer.play();
                        }
                    }
                    default -> {
                        // Do nothing for other keys
                    }
                }
            }
        });
    }


    public static void main(String[] args) {
        launch(args);
    }
}
