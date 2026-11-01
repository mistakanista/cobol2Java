-- Sample seed data mimicking the comma-delimited account records from the legacy AccountFile (accounts.txt).
-- In the legacy COBOL system, each line was formatted as: FirstName,LastName,BirthDate,Balance
-- Here we insert equivalent records directly into the H2 in-memory ACCOUNT table.

INSERT INTO account (first_name, last_name, birth_date, balance) VALUES ('Hans', 'Mueller', '1980-03-15', 1250.75);
INSERT INTO account (first_name, last_name, birth_date, balance) VALUES ('Anna', 'Schmidt', '1992-07-22', 3400.00);
INSERT INTO account (first_name, last_name, birth_date, balance) VALUES ('Peter', 'Wagner', '1975-11-05', 780.50);
INSERT INTO account (first_name, last_name, birth_date, balance) VALUES ('Maria', 'Fischer', '1988-01-30', 5900.20);
INSERT INTO account (first_name, last_name, birth_date, balance) VALUES ('Klaus', 'Becker', '1965-09-18', 12300.00);
INSERT INTO account (first_name, last_name, birth_date, balance) VALUES ('Sabine', 'Hoffmann', '1990-06-11', 640.99);
INSERT INTO account (first_name, last_name, birth_date, balance) VALUES ('Thomas', 'Koch', '1983-04-27', 2200.10);
INSERT INTO account (first_name, last_name, birth_date, balance) VALUES ('Laura', 'Bauer', '1995-12-03', 870.00);
INSERT INTO account (first_name, last_name, birth_date, balance) VALUES ('Stefan', 'Richter', '1978-08-14', 4500.60);
INSERT INTO account (first_name, last_name, birth_date, balance) VALUES ('Julia', 'Klein', '2000-02-19', 150.25);
