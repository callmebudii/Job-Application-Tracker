import exception.ApplicationNotFoundException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import model.ApplicationStatus;
import model.JobApplication;
import repository.JobApplicationRepository;
import service.JobApplicationService;

/** Run with a separate database whose name ends in _test. */
public class EditDetailsIntegrationTest {
    public static void main(String[] args) throws Exception {
        String url = System.getenv("DB_URL");
        if (url == null || !url.matches("jdbc:postgresql://[^/]+/[A-Za-z0-9_]+_test")) {
            throw new IllegalStateException("Use a separate PostgreSQL database ending in _test.");
        }
        var repo = new JobApplicationRepository();
        var service = new JobApplicationService(repo);
        var record = service.addApplication("Edit test " + UUID.randomUUID(), "Intern", "Berlin");
        int id = record.getId();
        try {
            service.changeApplicationStatus(id, ApplicationStatus.INTERVIEW);
            service.editApplicationDetails(id, "  O'Brien Ltd  ", "  Java Developer  ", "  Hamburg  ");
            assertDetails(repo.findById(id), id, "O'Brien Ltd", "Java Developer", "Hamburg");
            for (int field = 0; field < 3; field++) {
                for (String invalid : new String[]{null, "  "}) {
                    String[] details = {"Changed", "Changed", "Changed"};
                    details[field] = invalid;
                    try {
                        service.editApplicationDetails(id, details[0], details[1], details[2]);
                        throw new AssertionError("Invalid field accepted");
                    } catch (IllegalArgumentException expected) {
                        assertDetails(repo.findById(id), id, "O'Brien Ltd", "Java Developer", "Hamburg");
                    }
                }
            }
            try {
                service.editApplicationDetails(0, "Company", "Role", "City");
                throw new AssertionError("Invalid ID accepted");
            } catch (IllegalArgumentException expected) { }
            String output = runConsole("7\n" + id + "\nConsole Company\nEngineer\nMunich\n6\n");
            if (!output.contains("Application details updated.")) throw new AssertionError(output);
            assertDetails(repo.findById(id), id, "Console Company", "Engineer", "Munich");
            String persisted = runConsole("3\n" + id + "\n6\n");
            if (!persisted.contains("Console Company") || !persisted.contains("INTERVIEW")) {
                throw new AssertionError("Edits did not persist across console runs: " + persisted);
            }
            String invalidOutput = runConsole("7\n" + id + "\n \nRole\nCity\n6\n");
            if (!invalidOutput.contains("Company name must not be null or blank.")) throw new AssertionError(invalidOutput);
            assertDetails(repo.findById(id), id, "Console Company", "Engineer", "Munich");
            System.out.println("PASS: edits, trimming, apostrophes, required fields, unchanged ID/status, console flow, persistence");
        } finally {
            repo.deleteById(id);
        }
        try {
            service.editApplicationDetails(id, "Company", "Role", "City");
            throw new AssertionError("Missing ID accepted");
        } catch (ApplicationNotFoundException expected) { }
        String missing = runConsole("7\n" + id + "\n6\n");
        if (!missing.contains("No application found with ID " + id) || !missing.contains("Goodbye!")) throw new AssertionError(missing);
        if (repo.findById(id) != null) throw new AssertionError("Test cleanup failed");
        System.out.println("PASS: missing IDs and test record cleanup");
    }

    private static void assertDetails(JobApplication record, int id, String company,
                                      String position, String location) {
        if (record == null || record.getId() != id || !company.equals(record.getCompanyName())
                || !position.equals(record.getPosition()) || !location.equals(record.getLocation())
                || record.getStatus() != ApplicationStatus.INTERVIEW) {
            throw new AssertionError("Unexpected application: " + record);
        }
    }

    private static String runConsole(String input) throws Exception {
        String java = System.getProperty("java.home") + "/bin/java";
        Process process = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"), "main.Main")
                .redirectErrorStream(true).start();
        try (var stdin = process.getOutputStream()) {
            stdin.write(input.getBytes(StandardCharsets.UTF_8));
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.waitFor() != 0) throw new AssertionError(output);
        return output;
    }
}
