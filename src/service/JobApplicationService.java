package service;

import exception.ApplicationNotFoundException;
import java.sql.SQLException;
import java.util.List;
import model.ApplicationStatus;
import model.JobApplication;
import repository.JobApplicationRepository;

public class JobApplicationService {
    private final JobApplicationRepository repository;

    public JobApplicationService(JobApplicationRepository repository) {
        this.repository = repository;
    }

    public JobApplication addApplication(String companyName, String position,
                                         String location) throws SQLException {
        companyName = validateRequiredText(companyName, "Company name");
        position = validateRequiredText(position, "Position");
        location = validateRequiredText(location, "Location");
        return repository.save(companyName, position, location, ApplicationStatus.SAVED);
    }

    public List<JobApplication> getAllApplications() throws SQLException {
        return repository.findAll();
    }

    public JobApplication findById(int id)
            throws ApplicationNotFoundException, SQLException {
        validateId(id);
        JobApplication application = repository.findById(id);
        if (application == null) {
            throw new ApplicationNotFoundException(id);
        }
        return application;
    }

    public void changeApplicationStatus(int id, ApplicationStatus newStatus)
            throws ApplicationNotFoundException, SQLException {
        validateId(id);
        if (newStatus == null) {
            throw new IllegalArgumentException("Status must not be null.");
        }
        if (!repository.updateStatus(id, newStatus)) {
            throw new ApplicationNotFoundException(id);
        }
    }

    public void editApplicationDetails(int id, String companyName, String position,
                                       String location)
            throws ApplicationNotFoundException, SQLException {
        validateId(id);
        companyName = validateRequiredText(companyName, "Company name");
        position = validateRequiredText(position, "Position");
        location = validateRequiredText(location, "Location");
        if (!repository.updateDetails(id, companyName, position, location)) {
            throw new ApplicationNotFoundException(id);
        }
    }

    public void deleteApplication(int id)
            throws ApplicationNotFoundException, SQLException {
        validateId(id);
        if (!repository.deleteById(id)) {
            throw new ApplicationNotFoundException(id);
        }
    }

    private void validateId(int id) {
        if (id <= 0) {
            throw new IllegalArgumentException("Application ID must be positive.");
        }
    }

    private String validateRequiredText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be null or blank.");
        }
        return value.strip();
    }
}
