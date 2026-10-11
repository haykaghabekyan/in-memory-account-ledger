# Builds and runs the ledger without a local JDK or Maven. See README "Running with Docker".
FROM maven:3.9.9-eclipse-temurin-17

WORKDIR /app

# Dependencies and plugins first, so they stay cached when only the source changes.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# Building runs the tests too (the intentionally failing one is excluded by default), so the
# image only builds if they pass, and every plugin the test run needs is downloaded into it.
COPY src ./src
RUN mvn -B -q verify

# Default: print the daily report for Days 1 to 6.
CMD ["mvn", "-B", "-q", "exec:java"]
