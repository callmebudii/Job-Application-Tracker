package model;

public class JobApplication {
    private final int id;
    private final String companyName;
    private final String position;
    private final String location;
    private ApplicationStatus status;

    public JobApplication(int id, String companyName, String position,
                          String location, ApplicationStatus status) {
        this.id = id;
        this.companyName = companyName;
        this.position = position;
        this.location = location;
        setStatus(status);
    }

    public int getId() {
        return id;
    }

    public String getCompanyName() {
        return companyName;
    }

    public String getPosition() {
        return position;
    }

    public String getLocation() {
        return location;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    public void setStatus(ApplicationStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("Status must not be null.");
        }
        this.status = status;
    }

    @Override
    public String toString() {
        return "JobApplication{id=" + id
                + ", companyName='" + companyName + '\''
                + ", position='" + position + '\''
                + ", location='" + location + '\''
                + ", status=" + status + '}';
    }
}
