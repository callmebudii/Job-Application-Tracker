-- Run this script in the job_application_tracker database before starting Java.
CREATE TABLE IF NOT EXISTS job_applications (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_name TEXT NOT NULL CHECK (length(trim(company_name)) > 0),
    position TEXT NOT NULL CHECK (length(trim(position)) > 0),
    location TEXT NOT NULL CHECK (length(trim(location)) > 0),
    status TEXT NOT NULL CHECK (status IN ('SAVED', 'APPLIED', 'INTERVIEW', 'REJECTED', 'OFFER'))
);
