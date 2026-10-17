package main;

import exception.ApplicationNotFoundException;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Scanner;
import model.ApplicationStatus;
import model.JobApplication;
import service.JobApplicationService;
import repository.JobApplicationRepository;

public class Main {
    public static void main(String[] args) {
        JobApplicationService service = new JobApplicationService(new JobApplicationRepository());

        // One Scanner handles all input and is closed when the program ends.
        try (Scanner scanner = new Scanner(System.in)) {
            runMenu(scanner, service);
        } catch (NoSuchElementException exception) {
            // End-of-input (for example Ctrl+D) also exits cleanly.
            System.out.println("\nInput ended. Goodbye!");
        }
    }

    private static void runMenu(Scanner scanner, JobApplicationService service) {
        boolean running = true;
        while (running) {
            printMenu();
            String choice = scanner.nextLine().trim();
            try {
                running = handleChoice(choice, scanner, service);
            } catch (ApplicationNotFoundException exception) {
                System.out.println(exception.getMessage());
            } catch (SQLException exception) {
                System.out.println("Database operation failed. Check PostgreSQL, the JDBC driver, "
                        + "environment variables, and table setup.");
            } catch (IllegalArgumentException exception) {
                System.out.println(exception.getMessage());
            }
        }
    }

    private static boolean handleChoice(String choice, Scanner scanner,
                                        JobApplicationService service)
            throws ApplicationNotFoundException, SQLException {
        switch (choice) {
            case "1":
                addApplication(scanner, service);
                break;
            case "2":
                viewAllApplications(service);
                break;
            case "3":
                findApplication(scanner, service);
                break;
            case "4":
                changeStatus(scanner, service);
                break;
            case "5":
                deleteApplication(scanner, service);
                break;
            case "6":
                System.out.println("Goodbye!");
                return false;
            case "7":
                editApplicationDetails(scanner, service);
                break;
            default:
                System.out.println("Invalid choice. Please enter 1 to 7.");
        }
        return true;
    }

    private static void printMenu() {
        System.out.println("\nJob Application Tracker");
        System.out.println("1. Add application");
        System.out.println("2. View all applications");
        System.out.println("3. Find application by ID");
        System.out.println("4. Change application status");
        System.out.println("5. Delete application");
        System.out.println("6. Exit");
        System.out.println("7. Edit application details");
        System.out.print("Choose an option: ");
    }

    private static void addApplication(Scanner scanner, JobApplicationService service)
            throws SQLException {
        String companyName = readText(scanner, "Company name: ");
        String position = readText(scanner, "Position: ");
        String location = readText(scanner, "Location: ");

        JobApplication application = service.addApplication(companyName, position, location);
        System.out.println("Added application with ID " + application.getId()
                + " and status " + application.getStatus() + ".");
    }

    private static void viewAllApplications(JobApplicationService service) throws SQLException {
        List<JobApplication> applications = service.getAllApplications();
        if (applications.isEmpty()) {
            System.out.println("No applications yet.");
            return;
        }
        for (JobApplication application : applications) {
            System.out.println(application);
        }
    }

    private static void findApplication(Scanner scanner, JobApplicationService service)
            throws ApplicationNotFoundException, SQLException {
        int id = readId(scanner);
        System.out.println(service.findById(id));
    }

    private static void changeStatus(Scanner scanner, JobApplicationService service)
            throws ApplicationNotFoundException, SQLException {
        int id = readId(scanner);
        ApplicationStatus status = readStatus(scanner);
        service.changeApplicationStatus(id, status);
        System.out.println("Status changed to " + status + ".");
    }

    private static void deleteApplication(Scanner scanner, JobApplicationService service)
            throws ApplicationNotFoundException, SQLException {
        int id = readId(scanner);
        service.deleteApplication(id);
        System.out.println("Application deleted.");
    }

    private static void editApplicationDetails(Scanner scanner, JobApplicationService service)
            throws ApplicationNotFoundException, SQLException {
        int id = readId(scanner);
        JobApplication current = service.findById(id);
        System.out.println("Current application: " + current);
        System.out.println("Enter all three replacement details. Blank values are not allowed.");
        String companyName = readText(scanner, "New company name: ");
        String position = readText(scanner, "New position: ");
        String location = readText(scanner, "New location: ");
        service.editApplicationDetails(id, companyName, position, location);
        System.out.println("Application details updated.");
    }

    private static String readText(Scanner scanner, String prompt) {
        System.out.print(prompt);
        return scanner.nextLine();
    }

    private static int readId(Scanner scanner) {
        while (true) {
            System.out.print("Application ID: ");
            String input = scanner.nextLine().trim();
            try {
                int id = Integer.parseInt(input);
                if (id > 0) {
                    return id;
                }
            } catch (NumberFormatException exception) {
                // Non-numeric input or a number too large for int: prompt again.
            }
            System.out.println("Please enter a positive whole-number ID.");
        }
    }

    private static ApplicationStatus readStatus(Scanner scanner) {
        while (true) {
            System.out.println("Allowed statuses: SAVED, APPLIED, INTERVIEW, REJECTED, OFFER");
            System.out.print("New status: ");
            String input = scanner.nextLine().trim().toUpperCase(Locale.ROOT);
            try {
                return ApplicationStatus.valueOf(input);
            } catch (IllegalArgumentException exception) {
                System.out.println("Invalid status. Please enter one of the listed statuses.");
            }
        }
    }
}
