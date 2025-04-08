package com.flickwav;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;

import uk.co.caprica.vlcj.factory.MediaPlayerFactory;
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery;
import uk.co.caprica.vlcj.player.base.MediaPlayer;
import uk.co.caprica.vlcj.player.base.MediaPlayerEventListener;
import uk.co.caprica.vlcj.player.base.State;
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer;
import uk.co.caprica.vlcj.javafx.videosurface.ImageViewVideoSurface;
import uk.co.caprica.vlcj.media.MediaRef;
import uk.co.caprica.vlcj.media.TrackType;

import com.mpatric.mp3agic.*;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Main extends Application {

    private MediaPlayerFactory mediaPlayerFactory;
    private EmbeddedMediaPlayer mediaPlayer;
    private ImageView videoView;
    private Stage primaryStage;
    private Button playButton;
    private Button pauseButton;
    private Button stopButton;
    private BorderPane root;
    private VBox controlBox;
    private Menu audioMenu;
    private Menu subtitleMenu;
    private boolean controlsVisible = true;
    private javafx.animation.PauseTransition hideControlsTimer;
    
    private abstract class SimpleMediaPlayerEventAdapter implements MediaPlayerEventListener {
        public void mediaChanged(MediaPlayer mediaPlayer, MediaRef media) {}
        public void mediaParsedChanged(MediaPlayer mediaPlayer, boolean parsed) {}
        public void opening(MediaPlayer mediaPlayer) {}
        public void buffering(MediaPlayer mediaPlayer, float newCache) {}
        public void playing(MediaPlayer mediaPlayer) {}
        public void paused(MediaPlayer mediaPlayer) {}
        public void stopped(MediaPlayer mediaPlayer) {}
        public void finished(MediaPlayer mediaPlayer) {}
        public void timeChanged(MediaPlayer mediaPlayer, long newTime) {}
        public void positionChanged(MediaPlayer mediaPlayer, float newPosition) {}
        public void seekableChanged(MediaPlayer mediaPlayer, int newSeekable) {}
        public void pausableChanged(MediaPlayer mediaPlayer, int newPausable) {}
        public void titleChanged(MediaPlayer mediaPlayer, int newTitle) {}
        public void snapshotTaken(MediaPlayer mediaPlayer, String filename) {}
        public void lengthChanged(MediaPlayer mediaPlayer, long newLength) {}
        public void videoOutput(MediaPlayer mediaPlayer, int newCount) {}
        public void scrambledChanged(MediaPlayer mediaPlayer, int newScrambled) {}
        public void elementaryStreamAdded(MediaPlayer mediaPlayer, TrackType type, int id) {}
        public void elementaryStreamDeleted(MediaPlayer mediaPlayer, TrackType type, int id) {}
        public void elementaryStreamSelected(MediaPlayer mediaPlayer, TrackType type, int id) {}
        public void error(MediaPlayer mediaPlayer) {}
        public void mediaPlayerReady(MediaPlayer mediaPlayer) {}
    }

    @Override
    public void start(Stage stage) {
    	this.primaryStage = stage;

        new NativeDiscovery().discover();

        mediaPlayerFactory = new MediaPlayerFactory();
        mediaPlayer = mediaPlayerFactory.mediaPlayers().newEmbeddedMediaPlayer();
        
        addListenerForButtons();

        videoView = new ImageView();
        videoView.setPreserveRatio(true);
        videoView.setStyle("-fx-background-color: black;");

        mediaPlayer.videoSurface().set(new ImageViewVideoSurface(videoView));

        MenuBar menuBar = new MenuBar();

        Menu fileMenu = new Menu("File");
        MenuItem openItem = new MenuItem("Open File");

        MenuItem exitItem = new MenuItem("Exit");
        
        audioMenu = new Menu("Audio");
        subtitleMenu = new Menu("Subtitles");
        
        MenuItem loadSubtitleItem = new MenuItem("Open Subtitle File...");
        loadSubtitleItem.setOnAction(e -> loadSubtitle(stage));
        subtitleMenu.getItems().add(loadSubtitleItem);
        
        Menu streamingMenu = new Menu("Streaming");
        MenuItem youtubeStreamItem = new MenuItem("Play YouTube Video...");
        youtubeStreamItem.setOnAction(e -> showYouTubeStreamDialog());
        streamingMenu.getItems().add(youtubeStreamItem);

        openItem.setOnAction(e -> {
            openMedia(stage);
            Platform.runLater(() -> {
                populateAudioTracks(audioMenu);
                populateSubtitleTracks(subtitleMenu);
            });
        });

        exitItem.setOnAction(e -> {
            stop();
            stage.close();
        });

        fileMenu.getItems().addAll(openItem, exitItem);
        menuBar.getMenus().addAll(fileMenu, audioMenu, subtitleMenu, streamingMenu);

        VBox menuBarContainer = new VBox(menuBar);

        Slider progressSlider = new Slider();
        progressSlider.setPrefWidth(800);
        progressSlider.setMaxWidth(Double.MAX_VALUE);
        progressSlider.setValue(0);

        Label timeLabel = new Label("00:00 / 00:00");

        playButton = new Button("▶ Play");
        pauseButton = new Button("⏸ Pause");
        stopButton = new Button("⏹ Stop");

        playButton.setOnAction(e -> {
        	mediaPlayer.controls().play();
        	updateButtonStates();
        });
        pauseButton.setOnAction(e -> {
        	mediaPlayer.controls().pause();
        	updateButtonStates();
        });

        stopButton.setOnAction(e -> {
            mediaPlayer.controls().stop();

            // Reset slider and time
            progressSlider.setValue(0);
            updateSliderTrackStyle(progressSlider, 0);

            timeLabel.setText("00:00 / " + formatTime(mediaPlayer.media().info().duration()));
            updateButtonStates();
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

        controlBox = new VBox(5, progressSlider, buttonsBar);
        controlBox.setPadding(new Insets(10));
        controlBox.setStyle("-fx-background-color: #f0f0f0;");
        controlBox.setAlignment(javafx.geometry.Pos.CENTER);

        root = new BorderPane();
        root.setTop(menuBarContainer);
        root.setCenter(videoView);
        root.setBottom(controlBox);
        root.setStyle("-fx-background-color: black;"); // Set root background to black
        
        Button fullscreenButton = new Button("⛶"); // or use an icon
        fullscreenButton.setOnAction(e -> toggleFullScreen(stage));

        Scene scene = new Scene(root, 1000, 750);
        stage.setTitle("Flickwav VLCJ Player");
        stage.setScene(scene);
        stage.show();
        
        setupAutoHideControls(scene);
        
        videoView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                toggleFullScreen(primaryStage);
            }
        });
        
        videoView.fitWidthProperty().bind(root.widthProperty());
        videoView.fitHeightProperty().bind(root.heightProperty().subtract(controlBox.heightProperty()).subtract(menuBar.heightProperty()));
        
        Platform.runLater(() -> root.requestFocus());
        
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                stage.setFullScreen(false);
            }
        });

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
                else {
                    Platform.runLater(() -> updateButtonStates());
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
    
    private void setupAutoHideControls(Scene scene) {
        hideControlsTimer = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(3));
        hideControlsTimer.setOnFinished(e -> {
            if (primaryStage.isFullScreen()) {
                hideControls();
            }
        });

        scene.setOnMouseMoved(e -> {
            if (primaryStage.isFullScreen()) {
                if (!controlsVisible) showControls();
                hideControlsTimer.playFromStart();
            } else {
                // In windowed mode, always show controls
                if (!controlsVisible) showControls();
                hideControlsTimer.stop();
            }
        });

        controlBox.setOnMouseEntered(e -> {
            showControls();
            hideControlsTimer.stop();
        });

        controlBox.setOnMouseExited(e -> {
            if (primaryStage.isFullScreen()) {
                hideControlsTimer.playFromStart();
            }
        });
    }

    private void hideControls() {
        controlBox.setVisible(false);
        controlBox.setManaged(false); // 🔧 Add this line
        root.setCursor(javafx.scene.Cursor.NONE);
        controlsVisible = false;
    }


    private void showControls() {
        controlBox.setVisible(true);
        controlBox.setManaged(true); // 🔧 Add this line
        root.setCursor(javafx.scene.Cursor.DEFAULT);
        controlsVisible = true;
    }

	private void addListenerForButtons() {
		mediaPlayer.events().addMediaPlayerEventListener(new SimpleMediaPlayerEventAdapter() {
            @Override
            public void playing(MediaPlayer mediaPlayer) {
                Platform.runLater(() -> updateButtonStates());
            }

            @Override
            public void paused(MediaPlayer mediaPlayer) {
                Platform.runLater(() -> updateButtonStates());
            }

            @Override
            public void stopped(MediaPlayer mediaPlayer) {
                Platform.runLater(() -> updateButtonStates());
            }

            @Override
            public void finished(MediaPlayer mediaPlayer) {
                Platform.runLater(() -> updateButtonStates());
            }
            
            @Override
            public void mediaParsedChanged(MediaPlayer mediaPlayer, boolean parsed) {
                if (parsed) {
                    Platform.runLater(() -> {
                        populateAudioTracks(audioMenu);
                        populateSubtitleTracks(subtitleMenu);
                    });
                }
            }

			@Override
			public void forward(MediaPlayer mediaPlayer) {
				// TODO Auto-generated method stub
				
			}

			@Override
			public void backward(MediaPlayer mediaPlayer) {
				// TODO Auto-generated method stub
				
			}

			@Override
			public void corked(MediaPlayer mediaPlayer, boolean corked) {
				// TODO Auto-generated method stub
				
			}

			@Override
			public void muted(MediaPlayer mediaPlayer, boolean muted) {
				// TODO Auto-generated method stub
				
			}

			@Override
			public void volumeChanged(MediaPlayer mediaPlayer, float volume) {
				// TODO Auto-generated method stub
				
			}

			@Override
			public void audioDeviceChanged(MediaPlayer mediaPlayer, String audioDevice) {
				// TODO Auto-generated method stub
				
			}

			@Override
			public void chapterChanged(MediaPlayer mediaPlayer, int newChapter) {
				// TODO Auto-generated method stub
				
			}
        });
	}
	
	private void toggleFullScreen(Stage stage) {
	    boolean goingFullScreen = !stage.isFullScreen();
	    stage.setFullScreen(goingFullScreen);
	    Platform.runLater(() -> {
	        Node menuBarContainer = root.getTop();
	        if (menuBarContainer != null) {
	            menuBarContainer.setVisible(!goingFullScreen);
	            menuBarContainer.setManaged(!goingFullScreen);
	        }
	    });
	}



    private void updateButtonStates() {
        State state = mediaPlayer.status().state();

        switch (state) {
            case PLAYING:
                playButton.setDisable(true);
                pauseButton.setDisable(false);
                stopButton.setDisable(false);
                break;

            case PAUSED:
                playButton.setDisable(false);
                pauseButton.setDisable(true);
                stopButton.setDisable(false);
                break;

            case STOPPED:
            case ENDED:
            case NOTHING_SPECIAL:
                playButton.setDisable(false);
                pauseButton.setDisable(true);
                stopButton.setDisable(true);
                break;

            default:
                playButton.setDisable(true);
                pauseButton.setDisable(true);
                stopButton.setDisable(true);
                break;
        }
    }

    private void loadSubtitle(Stage stage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Load Subtitle File");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Subtitle Files", "*.srt", "*.sub", "*.ass"),
            new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        File subtitleFile = fileChooser.showOpenDialog(stage);
        if (subtitleFile != null) {
            boolean success = mediaPlayer.subpictures().setSubTitleFile(subtitleFile);
            if (success) {
                System.out.println("✅ Subtitle loaded: " + subtitleFile.getAbsolutePath());
            } else {
                System.err.println("❌ Failed to load subtitle file.");
            }
        }
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
            mediaPlayer.media().start(file.getAbsolutePath());

            // ✅ Shift focus to root so SPACE/ENTER work
            Platform.runLater(() -> primaryStage.getScene().getRoot().requestFocus());
            updateButtonStates();
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
    
    private void populateAudioTracks(Menu audioMenu) {
        audioMenu.getItems().clear();
        var audioTracks = mediaPlayer.audio().trackDescriptions();

        if (audioTracks != null) {
            for (var track : audioTracks) {
                MenuItem item = new MenuItem(track.description());
                int id = track.id();
                item.setOnAction(e -> mediaPlayer.audio().setTrack(id));
                audioMenu.getItems().add(item);
            }
        } else {
            audioMenu.getItems().add(new MenuItem("No audio tracks found"));
        }
    }

    private void populateSubtitleTracks(Menu subtitleMenu) {
        subtitleMenu.getItems().clear();
        
        MenuItem loadSubtitleItem = new MenuItem("Open Subtitle File...");
        loadSubtitleItem.setOnAction(e -> loadSubtitle(primaryStage));
        subtitleMenu.getItems().add(loadSubtitleItem);
        subtitleMenu.getItems().add(new SeparatorMenuItem());

        var subtitleTracks = mediaPlayer.subpictures().trackDescriptions();

        if (subtitleTracks != null) {
            for (var track : subtitleTracks) {
                MenuItem item = new MenuItem(track.description());
                int id = track.id();
                item.setOnAction(e -> mediaPlayer.subpictures().setTrack(id));
                subtitleMenu.getItems().add(item);
            }
        } else {
            subtitleMenu.getItems().add(new MenuItem("No subtitle tracks found"));
        }
    }

    private void showYouTubeStreamDialog() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("YouTube Streaming");
        dialog.setHeaderText("Stream YouTube Video");
        dialog.setContentText("Enter YouTube URL:");

        dialog.showAndWait().ifPresent(url -> {
            if (!url.trim().isEmpty()) {
                playYouTubeVideo(url.trim());
            }
        });
    }
        
    private void playYouTubeVideo(String youtubeUrl) {
        new Thread(() -> {
            try {
                System.out.println("Fetching stream URL for: " + youtubeUrl);
                
                // Get direct stream URL using yt-dlp
                ProcessBuilder builder = new ProcessBuilder(
                	    "yt-dlp",
                	    "-f b", 
                	    "-g",
                	    "--force-ipv4",
                	    youtubeUrl
                	);
                
                builder.redirectErrorStream(true);
                Process process = builder.start();

                // Read the output URL
                Scanner scanner = new Scanner(process.getInputStream());
                String directUrl = null;
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine().trim();
                    if (line.startsWith("http")) {
                        directUrl = line;
                    }
                }
                scanner.close();

                int exitCode = process.waitFor();

                if (directUrl == null || exitCode != 0 || directUrl.isEmpty()) {
                    showError("Failed to extract YouTube stream URL.");
                    return;
                }

                System.out.println("Stream URL: " + directUrl);
            	String url = directUrl;

                // Play in VLCJ with proper streaming options
                Platform.runLater(() -> {
                	String[] vlcOptions = {
                		    ":network-caching=5000",            // Increased to 5s buffer
                		    ":http-reconnect",
                		    ":http-continuous",
                		    ":http-user-agent=Mozilla/5.0",
                		    ":http-referrer=https://www.youtube.com/",
                		    ":tls-version=1.2"
                		};

                    mediaPlayer.media().play(url, vlcOptions);
                    primaryStage.setTitle("FlickWav - YouTube Stream");
                });

            } catch (Exception ex) {
                ex.printStackTrace();
                showError("Error streaming YouTube: " + ex.getMessage());
            }
        }).start();
    }    

    private void showError(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
    
    private String getStreamUrlFromYoutube(String youtubeUrl) {
        try {
            ProcessBuilder builder = new ProcessBuilder("yt-dlp", "-g", youtubeUrl);
            builder.redirectErrorStream(true);
            Process process = builder.start();

            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()));
            String line = reader.readLine(); // Only need first line — video URL
            process.waitFor();
            return line;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
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
