package main;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import model.ApplicationStatus;
import model.JobApplication;
import service.JobApplicationService;

/** Swing presents data; the existing service owns validation and database operations. */
public final class TrackerFrame extends JFrame {
    private final JobApplicationService service;
    private final DefaultTableModel model = new DefaultTableModel(
            new String[]{"ID", "Company", "Position", "Location", "Status"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
        @Override public Class<?> getColumnClass(int column) { return column == 0 ? Integer.class : String.class; }
    };
    final JTable table = new JTable(model);
    final JTextField company = new JTextField();
    final JTextField position = new JTextField();
    final JTextField location = new JTextField();
    final JComboBox<ApplicationStatus> status = new JComboBox<>(ApplicationStatus.values());
    final JTextField search = new JTextField();
    final JComboBox<String> statusFilter = new JComboBox<>();
    final JButton add = new JButton("Add application");
    final JButton save = new JButton("Save details");
    final JButton changeStatus = new JButton("Set status");
    final JButton delete = new JButton("Delete application");
    private final JButton clear = new JButton("New / Clear");
    private final JButton refresh = new JButton("Refresh");
    final JLabel message = new JLabel("Loading applications...");
    private final JLabel editorTitle = new JLabel("New application");
    private final JLabel count = new JLabel("0 applications");
    private final TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(model);
    private List<JobApplication> applications = new ArrayList<>();
    volatile boolean busy;
    private boolean replacingRows;

    public TrackerFrame(JobApplicationService service) {
        super("Job Application Tracker");
        this.service = service;
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(920, 600));
        setSize(1150, 700);
        setLocationRelativeTo(null);
        JPanel root = new JPanel(new BorderLayout(20, 20));
        root.setBorder(new EmptyBorder(24, 24, 18, 24));
        root.setBackground(new Color(245, 247, 250));
        setContentPane(root);

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel title = new JLabel("Job Application Tracker");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 26f));
        header.add(title, BorderLayout.NORTH);
        header.add(new JLabel("Keep your applications and next steps in one place."), BorderLayout.SOUTH);
        root.add(header, BorderLayout.NORTH);

        JPanel listing = new JPanel(new BorderLayout(10, 12));
        listing.setOpaque(false);
        JPanel filters = new JPanel(new BorderLayout(10, 0));
        filters.setOpaque(false);
        JPanel searchBox = new JPanel(new BorderLayout(8, 0));
        searchBox.setOpaque(false);
        searchBox.add(new JLabel("Search"), BorderLayout.WEST);
        searchBox.add(search, BorderLayout.CENTER);
        filters.add(searchBox, BorderLayout.CENTER);
        statusFilter.addItem("All statuses");
        for (ApplicationStatus value : ApplicationStatus.values()) statusFilter.addItem(value.name());
        JPanel filterActions = new JPanel(new BorderLayout(8, 0));
        filterActions.setOpaque(false);
        filterActions.add(statusFilter, BorderLayout.CENTER);
        filterActions.add(refresh, BorderLayout.EAST);
        filters.add(filterActions, BorderLayout.EAST);
        listing.add(filters, BorderLayout.NORTH);
        table.setRowHeight(32);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowSorter(sorter);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);
        table.getColumnModel().getColumn(0).setMaxWidth(65);
        listing.add(new JScrollPane(table), BorderLayout.CENTER);
        listing.add(count, BorderLayout.SOUTH);

        JPanel editor = new JPanel(new BorderLayout(0, 16));
        editor.setBorder(new EmptyBorder(18, 18, 18, 18));
        editor.setPreferredSize(new Dimension(300, 450));
        editorTitle.setFont(editorTitle.getFont().deriveFont(Font.BOLD, 18f));
        editor.add(editorTitle, BorderLayout.NORTH);
        JPanel fields = new JPanel(new GridLayout(0, 1, 0, 6));
        fields.add(new JLabel("Company *")); fields.add(company);
        fields.add(new JLabel("Position *")); fields.add(position);
        fields.add(new JLabel("Location *")); fields.add(location);
        fields.add(new JLabel("Status (for selected application)")); fields.add(status);
        add.setToolTipText("New applications start with status SAVED.");
        JPanel fieldHolder = new JPanel(new BorderLayout());
        fieldHolder.add(fields, BorderLayout.NORTH);
        JScrollPane formScroll = new JScrollPane(fieldHolder);
        formScroll.setBorder(null);
        formScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        editor.add(formScroll, BorderLayout.CENTER);
        JPanel actions = new JPanel(new GridLayout(0, 1, 0, 8));
        actions.add(add); actions.add(save); actions.add(changeStatus); actions.add(clear); actions.add(delete);
        editor.add(actions, BorderLayout.SOUTH);
        JPanel content = new JPanel(new BorderLayout(20, 0));
        content.setOpaque(false);
        content.add(listing, BorderLayout.CENTER);
        content.add(editor, BorderLayout.EAST);
        root.add(content, BorderLayout.CENTER);
        root.add(message, BorderLayout.SOUTH);

        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && !replacingRows) populateSelection();
        });
        search.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { filter(); }
            public void removeUpdate(DocumentEvent e) { filter(); }
            public void changedUpdate(DocumentEvent e) { filter(); }
        });
        statusFilter.addActionListener(e -> filter());
        refresh.addActionListener(e -> load());
        clear.addActionListener(e -> clearEditor());
        add.addActionListener(e -> addApplication());
        save.addActionListener(e -> saveDetails());
        changeStatus.addActionListener(e -> updateStatus());
        delete.addActionListener(e -> deleteApplication());
        updateControls();
        load();
    }

    private Integer selectedId() {
        int row = table.getSelectedRow();
        return row < 0 ? null : (Integer) model.getValueAt(table.convertRowIndexToModel(row), 0);
    }

    private void populateSelection() {
        Integer id = selectedId();
        if (id == null) { clearFields(); return; }
        applications.stream().filter(a -> a.getId() == id).findFirst().ifPresent(a -> {
            company.setText(a.getCompanyName()); position.setText(a.getPosition());
            location.setText(a.getLocation()); status.setSelectedItem(a.getStatus());
            editorTitle.setText("Application #" + id);
        });
        updateControls();
    }

    private void clearEditor() { table.clearSelection(); clearFields(); }

    private void clearFields() {
        company.setText(""); position.setText(""); location.setText("");
        status.setSelectedItem(ApplicationStatus.SAVED);
        editorTitle.setText("New application");
        updateControls();
    }

    private void updateControls() {
        boolean selected = selectedId() != null;
        add.setEnabled(!busy && !selected);
        save.setEnabled(!busy && selected); changeStatus.setEnabled(!busy && selected);
        delete.setEnabled(!busy && selected); clear.setEnabled(!busy); refresh.setEnabled(!busy);
        company.setEnabled(!busy); position.setEnabled(!busy); location.setEnabled(!busy);
        status.setEnabled(!busy && selected); table.setEnabled(!busy);
        search.setEnabled(!busy); statusFilter.setEnabled(!busy);
    }

    private void filter() {
        String term = search.getText().strip().toLowerCase(Locale.ROOT);
        String wanted = (String) statusFilter.getSelectedItem();
        sorter.setRowFilter(new RowFilter<DefaultTableModel, Integer>() {
            public boolean include(Entry<? extends DefaultTableModel, ? extends Integer> entry) {
                if (!"All statuses".equals(wanted) && !wanted.equals(entry.getStringValue(4))) return false;
                for (int column = 0; column < 5; column++) {
                    if (entry.getStringValue(column).toLowerCase(Locale.ROOT).contains(term)) return true;
                }
                return false;
            }
        });
        count.setText(table.getRowCount() + " shown / " + model.getRowCount() + " total");
    }

    private void replaceRows(List<JobApplication> rows, Integer preferredId) {
        replacingRows = true;
        try {
            applications = rows;
            model.setRowCount(0);
            for (JobApplication a : rows) model.addRow(new Object[]{a.getId(), a.getCompanyName(),
                    a.getPosition(), a.getLocation(), a.getStatus().name()});
            filter();
            if (preferredId != null) {
                for (int row = 0; row < model.getRowCount(); row++) {
                    if (preferredId.equals(model.getValueAt(row, 0))) {
                        int view = table.convertRowIndexToView(row);
                        if (view >= 0) table.setRowSelectionInterval(view, view);
                        break;
                    }
                }
            }
        } finally { replacingRows = false; }
        populateSelection();
    }

    private void load() {
        Integer id = selectedId();
        task("Loading applications...", () -> {
            List<JobApplication> rows = service.getAllApplications();
            return () -> { replaceRows(rows, id); message.setText(rows.isEmpty()
                    ? "No applications yet. Fill in the form to add your first one." : "Applications refreshed."); };
        });
    }

    private void addApplication() {
        String c = company.getText(), p = position.getText(), l = location.getText();
        task("Adding application...", () -> {
            JobApplication created = service.addApplication(c, p, l);
            return reloadAfterMutation(created.getId(), "Application added.");
        });
    }

    private void saveDetails() {
        Integer id = selectedId(); if (id == null) return;
        String c = company.getText(), p = position.getText(), l = location.getText();
        task("Saving details...", () -> {
            service.editApplicationDetails(id, c, p, l);
            return reloadAfterMutation(id, "Details saved.");
        });
    }

    private void updateStatus() {
        Integer id = selectedId(); if (id == null) return;
        ApplicationStatus value = (ApplicationStatus) status.getSelectedItem();
        task("Updating status...", () -> {
            service.changeApplicationStatus(id, value);
            return reloadAfterMutation(id, "Status updated.");
        });
    }

    private void deleteApplication() {
        Integer id = selectedId(); if (id == null) return;
        if (JOptionPane.showConfirmDialog(this, "Delete application #" + id + "? This cannot be undone.",
                "Delete application", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) return;
        task("Deleting application...", () -> {
            service.deleteApplication(id);
            return reloadAfterMutation(null, "Application deleted.");
        });
    }

    private Runnable reloadAfterMutation(Integer id, String success) {
        try {
            List<JobApplication> rows = service.getAllApplications();
            return () -> { replaceRows(rows, id); message.setText(success); };
        } catch (java.sql.SQLException exception) {
            // The write committed; don't misreport it as failed or encourage a duplicate insert.
            return () -> { clearEditor(); message.setText(success + " Refresh failed; click Refresh to reload."); };
        }
    }

    @FunctionalInterface private interface BackgroundTask { Runnable run() throws Exception; }

    private void task(String progress, BackgroundTask operation) {
        if (busy) return;
        busy = true; updateControls(); message.setText(progress);
        new SwingWorker<Runnable, Void>() {
            protected Runnable doInBackground() throws Exception { return operation.run(); }
            protected void done() {
                try {
                    get().run();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt(); message.setText("Operation interrupted. Refresh to check the result.");
                } catch (ExecutionException exception) {
                    Throwable cause = exception.getCause();
                    String text = cause instanceof java.sql.SQLException
                            ? "Database unavailable. Check PostgreSQL, connection settings, driver and table setup; then Refresh."
                            : cause.getMessage();
                    message.setText(text == null ? "Operation failed." : text);
                } finally { busy = false; updateControls(); }
            }
        }.execute();
    }
}
