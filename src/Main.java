
import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater (new Runnable() {
            @Override
            public void run () {
                try {
                    new MainFrame().setVisible(true);
                } catch (Exception e) {e.printStackTrace();}
            }
        });
    }
}
