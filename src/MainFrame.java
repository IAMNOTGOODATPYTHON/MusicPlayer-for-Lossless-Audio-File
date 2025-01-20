
import javax.sound.sampled.*;
import javax.swing.*;
import javax.swing.filechooser.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.text.*;
import java.util.*;
import java.util.logging.*;
import org.jaudiotagger.tag.*;
import org.jaudiotagger.tag.images.Artwork;
import org.kc7bfi.jflac.*;
import org.kc7bfi.jflac.metadata.*;
import org.jaudiotagger.audio.*;

public class MainFrame extends JFrame{
    private static final Logger LOGGER = Logger.getLogger(Frame.class.getName());
    //private static final String DEFAULT_MUSIC_PATH = "C:\\Users\\zhang\\Music\\iTunes\\iTunes Media\\Music\\FiiOMusic";
    private int currentIndex = 0;
    private int openCount = 0;
    private int songNumber = 1;
    private int clickcount = 0;
    private String encodiingFormat, sampleRate, bitDepth;
    private JLabel songTitle, songFormat, sliderLabelBegin, sliderLabelEnd, imageLabel;
    private JPanel buttonPanel, sliderPanel, playlistPanel;
    private JScrollPane playlistScrollPane;
    private JMenuBar menuBar;
    private JMenu fileMenu, openMenu, playlistMenu;
    private JMenuItem resetMenuItem, randomizeMenuItem, displayMenuItem, wavMenuItem, flacMenuItem;
    private JButton playButton, pauseButton, prevButton, nextButton;
    private JSlider playbackSlider;
    private AudioInputStream audioStream;
    private Clip clip;
    private File[] fileGroup, initialGroup, updateGroup;
    private JFileChooser fileChooser;
    private PlaybackCurrentPosition_Thread playbackThread;
    private PlayListFrame playlistFrame;
    private Object lock = new Object();
    private boolean actionPerformed;
    private File openRequest = null;
    private double seekRequest = -1;  // Either -1 or a number in [0.0, 1.0]
    private FlacDecoder dec = null;
    private SourceDataLine line = null;
    private long clipStartTime;
    private Thread decoderThread = null;
    MainFrame() {
        Thread.currentThread().setName("Frame_Thread");
        setupFrame(); initializeComponents();
    }
    final private void setupFrame() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);
        setLocationRelativeTo(null);
        setSize(400, 600);
        setLayout(null);
        getContentPane().setBackground(Color.BLACK);

        addComponentListener(new ComponentAdapter() {
            public void componentMoved(ComponentEvent e) {playlistFrame.setLocation(getX()+getWidth(), getY());}
            public void componentResized(ComponentEvent e) {}
        });
    }
    final private void initializeComponents() {
        initializeMenuBar();
        initializeButtons();
        initializeSongLabels();
        initialAlbumImage();
        initializeSlider(); 
        initializePlayListFrame();
    }
    final private void initializePlayListFrame () {
        playlistFrame = new PlayListFrame();
    }
    final private void initializeMenuBar() {
        menuBar = new JMenuBar();

        fileMenu = new JMenu("File");
        openMenu = new JMenu("Open");
        playlistMenu = new JMenu("PlayList");

        resetMenuItem = new JMenuItem("Reset");
        randomizeMenuItem = new JMenuItem("Randomize");
        displayMenuItem = new JMenuItem("Display");
        wavMenuItem = new JMenuItem(".wav");
        flacMenuItem = new JMenuItem(".flac");

        fileMenu.add(openMenu);
        fileMenu.add(resetMenuItem);
        openMenu.add(wavMenuItem);
        openMenu.add(flacMenuItem);
        playlistMenu.add(randomizeMenuItem);
        playlistMenu.add(displayMenuItem);

        menuBar.add(fileMenu);
        menuBar.add(playlistMenu);

        setJMenuBar(menuBar);

        fileMenu.addMouseListener(new MouseListener() {
            public void mouseEntered(MouseEvent e) {if (e.getSource() == fileMenu) fileMenu.doClick();}
            public void mouseClicked(MouseEvent e) {}
            public void mousePressed(MouseEvent e) {}
            public void mouseReleased(MouseEvent e) {}
            public void mouseExited(MouseEvent e) {}
        });
        playlistMenu.addMouseListener(new MouseListener() {
            public void mouseEntered(MouseEvent e) {if (e.getSource() == playlistMenu) playlistMenu.doClick();}
            public void mouseClicked(MouseEvent e) {}
            public void mousePressed(MouseEvent e) {}
            public void mouseReleased(MouseEvent e) {}
            public void mouseExited(MouseEvent e) {}
        });
        
        resetMenuItem.addActionListener(e -> ResetAction());
        randomizeMenuItem.addActionListener(e -> RandomizeAction());
        displayMenuItem.addActionListener(e -> DisplayAction());
        wavMenuItem.addActionListener(e -> wavAction());
        flacMenuItem.addActionListener(e -> flacAction());
    }
    final private void initializeButtons() {
        buttonPanel = new JPanel();
        buttonPanel.setBounds(0, 435, getWidth() - 15, 80);
        buttonPanel.setBackground(null);

        prevButton = createButton("Prev");
        playButton = createButton("Play");
        pauseButton = createButton("Pause");
        pauseButton.setVisible(false);
        nextButton = createButton("Next");

        buttonPanel.add(prevButton); buttonPanel.add(playButton); buttonPanel.add(pauseButton); buttonPanel.add(nextButton);

        getContentPane().add(buttonPanel);

        ButtonAction();
    }
    final private void ButtonAction() {
        playButton.addActionListener(e -> PlayAction());
        pauseButton.addActionListener(e -> PauseAction());
        nextButton.addActionListener(e -> NextAction());
        prevButton.addActionListener(e -> PrevAction());
    }
    final private JButton createButton(String text) {
        JButton button = new JButton(text); 
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        return button;
    }
    final private void initializeSongLabels() {
        songTitle = createLabel(300);
        songFormat = createLabel(335);

        add(songTitle); add(songFormat);
    }
    final private void initialAlbumImage() {
        imageLabel = new JLabel();
        imageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        imageLabel.setBounds(43, 5, 300, 300); // Example: Adjust these bounds as needed
        add(imageLabel);
    }
    final private JLabel createLabel(int yPosition) {
        JLabel label = new JLabel();
        label.setBounds(0, yPosition, getWidth() - 15, 30);
        label.setForeground(Color.WHITE);
        label.setFont(new Font("Dialog", Font.PLAIN, 17));
        label.setHorizontalAlignment(SwingConstants.CENTER);
        return label;
    }
    final private void initializeSlider(){
        sliderPanel = new JPanel();
        sliderPanel.setBackground(null);
        sliderPanel.setBounds(0, 380, getWidth() - 15, 30);

        sliderLabelBegin = new JLabel("00:00");
        sliderLabelBegin.setForeground(Color.WHITE);

        sliderLabelEnd = new JLabel("00:00");
        sliderLabelEnd.setForeground(Color.WHITE);

        playbackSlider = new JSlider(JSlider.HORIZONTAL, 0, 10000, 0);
        playbackSlider.setSize(200, 20);
        playbackSlider.setBackground(null);
        playbackSlider.setEnabled(false);

        playbackSlider.addMouseMotionListener(new MouseMotionListener() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if(getFileExtension(fileGroup[currentIndex].getName()).equals("wav")){
                    try{    
                        if(clip.isRunning()){
                            stopPlaybackCurrentPosition();
                            sliderLabelBegin.setText(DurationConverter(playbackSlider.getValue()));
                        }
                        else sliderLabelBegin.setText(DurationConverter(playbackSlider.getValue()));
                    }catch(Exception ignore){}
                }
                if(getFileExtension(fileGroup[currentIndex].getName()).equals("flac")){
                    moveSlider(e);
                }
            }
            @Override
            public void mouseMoved(MouseEvent e) {}
        });
        
        playbackSlider.addMouseListener(new MouseAdapter(){
            public void mouseClicked(MouseEvent e) {
                if(getFileExtension(fileGroup[currentIndex].getName()).equals("wav")){
                    try{
                        if(clip.isOpen()) {
                            sliderLabelBegin.setText(DurationConverter(getValueForXPositionin(e.getX())));
                            playbackSlider.setValue(getValueForXPositionin(e.getX()));
                            clip.setMicrosecondPosition(getValueForXPositionin(e.getX()));
                            if (playbackSlider.getValue() == playbackSlider.getMaximum()) AutomaticallyNextOrStop();
                        }            
                    }catch(Exception ignore) {}
                }
            }
            public void mousePressed(MouseEvent e) {
                if(getFileExtension(fileGroup[currentIndex].getName()).equals("flac")){
                    moveSlider(e);
                }
            }
            public void mouseReleased(MouseEvent e) {
                if(getFileExtension(fileGroup[currentIndex].getName()).equals("wav")){
                    try{
                        if(clip.isRunning()){
                            clip.setMicrosecondPosition(playbackSlider.getValue());
                            restartPlaybackCurrentPosition();
                        }
                        else clip.setMicrosecondPosition(playbackSlider.getValue());
                    }catch(Exception ignore){}
                }
                else if(getFileExtension(fileGroup[currentIndex].getName()).equals("flac")){
                    moveSlider(e);
                    if (playbackSlider.isEnabled()) {
                        synchronized(lock) {
                            seekRequest = (double)playbackSlider.getValue() / playbackSlider.getMaximum();
                            lock.notify();
                        }
                    }
                }
                if (playbackSlider.getValue() == playbackSlider.getMaximum()) AutomaticallyNextOrStop();
            }
        });
        
        sliderPanel.add(sliderLabelBegin);
        sliderPanel.add(playbackSlider);
        sliderPanel.add(sliderLabelEnd);

        add(sliderPanel);
    }
    private void moveSlider(MouseEvent e) {
        if (playbackSlider.isEnabled()) playbackSlider.setValue(getValueForXPositionin(e.getX()));
    }
    final private int getValueForXPositionin(int i) {
        int sliderWidth = playbackSlider.getWidth(); // Get the slider's total width
        int minValue = playbackSlider.getMinimum();
        int maxValue = playbackSlider.getMaximum();
        int range = maxValue - minValue;    
        double proportion = (double) i / sliderWidth;
        int value = minValue + (int) (proportion * range);
        return Math.max(minValue, Math.min(value, maxValue));
    }
    final private void ResetAction() {
        fileChooser.setCurrentDirectory(null);
        if(fileChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION){
            fileChooser.setCurrentDirectory(new File(fileChooser.getSelectedFile().getPath()));
        }
    }
    final private void RandomizeAction() {
        if(fileGroup != null) {
            Collections.shuffle(Arrays.asList(fileGroup));
            songNumber = 1; playlistPanel.removeAll();
            while (songNumber <= fileGroup.length) {
                JLabel label = createLabel(songNumber*30);
                label.setText("" + songNumber + ". " + fileGroup[songNumber - 1].getName());
                playlistPanel.add(label);
                songNumber++;
            }
            SwingUtilities.invokeLater(() -> {
                playlistPanel.revalidate(); // Refresh layout
                playlistPanel.repaint();   // Repaint UI
            });
            if (currentIndex == fileGroup.length - 1) currentIndex = -1;
        }
    }
    final private void DisplayAction() {displayPlaylist(); new displayPlaylist_Thread().start();}
    final private void wavAction() {
        if (fileChooser == null) {
            fileChooser = new JFileChooser(new File("C:\\Users\\zhang\\Music\\iTunes\\iTunes Media\\Music\\FiiOMusic"));
            try {
                createPlaylist();
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error opening files", e);
            }
        }
        else {
            fileChooser.setCurrentDirectory(new File("C:\\Users\\zhang\\Music\\iTunes\\iTunes Media\\Music\\FiiOMusic"));
            try {
                createPlaylist();
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error opening files", e);
            }
        }
    }
    final private void flacAction() {
        if (fileChooser == null) {
            fileChooser  = new JFileChooser(new File("C:\\Users\\zhang\\Music\\Music Center"));
            try {
                createPlaylist();
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error opening files", e);
            }
        }
        else {
            fileChooser.setCurrentDirectory(new File("C:\\Users\\zhang\\Music\\Music Center"));
            try {
                createPlaylist();
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error opening files", e);
            }
        }
    }
    final private void PlayAction() {
        try {
            if(clip != null && clip.isOpen()){
                playButton.setVisible(false);
                pauseButton.setVisible(true);
                clip.start(); startPlaybackCurrentPosition();
            }
            else if (getFileExtension(fileGroup[currentIndex].getName()).equals("flac")){
                playButton.setVisible(false);
                pauseButton.setVisible(true);
                if(clickcount == 0) {decoderThread.start();clickcount++;}
                else {
                    synchronized (lock) {
                        actionPerformed = true;
                        seekRequest = -1;
                        lock.notifyAll();
                    }
                    line.start();
                }
            }
        } catch (Exception e) {e.printStackTrace();}
    }
    final private void PauseAction() {
        try {
            if(getFileExtension(fileGroup[currentIndex].getName()).equals("wav")){
                pauseButton.setVisible(false);
                playButton.setVisible(true);
                clip.stop();
            }
            else if (getFileExtension(fileGroup[currentIndex].getName()).equals("flac")){
                pauseButton.setVisible(false);
                playButton.setVisible(true);

                actionPerformed = false;

                if(line != null) line.stop();
            }
        } catch (Exception ignore) {}
    }
    final private void NextAction() {
        try {
            if (currentIndex != fileGroup.length - 1 && fileGroup != null) {
                switchSong(++currentIndex); 
            } 
            else if(currentIndex == fileGroup.length - 1 && fileGroup != null) {
                JOptionPane.showMessageDialog(null, "This is the last song", "Caution", JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception ignore) {}
    }
    final private void PrevAction() {
        try {
            if (currentIndex > 0 && fileGroup != null) {
                switchSong(--currentIndex);
            } 
            else if (currentIndex == 0 && fileGroup != null) {
                JOptionPane.showMessageDialog(null, "This is the first song", "Caution", JOptionPane.ERROR_MESSAGE);
            }
            else JOptionPane.showMessageDialog(null, "No playlist for this moment", "Caution", JOptionPane.ERROR_MESSAGE);
        } catch (Exception ignore) {}
    }
    final private void createPlaylist() throws UnsupportedAudioFileException, IOException, LineUnavailableException {
        fileChooser.setMultiSelectionEnabled(true);
        fileChooser.setFileFilter(new FileNameExtensionFilter(null, "wav", "flac"));
        if (fileChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION){
            fileGroup = fileChooser.getSelectedFiles(); 
            playbackSlider.setEnabled(true);
            if (openCount == 0) setupInitialPlaylist(); else updatePlaylist(); 
        }     
    }
    final private void setupInitialPlaylist() throws UnsupportedAudioFileException, IOException, LineUnavailableException {

        openCount++;

        initialGroup = Arrays.copyOf(fileGroup, fileGroup.length);

        while (songNumber <= fileGroup.length) {
            JLabel label = createLabel(songNumber*30);
            label.setText("" + songNumber + ". " + fileGroup[songNumber - 1].getName());
            playlistPanel.add(label);
            songNumber++;
        }
        if (playlistFrame.isShowing()){
            SwingUtilities.invokeLater(() -> {
                playlistPanel.revalidate(); // Refresh layout
                playlistPanel.repaint();   // Repaint UI
            });
        }
        if (getFileExtension(fileGroup[currentIndex].getName()).equals("wav")){
            sliderLabelEnd.setText(getDurationFormatted(fileGroup[currentIndex]));

            playbackSlider.setMaximum(getDurationInMicroSeconds(fileGroup[currentIndex]));

            audioStream = AudioSystem.getAudioInputStream(fileGroup[currentIndex]);

            songTitle.setText(fileGroup[currentIndex].getName());
            updateSongFormat(fileGroup[currentIndex]);

            clip = AudioSystem.getClip();
            clip.open(audioStream);
        }
        else if (getFileExtension(fileGroup[currentIndex].getName()).equals("flac")) {
            //clickcount == 0 only applicable to the case which the first song is flac file
            setFlacImageLabel(currentIndex);

            songTitle.setText(fileGroup[currentIndex].getName());
            updateSongFormat(fileGroup[currentIndex]);

            openRequest = fileGroup[currentIndex]; // Set file for decoding
            decoderThread = new Thread(() -> doAudioDecoderWorkerLoop());
            /*  
                Why the Album Image Might Not Appear Without a Separate Thread:
                1.Blocking the Event Dispatch Thread (EDT):
                Swing uses the EDT to manage UI updates. If doAudioDecoderWorkerLoop() 
                is executed on the EDT, it will block the UI from refreshing because the method performs a 
                long-running task (e.g., decoding audio blocks).
                2.Asynchronous UI Updates:
                Swing components (like the label showing the album image) rely on SwingUtilities.invokeLater 
                to schedule updates on the EDT. If the EDT is blocked, these updates are delayed.
             */
            actionPerformed = true;
        }
    }
    final private void updatePlaylist() {
        updateGroup = Arrays.copyOf(fileGroup, fileGroup.length);
        fileGroup = mergeArrays(initialGroup, updateGroup);// We only need to make sure they share a same memory address, "identical in itmes is unnecessary"
        initialGroup = Arrays.copyOf(fileGroup, fileGroup.length);
        /* 
        Swing’s Layout Management: When you dynamically add components (e.g., new JLabels to playlistPanel), Swing doesn't 
        automatically refresh the layout of the parent container unless explicitly instructed.
        Lack of Revalidation: The playlistPanel is updated with new JLabels, but revalidate() and repaint() are not called.                 This means the JScrollPane (wrapping the playlistPanel) doesn't know about the changes.
        */
        while (songNumber <= fileGroup.length) {
            JLabel label = createLabel(songNumber*30);
            label.setText("" + songNumber + ". " + fileGroup[songNumber - 1].getName());
            playlistPanel.add(label);
            songNumber++;
        }
        if (playlistFrame.isShowing()){
            SwingUtilities.invokeLater(() -> {
                playlistPanel.revalidate(); // Refresh layout
                playlistPanel.repaint();   // Repaint UI
            });
        }
    }
    final private void switchSong(int index) throws UnsupportedAudioFileException, IOException, LineUnavailableException {
        stopPlaybackCurrentPosition();

        playbackSlider.setEnabled(false);

        if (getFileExtension(fileGroup[index].getName()).equals("wav")){
            if(decoderThread != null) {line.stop(); line.close(); decoderThread.interrupt(); decoderThread = null;}
            if(clip != null) {clip.stop(); clip.close();}

            sliderLabelBegin.setText("00:00");
            sliderLabelEnd.setText(getDurationFormatted(fileGroup[index]));

            playbackSlider.setMaximum(getDurationInMicroSeconds(fileGroup[index]));

            audioStream = AudioSystem.getAudioInputStream(fileGroup[index]);

            imageLabel.setIcon(null);
            SwingUtilities.invokeLater(() -> {
                imageLabel.revalidate(); // Refresh layout
                imageLabel.repaint();   // Repaint UI
            });

            songTitle.setText(fileGroup[index].getName());
            updateSongFormat(fileGroup[index]);

            playbackSlider.setValue(0);
            playbackSlider.setEnabled(true);

            clip = AudioSystem.getClip();
            clip.open(audioStream);
            clip.start();

            SwingUtilities.invokeLater(() -> {
                sliderPanel.revalidate(); // Refresh layout
                sliderPanel.repaint();   // Repaint UI
                restartPlaybackCurrentPosition();
            });
        }
        else if (getFileExtension(fileGroup[index].getName()).equals("flac")) {
            clickcount++;//avoid clickcount == 0

            if(clip != null) {clip.stop(); clip.close();}
            if (line != null && line.isOpen()) {
                line.stop();
                line.close();
                decoderThread.interrupt();
                decoderThread = null;
            }

            setFlacImageLabel(index);

            sliderLabelBegin.setText("00:00");

            songTitle.setText(fileGroup[index].getName());
            updateSongFormat(fileGroup[index]);

            openRequest = fileGroup[index]; // Set file for decoding
            decoderThread = new Thread(() -> doAudioDecoderWorkerLoop());

            actionPerformed = true;//When pausing the current flac file, u can still switch songs.

            decoderThread.start();
        }

        playButton.setVisible(false);
        pauseButton.setVisible(true);
    }
    final private void setFlacImageLabel(int index) {
        Image image = FlacAlbumImageExtractor(fileGroup[index].getPath()).getImage();
        if(image != null){
            image = image.getScaledInstance(260, (int) (image.getHeight(null) * ((double) 260 / image.getWidth(null))), Image.SCALE_SMOOTH);
            imageLabel.setIcon(new ImageIcon(image));
        }
        SwingUtilities.invokeLater(() -> {
            imageLabel.revalidate(); // Refresh layout
            imageLabel.repaint();   // Repaint UI
        });
    }
    final private void updateSongFormat(File file) throws UnsupportedAudioFileException, IOException {
        if(getFileExtension(file.getName()).equals("wav")){
            AudioInputStream audio = AudioSystem.getAudioInputStream(file);
            encodiingFormat = audio.getFormat().getEncoding().toString().substring(0, 3);
            sampleRate = Float.toString(audio.getFormat().getSampleRate() / 1000) + " " + "kHz";
            bitDepth = Integer.toString(audio.getFormat().getSampleSizeInBits()) + " " + "bit";
            songFormat.setText(encodiingFormat + " " + bitDepth + " / " + sampleRate);
        }
        else if(getFileExtension(file.getName()).equals("flac")){
            try (FileInputStream fileInputStream = new FileInputStream(file)){
                FLACDecoder decoder = new FLACDecoder(fileInputStream);
                StreamInfo streamInfo = decoder.readStreamInfo();
                encodiingFormat = "FLAC";
                bitDepth = "" + streamInfo.getBitsPerSample() + " " + "bit";
                sampleRate = "" + (float)streamInfo.getSampleRate() / 1000 + " " + "kHz"; 
                songFormat.setText(encodiingFormat + " " + bitDepth + " / " + sampleRate);
            } catch (Exception ignore){}
        }
    }
    final private String getDurationFormatted(File file) throws UnsupportedAudioFileException, IOException {
        double durationInSeconds = getDurationInSeconds(file);
        int minutes = (int) (durationInSeconds / 60); int seconds = (int) (durationInSeconds % 60); 
        return String.format("%d:%02d", minutes, seconds);
    }  
    final private String DurationConverter(int Microsecond) {
        return String.format("%d:%02d", (Microsecond/1000000) / 60, (Microsecond/1000000) % 60);
    }
    final private double getDurationInSeconds (File file) throws UnsupportedAudioFileException, IOException {
        return Double.parseDouble(String.format("%.7f", AudioSystem.getAudioFileFormat(file).getFrameLength() / AudioSystem.getAudioInputStream(file).getFormat().getSampleRate()));
        //return (AudioSystem.getAudioFileFormat(file).getFrameLength() / AudioSystem.getAudioInputStream(file).getFormat().getSampleRate());
    }
    final private int getDurationInMicroSeconds (File file) throws UnsupportedAudioFileException, IOException {
        DecimalFormat df = new DecimalFormat("#");
        return Integer.parseInt(df.format(getDurationInSeconds(file)*1000000));
    }
    final private void startPlaybackCurrentPosition() {
        //We need to use a Thread to run simultaneously;
        playbackThread = new PlaybackCurrentPosition_Thread();
        playbackThread.start(); 
    }  
    final private void stopPlaybackCurrentPosition() {
        //We need to use a Thread to run simultaneously;
        if (playbackThread != null && playbackThread.isAlive()) {
            playbackThread.interrupt(); // Interrupt the current thread
            playbackThread = null;     // Clear the reference
        }
    }
    final private void restartPlaybackCurrentPosition() {
        //you can restart a thread after it has been interrupted, 
        //but you cannot restart the same thread instance once it has been terminated. 
        //Instead, you need to create and start a new thread instance.
        stopPlaybackCurrentPosition();
        //System.out.println("restartPlaybackCurrentPosition is working...");// For debug
        try {startPlaybackCurrentPosition();} 
        catch (Exception e) {e.printStackTrace();}
    }
    final private File[] mergeArrays(File[] arr1, File[] arr2) {
        int i = 0; int j; File[] res = new File[arr1.length + arr2.length];
        while (i < arr1.length) {res[i] = arr1[i]; i++;}
        i = 0; j = arr1.length;
        while (i < arr2.length) {res[j] = arr2[i]; i++; j++;}
        return res;
    }
    final private String getFileExtension(String fileName) {
        int lastIndexOfDot = fileName.lastIndexOf(".");
        if (lastIndexOfDot > 0) {
            return fileName.substring(lastIndexOfDot + 1);
        } else {
            return ""; // No extension found
        }
    }
    final private void displayPlaylist() {playlistFrame.setVisible(true);}
    final private long getClipMicrosecondPosition() {return clip.getMicrosecondPosition();}
    final private void AutomaticallyNextOrStop() {
        try {
            if(playbackSlider.getValue() == playbackSlider.getMaximum()) {
                if (currentIndex == fileGroup.length - 1) {
                    currentIndex = -1; nextButton.doClick();
                }
                else nextButton.doClick();
            }
        } catch (Exception e) {e.printStackTrace();}
    }
    private class PlaybackCurrentPosition_Thread extends Thread{
        PlaybackCurrentPosition_Thread(){setName("PlaybackCurrentPosition_Thread");}
        public void run() {
            //System.out.println("Thread running...");// For debug
            while (clip != null && clip.isRunning()) {
                try {
                    long Microsecond = getClipMicrosecondPosition();
                    playbackSlider.setValue((int) Microsecond);
                    sliderLabelBegin.setText(String.format("%d:%02d", (int) ((Microsecond / 1000000) / 60), (int) ((Microsecond / 1000000) % 60)));
                    AutomaticallyNextOrStop();
                    Thread.sleep(100); // Update every (1 s = 1,000 ms)
                } catch (InterruptedException e) {Thread.currentThread().interrupt(); break;}
            }
            //System.out.println("Thread exiting...");// For debug
        }
    }
    private class displayPlaylist_Thread extends Thread {
        displayPlaylist_Thread(){setName("displayPlaylist_Thread");}
        public void run(){
            while(isShowing()) {
                if(playlistFrame.isShowing()) displayMenuItem.setEnabled(false);
                if(!playlistFrame.isShowing()) displayMenuItem.setEnabled(true);
            }
        }
    }
    private class PlayListFrame extends JFrame {
        PlayListFrame() {
            setTitle("PlayList");
            setResizable(false);
            setSize(300, 450);
            setLocation(getX()+getWidth(), getY());

            playlistPanel = new JPanel();
            playlistPanel.setLayout(new BoxLayout(playlistPanel, BoxLayout.Y_AXIS));
            playlistPanel.setBackground(Color.BLACK);

            playlistScrollPane = new JScrollPane(playlistPanel);
            playlistScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
            playlistScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

            getContentPane().add(playlistScrollPane);
        }
    }
    final private ImageIcon FlacAlbumImageExtractor(String filePath){
        try{
            AudioFile audioFile = AudioFileIO.read(new File(filePath));
            Tag tag = audioFile.getTag();
            if (tag != null && tag.hasField(FieldKey.COVER_ART)) {
                Artwork artwork = tag.getFirstArtwork();
                byte[] imageData = artwork.getBinaryData();
                return new ImageIcon(imageData);
            }
        }catch(Exception ignore){}
        return null;
    }
    final private void doAudioDecoderWorkerLoop() {
        while (true) {
            try {
                doWorkerIteration();
            } catch (IOException|LineUnavailableException e) {
                String prefix;
                if (e instanceof FlacDecoder.FormatException) prefix = "FLAC format";
                else if (e instanceof IOException) prefix = "I/O";
                else if (e instanceof LineUnavailableException) prefix = "Line unavailable";
                else prefix = "General";
                final String msg = prefix + " exception: " + e.getMessage();
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        //JOptionPane.showMessageDialog(frame, msg);
                        setSliderPosition(0);
                        playbackSlider.setEnabled(false);
                    }
                });
                try {
                    closeFile();
                } catch (IOException ee) {
                    ee.printStackTrace();
                    System.exit(1);
                }
            } catch (InterruptedException e) {}
        }
    }
    final private void setSliderPosition(final double t) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                if (!playbackSlider.getValueIsAdjusting())
                playbackSlider.setValue((int)Math.round(t * playbackSlider.getMaximum()));
            }
        });
    }
    final private void closeFile() throws IOException {
        if (dec != null) {
            dec.close();
            dec = null;
        }
        if (line != null) {
            line.close();
            line = null;
        }
    }
    @SuppressWarnings("serial")
    public class FormatException extends IOException {
        public FormatException(String msg) {
            super(msg);
        }
    }
    final private void doWorkerIteration() throws IOException, LineUnavailableException, InterruptedException {
        // Take request from shared variables
        File openReq;
        double seekReq;
        synchronized(lock) {
            openReq = openRequest;
            openRequest = null;
            seekReq = seekRequest;
            seekRequest = -1;
            while (actionPerformed == false) {
                lock.wait(); // Wait until notified
            }
        }
        // Open or switch files, and start audio line
        if (openReq != null) {
            seekReq = -1;
            closeFile();
            dec = new FlacDecoder(openReq);
            if (dec.numSamples == 0)
                throw new FormatException("Unknown audio length");
            AudioFormat format = new AudioFormat(dec.sampleRate, dec.sampleDepth, dec.numChannels, true, false);
            line = (SourceDataLine)AudioSystem.getLine(new DataLine.Info(SourceDataLine.class, format));
            line.open(format);
            line.start();
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    //frame.setTitle(title);
                    playbackSlider.setEnabled(true);
                }
            });
            clipStartTime = 0;
        } else if (dec == null) {
            synchronized(lock) {
                while (openRequest == null)
                    lock.wait();
            }
            return;
        }
        
        // Decode next audio block, or seek and decode
        long[][] samples = null;
        if (seekReq == -1) {
            Object[] temp = dec.readNextBlock();
            if (temp != null)
                samples = (long[][])temp[0];
        } else {
            long samplePos = Math.round(seekReq * dec.numSamples);
            samples = dec.seekAndReadBlock(samplePos);
            line.flush();
            clipStartTime = line.getMicrosecondPosition() - Math.round(samplePos * 1e6 / dec.sampleRate);
        }
        
        // Set display position
        double timePos = (line.getMicrosecondPosition() - clipStartTime) / 1e6;
        setSliderPosition(timePos * dec.sampleRate / dec.numSamples);
        
        // Wait when end of stream reached
        if (samples == null) {
            synchronized(lock) {
                while (openRequest == null && seekRequest == -1)
                    lock.wait();
            }
            return;
        }
        
        // Convert samples to channel-interleaved bytes in little endian
        int bytesPerSample = dec.sampleDepth / 8;
        byte[] sampleBytes = new byte[samples[0].length * samples.length * bytesPerSample];
        for (int i = 0, k = 0; i < samples[0].length; i++) {
            for (int ch = 0; ch < samples.length; ch++) {
                long val = samples[ch][i];
                for (int j = 0; j < bytesPerSample; j++, k++)
                    sampleBytes[k] = (byte)(val >>> (j << 3));
            }
        }
        line.write(sampleBytes, 0, sampleBytes.length);
    }
    final private class FlacDecoder {
         
        private Stream input;
        private long metadataEndPos;
        
        public int sampleRate = -1;
        public int numChannels = -1;
        public int sampleDepth = -1;
        public long numSamples = -1;
        public int constantBlockSize = -1;
        
        
        public FlacDecoder(File file) throws IOException {
            input = new Stream(file);
            if (input.readUint(32) != 0x664C6143)
                throw new FormatException("Invalid magic string");
            
            // Handle metadata blocks
            for (boolean last = false; !last; ) {
                last = input.readUint(1) != 0;
                int type = input.readUint(7);
                int length = input.readUint(24);
                if (type == 0) {  // Parse stream info block
                    int minBlockSize = input.readUint(16);
                    int maxBlockSize = input.readUint(16);
                    if (minBlockSize == maxBlockSize)
                        constantBlockSize = minBlockSize;
                    input.readUint(24);
                    input.readUint(24);
                    sampleRate = input.readUint(20);
                    numChannels = input.readUint(3) + 1;
                    sampleDepth = input.readUint(5) + 1;
                    numSamples = (long)input.readUint(18) << 18 | input.readUint(18);
                    for (int i = 0; i < 16; i++)
                        input.readUint(8);
                } else {  // Skip other blocks
                    for (int i = 0; i < length; i++)
                        input.readUint(8);
                }
            }
            if (sampleRate == -1)
                throw new FormatException("Stream info metadata block absent");
            metadataEndPos = input.getPosition();
        }
        
        public void close() throws IOException {
            input.close();
        }
        
        public long[][] seekAndReadBlock(long samplePos) throws IOException {
            // Binary search to find a frame slightly before requested position
            long startFilePos = metadataEndPos;
            long endFilePos = input.getLength();
            long curSamplePos = 0;
            while (endFilePos - startFilePos > 100000) {
                long middle = (startFilePos + endFilePos) / 2;
                long[] offsets = findNextDecodableFrame(middle);
                if (offsets == null || offsets[1] > samplePos)
                    endFilePos = middle;
                else {
                    startFilePos = offsets[0];
                    curSamplePos = offsets[1];
                }
            }
            
            input.seekTo(startFilePos);
            while (true) {
                Object[] temp = readNextBlock();
                if (temp == null)
                    return null;
                long[][] samples = (long[][])temp[0];
                int blockSize = samples[0].length;
                long nextSamplePos = curSamplePos + blockSize;
                if (nextSamplePos > samplePos) {
                    long[][] result = new long[samples.length][];
                    for (int ch = 0; ch < numChannels; ch++)
                        result[ch] = Arrays.copyOfRange(samples[ch], (int)(samplePos - curSamplePos), blockSize);
                    return result;
                }
                curSamplePos = nextSamplePos;
            }
        }
        
        
        // Returns (filePosition, sampleOffset) or null.
        private long[] findNextDecodableFrame(long filePos) throws IOException {
            while (true) {
                input.seekTo(filePos);
                int state = 0;
                while (true) {
                    int b = input.readByte();
                    if (b == -1)
                        return null;
                    else if (b == 0xFF)
                        state = 1;
                    else if (state == 1 && (b & 0xFE) == 0xF8)
                        break;
                    else
                        state = 0;
                }
                filePos = input.getPosition() - 2;
                input.seekTo(filePos);
                try {
                    Object[] temp = readNextBlock();
                    if (temp == null)
                        return null;
                    else
                        return new long[]{filePos, (long)temp[1]};
                } catch (FormatException e) {
                    filePos += 2;
                }
            }
        }
        
        
        // Returns (long[][] blockSamples, long sampleOffsetAtStartOfBlock)
        // if a block is decoded, or null if the end of stream is reached.
        public Object[] readNextBlock() throws IOException {
            // Find next sync code
            int byteVal = input.readByte();
            if (byteVal == -1)
                return null;
            int sync = byteVal << 6 | input.readUint(6);
            if (sync != 0x3FFE)
                throw new FormatException("Sync code expected");
            if (input.readUint(1) != 0)
                throw new FormatException("Reserved bit");
            int blockStrategy = input.readUint(1);
            
            // Read numerous header fields, and ignore some of them
            int blockSizeCode = input.readUint(4);
            int sampleRateCode = input.readUint(4);
            int chanAsgn = input.readUint(4);
            switch (input.readUint(3)) {
                case 1:  if (sampleDepth !=  8) throw new FormatException("Sample depth mismatch");  break;
                case 2:  if (sampleDepth != 12) throw new FormatException("Sample depth mismatch");  break;
                case 4:  if (sampleDepth != 16) throw new FormatException("Sample depth mismatch");  break;
                case 5:  if (sampleDepth != 20) throw new FormatException("Sample depth mismatch");  break;
                case 6:  if (sampleDepth != 24) throw new FormatException("Sample depth mismatch");  break;
                default:  throw new FormatException("Reserved/invalid sample depth");
            }
            if (input.readUint(1) != 0)
                throw new FormatException("Reserved bit");
            
            byteVal = input.readUint(8);
            long rawPosition;
            if (byteVal < 0x80)
                rawPosition = byteVal;
            else {
                int rawPosNumBytes = Integer.numberOfLeadingZeros(~(byteVal << 24)) - 1;
                rawPosition = byteVal & (0x3F >>> rawPosNumBytes);
                for (int i = 0; i < rawPosNumBytes; i++)
                    rawPosition = (rawPosition << 6) | (input.readUint(8) & 0x3F);
            }
            
            int blockSize;
            if (blockSizeCode == 1)
                blockSize = 192;
            else if (2 <= blockSizeCode && blockSizeCode <= 5)
                blockSize = 576 << (blockSizeCode - 2);
            else if (blockSizeCode == 6)
                blockSize = input.readUint(8) + 1;
            else if (blockSizeCode == 7)
                blockSize = input.readUint(16) + 1;
            else if (8 <= blockSizeCode && blockSizeCode <= 15)
                blockSize = 256 << (blockSizeCode - 8);
            else
                throw new FormatException("Reserved block size");
            
            if (sampleRateCode == 12)
                input.readUint(8);
            else if (sampleRateCode == 13 || sampleRateCode == 14)
                input.readUint(16);
            
            input.readUint(8);
            
            // Decode each channel's subframe, then skip footer
            long[][] samples = decodeSubframes(blockSize, sampleDepth, chanAsgn);
            input.alignToByte();
            input.readUint(16);
            return new Object[]{samples, rawPosition * (blockStrategy == 0 ? constantBlockSize : 1)};
        }
        
        
        private long[][] decodeSubframes(int blockSize, int sampleDepth, int chanAsgn) throws IOException {
            long[][] result;
            if (0 <= chanAsgn && chanAsgn <= 7) {
                result = new long[chanAsgn + 1][blockSize];
                for (int ch = 0; ch < result.length; ch++)
                    decodeSubframe(sampleDepth, result[ch]);
            } else if (8 <= chanAsgn && chanAsgn <= 10) {
                result = new long[2][blockSize];
                decodeSubframe(sampleDepth + (chanAsgn == 9 ? 1 : 0), result[0]);
                decodeSubframe(sampleDepth + (chanAsgn == 9 ? 0 : 1), result[1]);
                if (chanAsgn == 8) {
                    for (int i = 0; i < blockSize; i++)
                        result[1][i] = result[0][i] - result[1][i];
                } else if (chanAsgn == 9) {
                    for (int i = 0; i < blockSize; i++)
                        result[0][i] += result[1][i];
                } else if (chanAsgn == 10) {
                    for (int i = 0; i < blockSize; i++) {
                        long side = result[1][i];
                        long right = result[0][i] - (side >> 1);
                        result[1][i] = right;
                        result[0][i] = right + side;
                    }
                }
            } else
                throw new FormatException("Reserved channel assignment");
            return result;
        }
        
        
        private void decodeSubframe(int sampleDepth, long[] result) throws IOException {
            if (input.readUint(1) != 0)
                throw new FormatException("Invalid padding bit");
            int type = input.readUint(6);
            int shift = input.readUint(1);
            if (shift == 1) {
                while (input.readUint(1) == 0)
                    shift++;
            }
            sampleDepth -= shift;
            
            if (type == 0)  // Constant coding
                Arrays.fill(result, 0, result.length, input.readSignedInt(sampleDepth));
            else if (type == 1) {  // Verbatim coding
                for (int i = 0; i < result.length; i++)
                    result[i] = input.readSignedInt(sampleDepth);
            } else if (8 <= type && type <= 12 || 32 <= type && type <= 63) {
                int predOrder;
                int[] lpcCoefs;
                int lpcShift;
                if (type <= 12) {  // Fixed prediction
                    predOrder = type - 8;
                    for (int i = 0; i < predOrder; i++)
                        result[i] = input.readSignedInt(sampleDepth);
                    lpcCoefs = FIXED_PREDICTION_COEFFICIENTS[predOrder];
                    lpcShift = 0;
                } else {  // Linear predictive coding
                    predOrder = type - 31;
                    for (int i = 0; i < predOrder; i++)
                        result[i] = input.readSignedInt(sampleDepth);
                    int precision = input.readUint(4) + 1;
                    lpcShift = input.readSignedInt(5);
                    lpcCoefs = new int[predOrder];
                    for (int i = 0; i < predOrder; i++)
                        lpcCoefs[i] = input.readSignedInt(precision);
                }
                decodeRiceResiduals(predOrder, result);
                for (int i = predOrder; i < result.length; i++) {  // LPC restoration
                    long sum = 0;
                    for (int j = 0; j < lpcCoefs.length; j++)
                        sum += result[i - 1 - j] * lpcCoefs[j];
                    result[i] += sum >> lpcShift;
                }
            } else
                throw new FormatException("Reserved subframe type");
            
            for (int i = 0; i < result.length; i++)
                result[i] <<= shift;
        }
        
        
        private void decodeRiceResiduals(int warmup, long[] result) throws IOException {
            int method = input.readUint(2);
            if (method >= 2)
                throw new FormatException("Reserved residual coding method");
            int paramBits = method == 0 ? 4 : 5;
            int escapeParam = method == 0 ? 0xF : 0x1F;
            int partitionOrder = input.readUint(4);
            int numPartitions = 1 << partitionOrder;
            if (result.length % numPartitions != 0)
                throw new FormatException("Block size not divisible by number of Rice partitions");
            int partitionSize = result.length / numPartitions;
            
            for (int i = 0; i < numPartitions; i++) {
                int start = i * partitionSize + (i == 0 ? warmup : 0);
                int end = (i + 1) * partitionSize;
                int param = input.readUint(paramBits);
                if (param < escapeParam) {
                    for (int j = start; j < end; j++) {  // Read Rice signed integers
                        long val = 0;
                        while (input.readUint(1) == 0)
                            val++;
                        val = (val << param) | input.readUint(param);
                        result[j] = (val >>> 1) ^ -(val & 1);
                    }
                } else {
                    int numBits = input.readUint(5);
                    for (int j = start; j < end; j++)
                        result[j] = input.readSignedInt(numBits);
                }
            }
        }
        
        
        private final int[][] FIXED_PREDICTION_COEFFICIENTS = {
            {},
            {1},
            {2, -1},
            {3, -3, 1},
            {4, -6, 4, -1},
        };
        
        
        
        // Provides low-level bit/byte reading of a file.
        private final class Stream {
            
            private RandomAccessFile raf;
            private long bytePosition;
            private InputStream byteBuffer;
            private long bitBuffer;
            private int bitBufferLen;
            
            public Stream(File file) throws IOException {
                raf = new RandomAccessFile(file, "r");
                seekTo(0);
            }
            
            
            public void close() throws IOException {
                raf.close();
            }
            
            public long getLength() throws IOException {
                return raf.length();
            }
            
            public long getPosition() {
                return bytePosition;
            }
            
            public void seekTo(long pos) throws IOException {
                raf.seek(pos);
                bytePosition = pos;
                byteBuffer = new BufferedInputStream(new InputStream() {
                    public int read() throws IOException {
                        return raf.read();
                    }
                    public int read(byte[] b, int off, int len) throws IOException {
                        return raf.read(b, off, len);
                    }
                });
                bitBufferLen = 0;
            }
            
            public int readByte() throws IOException {
                if (bitBufferLen >= 8)
                    return readUint(8);
                else {
                    int result = byteBuffer.read();
                    if (result != -1)
                        bytePosition++;
                    return result;
                }
            }
            
            public int readUint(int n) throws IOException {
                while (bitBufferLen < n) {
                    int temp = byteBuffer.read();
                    if (temp == -1)
                        throw new EOFException();
                    bytePosition++;
                    bitBuffer = (bitBuffer << 8) | temp;
                    bitBufferLen += 8;
                }
                bitBufferLen -= n;
                int result = (int)(bitBuffer >>> bitBufferLen);
                if (n < 32)
                    result &= (1 << n) - 1;
                return result;
            }
            
            public int readSignedInt(int n) throws IOException {
                return (readUint(n) << (32 - n)) >> (32 - n);
            }
            
            public void alignToByte() {
                bitBufferLen -= bitBufferLen % 8;
            }
            
        }
        
        
        
        // Thrown when non-conforming FLAC data is read.
        @SuppressWarnings("serial")
        public class FormatException extends IOException {
            public FormatException(String msg) {
                super(msg);
            }
        }
        
    }

}
