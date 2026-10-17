> Historical guide: this describes the earlier ArrayList version. For current setup,
> see [the README](../README.md) and [the JDBC guide](JDBC_GUIDE.md).

# Job Application Tracker

A beginner-friendly Java console application for tracking job applications.

## Features

- Add applications
- View all applications
- Search by ID
- Change application status
- Delete applications
- Input validation
- Custom exception handling

## Technologies

- Java (Core Java only)
- Object-oriented programming (OOP)
- ArrayList
- Enums
- Exception handling
- Git for version control

The console menu uses `Scanner` for input. No frameworks, database, or build tools
are required. Applications live in memory, so they disappear when the program ends.

## Step 1: Organize the packages

```text
src/
├── exception/
│   └── ApplicationNotFoundException.java
├── model/
│   ├── ApplicationStatus.java
│   └── JobApplication.java
├── service/
│   └── JobApplicationService.java
└── main/
    └── Main.java
```

`exception` defines application errors, `model` describes the data,
`service` manages applications, and `main` runs the
program. The `package` line in each file matches its folder under `src`.

## Step 2: Define the allowed statuses

`src/model/ApplicationStatus.java` is an **enum**: a fixed set of named values.
It contains `SAVED`, `APPLIED`, `INTERVIEW`, `REJECTED`, and `OFFER`.
Using `ApplicationStatus.APPLIED` instead of a string avoids spelling mistakes
such as `"APLIED"` and lets the compiler check the type.

## Step 3: Model one application

`src/model/JobApplication.java` represents one application with five fields:
`id`, `companyName`, `position`, `location`, and `status`.

- The constructor initializes a new object. `this.id = id` assigns the constructor
  parameter to the object's field.
- Fields are `private`, so other classes access them through public methods.
  This is **encapsulation**.
- Getters read the fields. Only status has a setter because changing status is
  the only editing operation needed for now.
- `final` fields cannot be reassigned after construction. The ID and application
  details stay fixed; status can change.
- `setStatus` rejects `null` with an `IllegalArgumentException`, ensuring an
  application always has a status.
- `toString()` gives readable output when an application is printed.
  `@Override` tells Java that this replaces the method inherited from `Object`.

## Step 4: Manage applications with a service

`src/service/JobApplicationService.java` owns an
`ArrayList<JobApplication>`. An `ArrayList` is a growable list, and the type in
angle brackets says which objects it stores. Each service instance has its own
list and ID counter.

| Method | Behavior | Return value |
| --- | --- | --- |
| `addApplication(companyName, position, location)` | Assigns the next ID, starts at `SAVED`, and stores the application | The new `JobApplication` |
| `getAllApplications()` | Copies the list of stored applications | `ArrayList<JobApplication>` |
| `findById(id)` | Loops through the list and compares IDs | Matching application; throws `ApplicationNotFoundException` if absent |
| `changeApplicationStatus(id, newStatus)` | Finds the application and calls its setter | `void`; throws `ApplicationNotFoundException` if absent |
| `deleteApplication(id)` | Finds and removes the application | `void`; throws `ApplicationNotFoundException` if absent |

IDs start at 1 and increase with each addition. Deleting an application does not
reuse its ID. Searching compares numeric IDs with `==`.

Returning a copy from `getAllApplications` protects the internal list from
external additions and removals. It is a **shallow copy**: its application objects
are still the same objects, so calling `setStatus` on one changes that application.
The service's `final` list field prevents replacing the list, but still allows
adding and removing items.

The model holds the data; the service handles storing, searching, updating, and
removing it. These separate responsibilities keep the code easy to follow without
extra interfaces or abstractions.

## Step 5: Run the console menu

`src/main/Main.java` contains `public static void main(String[] args)`, Java's
entry point. It creates one service and one `Scanner`, then repeatedly shows the
menu until you choose option 6. The application list starts empty.

From the project folder, compile and run:

```sh
mkdir -p out
javac -d out src/exception/ApplicationNotFoundException.java src/model/ApplicationStatus.java src/model/JobApplication.java src/service/JobApplicationService.java src/main/Main.java
java -cp out main.Main
```

