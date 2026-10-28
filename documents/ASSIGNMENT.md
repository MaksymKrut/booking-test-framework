# Technical Assessment

This exercise is designed to give you an opportunity to showcase your framework architecture skills, coding practices, and testing strategies.

## ⏱ Time Expectation

We respect your time. Please aim to spend no more than one day of actual effort on this assignment. We are significantly more interested in the architecture, code quality, and design patterns of your framework than in the sheer volume of test cases you write. Quality over quantity! Note: You have a maximum of one week from receiving this prompt to submit your assignment.

## 🏗 The Application Under Test

We have provided a containerized Fullstack Booking System for this exercise.

- Repository: https://github.com/uekita/fullstack-booking-system
- Setup Instructions:
  - Clone the repository to your local machine.
  - Follow the repository's README.md to spin up the application locally (typically via docker-compose up).
  - Ensure you can access the frontend UI and that the backend services (APIs and databases) are running successfully.

## 🎯 Your Mission

Your task is to build a robust, scalable test automation framework from scratch using Java. Please create a new project repository to house your framework.

### 1. Framework Architecture

- Initialize a new Java project using Maven or Gradle.
- Choose your preferred test runner (e.g., TestNG or JUnit 5).
- Structure your project with a clear separation of concerns (e.g., Page Object Model, API clients, configuration management, test data, and utilities).

### 2. UI Automation (Selenium or Playwright)

- Automate 1 or 2 critical end-to-end user journeys via the frontend.
- Scenario Idea: Automate the process of a user logging in, navigating the booking flow, and successfully creating a new booking.
- Expectation: Demonstrate proper use of fluent/explicit waits, robust locator strategies, and avoid hardcoded test data inside test methods.

### 3. API Automation (REST Assured)

- Automate 1 or 2 core backend endpoints.
- Scenario Idea: Create a booking directly via the REST API. Validate the HTTP status codes, response times, and the structure/data of the JSON response payload.
- Expectation: Utilize Request/Response specifications, and handle serialization/deserialization using POJOs.

### 4. Reporting and Logging

- Integrate a reporting tool of your choice (e.g., Allure, ExtentReports).
- Ensure meaningful logging (e.g., SLF4J, Log4j) is present to trace test execution steps, especially for API requests and responses.

## 📦 Deliverables

Please submit your completed assignment by providing a link to your repository (e.g., GitHub, GitLab). Your submission MUST include a README.md detailing:

- The tech stack and libraries used.
- Prerequisites and clear instructions on how to execute the test suite locally (e.g., mvn clean test).
- Instructions on how to generate and view the test report.
- Any assumptions you made, known issues, or what you would improve if you had more time.

## 🚨 Important Notes for the Next Steps

- AI Usage: Use of AI (Copilot, ChatGPT) is completely fine, but be mindful that during the next interview, we will ask deep technical questions about your solution and the design choices you made.
- Live Interview Prep: Be prepared to run this project on your local machine during our follow-up interview. Sharing your screen will be mandatory for a brief live-coding task to extend your framework.
- Environment: Please install the JetBrains Toolbox App in advance. We highly recommend having IntelliJ downloaded and installed.

Good luck, and have fun building! If you run into critical infrastructure issues with the provided application, please reach out.