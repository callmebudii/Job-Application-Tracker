package exception;

public class ApplicationNotFoundException extends Exception {
    public ApplicationNotFoundException(int id) {
        super("No application found with ID " + id + ".");
    }
}
