package main;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import model.ApplicationStatus;
import repository.JobApplicationRepository;
import service.JobApplicationService;

public final class GuiIntegrationTest {
    private static TrackerFrame frame;

    public static void main(String[] args) throws Exception {
        String url = System.getenv("DB_URL");
        if (url == null || !url.matches("jdbc:postgresql://[^/]+/[A-Za-z0-9_]+_test")) {
            throw new IllegalStateException("A separate database ending in _test is required.");
        }
        var repo = new JobApplicationRepository();
        String marker = "GUI test " + UUID.randomUUID();
        try {
            SwingUtilities.invokeAndWait(() -> {
                frame = new TrackerFrame(new JobApplicationService(repo));
                frame.setVisible(true);
            });
            awaitIdle();
            SwingUtilities.invokeAndWait(() -> {
                frame.company.setText(marker);
                frame.position.setText("Java Intern"); frame.location.setText("Berlin");
                frame.add.doClick();
            });
            awaitIdle();
            var record = repo.findAll().stream().filter(a -> marker.equals(a.getCompanyName())).findFirst().orElseThrow();
            int id = record.getId();
            SwingUtilities.invokeAndWait(() -> {
                if (!frame.save.isEnabled()) throw new AssertionError("New record not selected");
                frame.position.setText("Java Developer"); frame.save.doClick();
            });
            awaitIdle();
            if (!repo.findById(id).getPosition().equals("Java Developer")) throw new AssertionError("GUI save failed");
            SwingUtilities.invokeAndWait(() -> {
                frame.status.setSelectedItem(ApplicationStatus.INTERVIEW); frame.changeStatus.doClick();
            });
            awaitIdle();
            if (repo.findById(id).getStatus() != ApplicationStatus.INTERVIEW) throw new AssertionError("GUI status failed");
            SwingUtilities.invokeAndWait(() -> {
                frame.search.setText(marker);
                frame.statusFilter.setSelectedItem("SAVED");
                if (frame.table.getRowCount() != 0) throw new AssertionError("Status filter failed");
                frame.statusFilter.setSelectedItem("INTERVIEW");
                if (frame.table.getRowCount() != 1) throw new AssertionError("Combined filter failed");
                frame.table.setRowSelectionInterval(0, 0);
                frame.company.setText(" "); frame.save.doClick();
            });
            awaitIdle();
            if (!repo.findById(id).getCompanyName().equals(marker)) throw new AssertionError("Blank input changed database");
            SwingUtilities.invokeAndWait(() -> {
                if (!frame.message.getText().contains("blank")) throw new AssertionError("Validation feedback missing");
                frame.company.setText(marker);
                frame.search.setText(""); frame.statusFilter.setSelectedItem("All statuses");
                if (args.length > 0) {
                    BufferedImage image = new BufferedImage(frame.getContentPane().getWidth(),
                            frame.getContentPane().getHeight(), BufferedImage.TYPE_INT_RGB);
                    var graphics = image.createGraphics();
                    frame.getContentPane().printAll(graphics); graphics.dispose();
                    try { ImageIO.write(image, "png", new File(args[0])); }
                    catch (Exception exception) { throw new RuntimeException(exception); }
                }
            });
            confirmDelete(javax.swing.JOptionPane.NO_OPTION, id);
            if (repo.findById(id) == null) throw new AssertionError("Cancelled deletion removed record");
            confirmDelete(javax.swing.JOptionPane.YES_OPTION, id);
            awaitIdle();
            if (repo.findById(id) != null) throw new AssertionError("Confirmed deletion failed");
            System.out.println("PASS: GUI load, add, edit, status, search/filter, validation, delete confirmation and worker completion");
        } finally {
            for (var record : repo.findAll()) {
                if (marker.equals(record.getCompanyName())) repo.deleteById(record.getId());
            }
            if (frame != null) SwingUtilities.invokeAndWait(() -> frame.dispose());
        }
    }

    private static void confirmDelete(int choice, int id) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (int row = 0; row < frame.table.getRowCount(); row++) {
                if (Integer.valueOf(id).equals(frame.table.getValueAt(row, 0))) frame.table.setRowSelectionInterval(row, row);
            }
            javax.swing.Timer timer = new javax.swing.Timer(100, event -> {
                for (java.awt.Window window : java.awt.Window.getWindows()) {
                    if (window instanceof javax.swing.JDialog dialog && dialog.isVisible()) {
                        for (java.awt.Component component : dialog.getContentPane().getComponents()) {
                            if (component instanceof javax.swing.JOptionPane pane) pane.setValue(choice);
                        }
                    }
                }
            });
            timer.start();
            try { frame.delete.doClick(); } finally { timer.stop(); }
        });
    }

    private static void awaitIdle() throws Exception {
        long deadline = System.nanoTime() + 20_000_000_000L;
        while (System.nanoTime() < deadline) {
            boolean[] busy = {true};
            SwingUtilities.invokeAndWait(() -> busy[0] = frame.busy);
            if (!busy[0]) return;
            Thread.sleep(50);
        }
        throw new AssertionError("GUI operation timed out");
    }
}
