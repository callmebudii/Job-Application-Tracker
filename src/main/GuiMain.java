package main;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import repository.JobApplicationRepository;
import service.JobApplicationService;

public final class GuiMain {
    private GuiMain() { }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // Swing's default look and feel is also supported.
            }
            new TrackerFrame(new JobApplicationService(new JobApplicationRepository())).setVisible(true);
        });
    }
}
