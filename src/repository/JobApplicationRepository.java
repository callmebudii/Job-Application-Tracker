package repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import model.ApplicationStatus;
import model.JobApplication;
import util.DatabaseConnection;

public class JobApplicationRepository {
    public JobApplication save(String companyName, String position, String location,
                               ApplicationStatus status) throws SQLException {
        String sql = "INSERT INTO job_applications (company_name, position, location, status) "
                + "VALUES (?, ?, ?, ?) RETURNING id, company_name, position, location, status";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, companyName);
            statement.setString(2, position);
            statement.setString(3, location);
            statement.setString(4, status.name());
            try (ResultSet rows = statement.executeQuery()) {
                if (rows.next()) {
                    return mapRow(rows);
                }
                throw new SQLException("Insert did not return an application.");
            }
        }
    }

    public List<JobApplication> findAll() throws SQLException {
        String sql = "SELECT id, company_name, position, location, status "
                + "FROM job_applications ORDER BY id";
        List<JobApplication> applications = new ArrayList<>();
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                applications.add(mapRow(rows));
            }
        }
        return applications;
    }

    public JobApplication findById(int id) throws SQLException {
        String sql = "SELECT id, company_name, position, location, status "
                + "FROM job_applications WHERE id = ?";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? mapRow(rows) : null;
            }
        }
    }

    public boolean updateStatus(int id, ApplicationStatus status) throws SQLException {
        String sql = "UPDATE job_applications SET status = ? WHERE id = ?";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status.name());
            statement.setInt(2, id);
            return statement.executeUpdate() == 1;
        }
    }

    public boolean updateDetails(int id, String companyName, String position,
                                 String location) throws SQLException {
        String sql = "UPDATE job_applications SET company_name = ?, position = ?, location = ? "
                + "WHERE id = ?";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, companyName);
            statement.setString(2, position);
            statement.setString(3, location);
            statement.setInt(4, id);
            return statement.executeUpdate() == 1;
        }
    }

    public boolean deleteById(int id) throws SQLException {
        String sql = "DELETE FROM job_applications WHERE id = ?";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            return statement.executeUpdate() == 1;
        }
    }

    private JobApplication mapRow(ResultSet rows) throws SQLException {
        ApplicationStatus status;
        try {
            status = ApplicationStatus.valueOf(rows.getString("status"));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new SQLException("Database row contains an invalid application status.", exception);
        }
        return new JobApplication(rows.getInt("id"), rows.getString("company_name"),
                rows.getString("position"), rows.getString("location"), status);
    }
}
