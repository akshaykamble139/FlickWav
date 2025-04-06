package com.flickwav;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;

import uk.co.caprica.vlcj.factory.MediaPlayerFactory;
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery;
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer;
import uk.co.caprica.vlcj.javafx.videosurface.ImageViewVideoSurface;
import com.mpatric.mp3agic.*;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;

public class Main extends Application {

    private MediaPlayerFactory mediaPlayerFactory;
    private EmbeddedMediaPlayer mediaPlayer;
    private ImageView videoView;
    private Stage primaryStage;

    @Override
    public void start(Stage stage) {
    	this.primaryStage = stage;

        new NativeDiscovery().discover();

        mediaPlayerFactory = new MediaPlayerFactory();
        mediaPlayer = mediaPlayerFactory.mediaPlayers().newEmbeddedMediaPlayer();

        videoView = new ImageView();
        videoView.setFitWidth(960);
        videoView.setFitHeight(540);
        videoView.setPreserveRatio(true);

        mediaPlayer.videoSurface().set(new ImageViewVideoSurface(videoView));

        MenuBar menuBar = new MenuBar();
        Menu fileMenu = new Menu("File");
        MenuItem openItem = new MenuItem("Open File");
        MenuItem exitItem = new MenuItem("Exit");

        openItem.setOnAction(e -> openMedia(stage));
        exitItem.setOnAction(e -> {
            stop();
            stage.close();
        });

        fileMenu.getItems().addAll(openItem, exitItem);
        menuBar.getMenus().add(fileMenu);

        Slider progressSlider = new Slider();
        progressSlider.setPrefWidth(800);
        progressSlider.setMaxWidth(Double.MAX_VALUE);
        progressSlider.setValue(0);

        Label timeLabel = new Label("00:00 / 00:00");

        Button playButton = new Button("▶ Play");
        Button pauseButton = new Button("⏸ Pause");
        Button stopButton = new Button("⏹ Stop");

        playButton.setOnAction(e -> mediaPlayer.controls().play());
        pauseButton.setOnAction(e -> mediaPlayer.controls().pause());

        stopButton.setOnAction(e -> {
            mediaPlayer.controls().stop();

            // Reset slider and time
            progressSlider.setValue(0);
            updateSliderTrackStyle(progressSlider, 0);

            timeLabel.setText("00:00 / " + formatTime(mediaPlayer.media().info().duration()));
        });

        progressSlider.setOnMousePressed(e -> {
            double percent = e.getX() / progressSlider.getWidth();
            long duration = mediaPlayer.media().info().duration();
            mediaPlayer.controls().setTime((long) (percent * duration));
            progressSlider.setValue(percent * 100);
            updateSliderTrackStyle(progressSlider, percent);
        });
        
        progressSlider.setOnMouseDragged(e -> {
            double perc = e.getX() / progressSlider.getWidth();
            double percent = Math.max(0, Math.min(1, perc)); // Clamp 0–1
            long duration = mediaPlayer.media().info().duration();
            long time = (long) (percent * duration);
            Platform.runLater(() -> {
                mediaPlayer.controls().setTime(time);
                progressSlider.setValue(percent * 100);
                updateSliderTrackStyle(progressSlider, percent);
            });
        });

        
        ComboBox<String> speedCombo = new ComboBox<>();
        speedCombo.setItems(FXCollections.observableArrayList(
            "0.5x", "0.75x", "1.0x", "1.25x", "1.5x", "2.0x"
        ));
        speedCombo.setValue("1.0x"); // Default speed

        speedCombo.setOnAction(e -> {
            String selected = speedCombo.getValue();
            double rate = Double.parseDouble(selected.replace("x", ""));
            mediaPlayer.controls().setRate((float) rate);
        });
        
        Slider volumeSlider = new Slider(0, 100, 50); // Min=0, Max=100, Initial=50
        volumeSlider.setPrefWidth(100);
        volumeSlider.setShowTickMarks(false);
        volumeSlider.setShowTickLabels(false);
        
        volumeSlider.getStyleClass().add("custom-slider");
        
        volumeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            int volume = newVal.intValue();
            mediaPlayer.audio().setVolume(volume);

            Node track = volumeSlider.lookup(".track");
            if (track != null) {
                track.setStyle(String.format(
                    "-fx-background-color: linear-gradient(to right, #4caf50 %.2f%%, #ddd %.2f%%);",
                    (double) volume, (double) volume
                ));
            }
        });


        HBox buttonsBar = new HBox(10,
        	    playButton,
        	    pauseButton,
        	    stopButton,
        	    timeLabel,
        	    new Label("Speed:"), speedCombo,
        	    new Label("Volume:"), volumeSlider
        	);

        buttonsBar.setAlignment(javafx.geometry.Pos.CENTER);

        VBox controlBox = new VBox(5, progressSlider, buttonsBar);
        controlBox.setPadding(new Insets(10));
        controlBox.setStyle("-fx-background-color: #f0f0f0;");
        controlBox.setAlignment(javafx.geometry.Pos.CENTER);

        BorderPane root = new BorderPane();
        root.setTop(menuBar);
        root.setCenter(videoView);
        root.setBottom(controlBox);

        Scene scene = new Scene(root, 1000, 750);
        stage.setTitle("Flickwav VLCJ Player");
        stage.setScene(scene);
        stage.show();
        
        Platform.runLater(() -> root.requestFocus());
        
        scene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            switch (event.getCode()) {
                case SPACE:
                case ENTER:
                    if (mediaPlayer.status().isPlayable()) {
                        if (mediaPlayer.status().isPlaying()) {
                            mediaPlayer.controls().pause();
                        } else {
                            mediaPlayer.controls().play();
                        }
                    }
                    event.consume(); // ⛔ prevent buttons from being "clicked"
                    break;
            }
        });


        // Set initial track fill to green (0%)
        Platform.runLater(() -> {
            Node track = progressSlider.lookup(".track");
            if (track != null) {
                track.setStyle("-fx-background-color: linear-gradient(to right, #4caf50 0%, #ddd 0%);");
            }
            
            Node volTrack = volumeSlider.lookup(".track");
            if (volTrack != null) {
                double vol = volumeSlider.getValue();
                volTrack.setStyle(String.format(
                    "-fx-background-color: linear-gradient(to right, #4caf50 %.2f%%, #ddd %.2f%%);",
                    vol, vol
                ));
            }
        });

        scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());

        // Thread to update slider progress and style        
        Thread updateThread = new Thread(() -> {
        	while (true) {
                if (mediaPlayer.status().isPlaying()) {
                    long time = mediaPlayer.status().time();
                    long duration = mediaPlayer.media().info().duration();
                    Platform.runLater(() -> {
                        if (duration > 0) {
                            double percent = (double) time / duration * 100;
                            progressSlider.setValue(percent);
                            timeLabel.setText(formatTime(time) + " / " + formatTime(duration));

                            Node track = progressSlider.lookup(".track");
                            if (track != null) {
                                track.setStyle(String.format(
                                    "-fx-background-color: linear-gradient(to right, #4caf50 %.2f%%, #ddd %.2f%%);",
                                    percent, percent
                                ));
                            }
                        }
                    });
                }
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        updateThread.setDaemon(true); 
        updateThread.start();

    }

    private String formatTime(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        seconds %= 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
    
    private void openMedia(Stage stage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Media File");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Media Files", "*.mp4", "*.mp3", "*.mkv", "*.avi", "*.wav", "*.flac", "*.mov"),
            new FileChooser.ExtensionFilter("All Files", "*.*")
        );
        File file = fileChooser.showOpenDialog(stage);
        if (file != null) {
            String path = file.getAbsolutePath();
            String filename = file.getName();

            primaryStage.setTitle("Flickwav - " + filename);

            if (path.toLowerCase().endsWith(".mp3")) {
                Image artwork = extractAlbumArt(path);
                videoView.setImage(artwork);
            } else {
                videoView.setImage(null);
            }

            mediaPlayer.media().play(path);

            // ✅ Shift focus to root so SPACE/ENTER work
            Platform.runLater(() -> primaryStage.getScene().getRoot().requestFocus());
        }
    }



    private Image extractAlbumArt(String filePath) {
        try {
            Mp3File mp3file = new Mp3File(filePath);
            if (mp3file.hasId3v2Tag()) {
                ID3v2 id3v2Tag = mp3file.getId3v2Tag();
                byte[] albumImageData = id3v2Tag.getAlbumImage();
                if (albumImageData != null) {
                    return new Image(new ByteArrayInputStream(albumImageData));
                } else {
                    System.err.println("No album image found in: " + filePath);
                }
            }
            else if (!mp3file.hasId3v2Tag() && mp3file.hasId3v1Tag()) {
                ID3v1 id3v1Tag = mp3file.getId3v1Tag();
                System.out.println("Title: " + id3v1Tag.getTitle());
                System.out.println("Artist: " + id3v1Tag.getArtist());
                System.out.println("Album: " + id3v1Tag.getAlbum());
            } else {
                System.err.println("No ID3v2 tag found in: " + filePath);
            }
        } catch (IOException | UnsupportedTagException | InvalidDataException e) {
            System.err.println("Error extracting album art from: " + filePath);
            e.printStackTrace();
        }
        return null;
    }

    
    private void updateSliderTrackStyle(Slider slider, double percent) {
        Node track = slider.lookup(".track");
        if (track != null) {
            track.setStyle(String.format(
                "-fx-background-color: linear-gradient(to right, #4caf50 %.2f%%, #ddd %.2f%%);",
                percent * 100, percent * 100
            ));
        }
    }


    @Override
    public void stop() {
        if (mediaPlayer != null) mediaPlayer.release();
        if (mediaPlayerFactory != null) mediaPlayerFactory.release();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