`javac` compiles source files into `.class` files. `-d out` puts them under `out`
in their package folders. `java -cp out main.Main` runs the fully qualified class
name using `out` as the classpath.

Choose one of these options:

1. Add application — enter company name, position, and location. The service
   assigns the ID and initial `SAVED` status automatically.
2. View all applications — print the stored applications, or an empty-list message.
3. Find application by ID — enter an existing application's ID.
4. Change application status — enter its ID and one of the five allowed statuses.
5. Delete application — enter the ID of the application to remove.
6. Exit — leave the loop and close the scanner.

### Exception handling and validation: what changed and why

**New file: `src/exception/ApplicationNotFoundException.java`**

This small class extends `Exception`, making it a checked exception. Its
constructor builds a clear message containing the missing ID. A checked exception
requires callers to either catch it or declare it with `throws`, which makes the
lookup's failure case explicit. No extra exception classes are needed.

**Updated file: `src/service/JobApplicationService.java`**

- `addApplication` uses a private `validateRequiredText` helper for company name,
  position, and location. It rejects `null`, empty strings, and whitespace-only
  strings with `IllegalArgumentException`, then strips surrounding whitespace.
  Validation happens before constructing or storing the application, so a failed
  addition does not consume an ID or change the list.
- `findById` rejects non-positive IDs with `IllegalArgumentException`. A positive
  ID that is not stored causes `ApplicationNotFoundException` instead of `null`.
- `changeApplicationStatus` and `deleteApplication` both rely on `findById`.
  They no longer need duplicate not-found checks. Both now return `void`: normal
  completion means success, while an exception means failure.
- Status changes reject `null` before updating the object. The enum already
  limits non-null values to the five supported statuses.
- The service contains no console input/output and does not catch these errors:
  it reports them to the caller, which decides how to present them.

**Updated file: `src/main/Main.java`**

- `main` only creates the service and scanner, calls `runMenu`, and handles
  end-of-input. The existing try-with-resources still closes the scanner.
- `runMenu` repeats the menu and catches `ApplicationNotFoundException` and
  `IllegalArgumentException` around one menu action. It displays the exception's
  message and returns to the menu rather than stopping the program.
- `handleChoice` dispatches options to the small interaction methods and returns
  whether the loop should continue. Option 6 returns `false`.
- Find, update, and delete interaction methods declare `throws` and let errors
  reach the menu's shared catch blocks. Their previous null/boolean checks were
  removed to match the new service contract. Success messages appear only after
  the service operation completes.
- `readText` simply reads a line. Required-field validation now belongs to the
  service rather than only to the console. If a field is blank, the addition is
  rejected after the three prompts; select option 1 to try again.
- `readId` still uses `nextLine` and `Integer.parseInt`. It catches only
  `NumberFormatException` to retry non-numeric or oversized input, and also retries
  zero and negative IDs. The service independently validates IDs for other callers.
- `readStatus` catches `IllegalArgumentException` only around enum conversion and
  retries unknown statuses. It accepts lowercase names by converting to uppercase
  with `Locale.ROOT`. No arbitrary status string reaches the service.

These catches have specific recovery behavior: retry a malformed input, return
from a failed action to the menu, or exit when input ends. There is no broad
`catch (Exception)` hiding unrelated programming errors.

**Updated file: this README**

The package tree, method contracts, compilation command, and explanations now
match the exception-based version. The model files and `.gitignore` needed no
changes. The model's existing status guard remains useful if its setter is called
directly. Business validation for adding applications lives in the service, so
callers should use `addApplication` rather than constructing stored objects themselves.

Everything still uses Core Java and an in-memory `ArrayList`; exiting loses data.

## Supporting files

This `README.md` explains the project and how to run it. `.gitignore` keeps the
generated `out/` folder and `.class` files out of version control. You edit the
Java source files, then compile again to refresh the generated files.

## Practice next

Add two applications, move the first to `INTERVIEW`, delete the second, and add
a third. Notice that its ID is 3: the service does not reuse deleted IDs. Try an
invalid menu choice, a non-numeric ID, and an unknown status to see the retries.
