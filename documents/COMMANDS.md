# COMMANDS — quick reference

## Prerequisites

```
java -version
mvn -v
docker compose version
```

## Application under test

```
git clone https://github.com/uekita/fullstack-booking-system
cd fullstack-booking-system
docker compose up --build -d
docker compose up -d
curl localhost:8080/actuator/health
docker compose ps
docker compose logs -f backend
docker compose down
docker compose down -v && docker compose up -d
git fetch origin && git checkout feature/booking-reschedule && docker compose up --build -d
git checkout main && docker compose up --build -d
```

## Browser

```
mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install chromium"
```

## Tests

```
mvn clean test
mvn test -Dgroups=api
mvn test -Dgroups=ui
mvn test -Dgroups=smoke
mvn test -Dtest=BookShiftE2ETest
mvn test -Dtest='RescheduleBookingApiTest,RescheduleBookingE2ETest,ReschedulePickerUiTest'
mvn test -Dtest=BookingConcurrencyTest -DexcludedGroups=
mvn test -Dgroups=ui -Dui.headless=false
mvn test -Dgroups=ui -Dui.browser=firefox
mvn test -Dgroups=bugs -DexcludedGroups=
mvn test -DexcludedGroups=
mvn test -Djunit.jupiter.execution.parallel.enabled=false
mvn test -Dsurefire.rerunFailingTestsCount=1
mvn test -Dapi.base.url=http://localhost:8080/api -Dui.base.url=http://localhost:3000
```

## Report

```
mvn allure:serve
mvn allure:report
open target/site/allure-maven-plugin/index.html
mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="show-trace <file>"
```

## Logs

```
tail -f target/logs/tests.log
```

## Docker: image

```
docker pull mcr.microsoft.com/playwright/java:v1.64.0-noble
docker run --rm mcr.microsoft.com/playwright/java:v1.64.0-noble java -version
docker build -t booking-tests .
docker network ls
```

## Docker: run

```
docker run --rm --ipc=host --network fullstack-booking-system_default -e API_BASE_URL=http://backend:8080/api -e UI_BASE_URL=http://frontend:80 -v "$(pwd)/target:/app/target" booking-tests
docker run --rm --ipc=host --network fullstack-booking-system_default -e API_BASE_URL=http://backend:8080/api -e UI_BASE_URL=http://frontend:80 -v "$(pwd)/target:/app/target" booking-tests -Dgroups=smoke
docker run --rm --ipc=host --network fullstack-booking-system_default -e API_BASE_URL=http://backend:8080/api -e UI_BASE_URL=http://frontend:80 -v "$(pwd)/target:/app/target" booking-tests -Dgroups=api
docker run --rm --ipc=host --network fullstack-booking-system_default -e API_BASE_URL=http://backend:8080/api -e UI_BASE_URL=http://frontend:80 -e UI_BROWSER=firefox -v "$(pwd)/target:/app/target" booking-tests -Dgroups=ui
docker run --rm --ipc=host --network fullstack-booking-system_default -e API_BASE_URL=http://backend:8080/api -e UI_BASE_URL=http://frontend:80 -v "$(pwd)/target:/app/target" booking-tests -Dgroups=bugs -DexcludedGroups=
docker run --rm --ipc=host --network fullstack-booking-system_default -e API_BASE_URL=http://backend:8080/api -e UI_BASE_URL=http://frontend:80 -v "$(pwd)/target:/app/target" booking-tests -Dtest=CreateBookingApiTest
docker run --rm --ipc=host -e API_BASE_URL=http://host.docker.internal:8080/api -e UI_BASE_URL=http://host.docker.internal:3000 -v "$(pwd)/target:/app/target" booking-tests
```

## Git: containerise branch

```
git checkout -b containerise
git add Dockerfile .dockerignore documents/DOCKER.md
git commit -m "Containerise: Playwright Java image, .dockerignore, DOCKER.md (local, CI, other dev envs)"
git add documents/TESTS.md documents/COMMANDS.md
git commit -m "Docs: TESTS.md test catalogue, COMMANDS.md quick reference"
git checkout master
git merge containerise
```
