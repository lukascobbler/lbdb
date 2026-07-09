UPDATE Professor
SET Salary = Salary + 1500
WHERE IsTenured = false AND Salary < 55000;

UPDATE Student
SET IsActive = false
WHERE EnrollmentYear = 2019 AND IsActive = true;

UPDATE Enrollment
SET Points = 100
WHERE Points > 91 AND Points < 100 AND CourseID > 50;

DELETE FROM Enrollment
WHERE Points < 78 AND StudentID = 16;

DELETE FROM Professor
WHERE Salary < 50000 AND IsTenured = false;