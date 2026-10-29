# DOCKER — Running the framework in a container

The whole suite (API and UI) can run from one Docker image. The only things a machine needs are
Docker and the application under test (AUT). It doesn't need Java, Maven or Playwright browsers.
CI and every developer get the same JDK, Maven, browser build and OS libraries, so a test that is
green on one machine is green on all of them.

---

## What is in the image

### Dockerfile

```dockerfile
FROM mcr.microsoft.com/playwright/java:v1.64.0-noble
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
ENTRYPOINT ["mvn", "-B", "test"]
```

- **Base image.** This is Microsoft's official Playwright for Java image on Ubuntu 24.04 (noble). It
  ships a JDK, Maven, and Chromium, Firefox and WebKit, plus every OS library they need. The tag must
  match `playwright.version` in `pom.xml` (currently `1.64.0`). The Java client expects the exact
  browser builds of its own version, so bump both together. The JDK inside must be 21 or newer,
  because the pom compiles with `release 21`. Check it with
  `docker run --rm mcr.microsoft.com/playwright/java:v1.64.0-noble java -version`.
- **Dependencies first, sources second.** `pom.xml` is copied and its dependencies are downloaded
  before `src` is copied. Docker caches that layer, so rebuilding after a code change takes seconds.
  The download runs again only when `pom.xml` changes.
- **Entrypoint is `mvn -B test`.** The container's exit code is Maven's exit code: 0 when all tests
  pass, non-zero otherwise. That is all a CI gate needs. Anything after the image name in
  `docker run` is appended to the command (see "Choosing what to run").

### .dockerignore

```
target/
.allure/
.git/
.playwright-mcp/
documents/
```

This keeps the build context to `pom.xml` and `src`. Build output, local reports, git history,
MCP session files and documentation never reach the image.

---

## Build

```
docker build -t booking-tests .
```

The first build pulls the base image (about 2 GB) and the Maven dependencies. Later builds reuse
both from the cache.

---

## Run against the AUT in Docker Compose

1. Start the AUT in its own repository:
   ```
   docker compose up -d
   ```
   It is ready when `http://localhost:8080/actuator/health` reports `UP`.

2. Find the AUT's network. Compose names it `<project>_default`, and the project name is the
   directory name, so normally it is `fullstack-booking-system_default`:
   ```
   docker network ls
   ```

3. Run the suite in that network, with results mounted back into the host's `target/`:
   ```
   docker run --rm --ipc=host \
     --network fullstack-booking-system_default \
     -e API_BASE_URL=http://backend:8080/api \
     -e UI_BASE_URL=http://frontend:80 \
     -v "$(pwd)/target:/app/target" \
     booking-tests
   ```
   Expected result of the default run: `Tests run: 30, Failures: 0`. The 10 known-bug tests are
   excluded (see [TESTS.md](TESTS.md)).

4. Open the report on the host:
   ```
   mvn allure:serve
   ```
   `allure:serve` needs Maven on the host. Without it, open the `target/allure-results` folder with
   any Allure 3 installation, or publish it as a CI artifact.

### Why each flag is there

- `--network …` puts the test container next to the AUT, so it can reach `backend` and `frontend`
  by their Compose service names.
- `-e API_BASE_URL` / `-e UI_BASE_URL` point the framework at those names instead of `localhost`.
  Inside a container, `localhost` is the container itself.
- `--ipc=host` gives Chromium enough shared memory. Docker's default `/dev/shm` is 64 MB, and the
  browser can crash with it. Playwright's documentation recommends this flag for containers.
- `-v …/target:/app/target` keeps the Allure results, Playwright traces, screenshots and Surefire
  reports after `--rm` removes the container.

### Why the UI works from inside the network

The browser in the test container loads `http://frontend:80`. The frontend's nginx proxies `/api/`
to `http://backend:8080`, so the page's own API calls stay relative and also resolve inside the
network. No host ports are involved.

---

## Configuration in a container

The framework resolves every key in this order: `-Dkey=value`, then an environment variable, then
`config/<env>.properties` (with `-Denv=<name>`), then `defaults.properties`. The environment
variable name is the key in upper case with dots replaced by underscores:

- `api.base.url` → `API_BASE_URL`
- `ui.base.url` → `UI_BASE_URL`
- `ui.browser` → `UI_BROWSER` (`chromium`, `firefox` or `webkit`; all three are in the image)
- `ui.headless` → `UI_HEADLESS` (leave it `true`, because the container has no display)
- `api.max.response.ms` → `API_MAX_RESPONSE_MS`

The resolved values are logged at start-up (`Config: env=…, ui.base.url=…`), so a wrong URL shows
up in the first lines of output.

---

## Choosing what to run

Arguments after the image name are passed to Maven:

- Smoke subset: `docker run … booking-tests -Dgroups=smoke`
- API only: `docker run … booking-tests -Dgroups=api`
- UI only, in Firefox: `docker run … -e UI_BROWSER=firefox booking-tests -Dgroups=ui`
- Known bugs only (expected red): `docker run … booking-tests -Dgroups=bugs -DexcludedGroups=`
- Everything, bugs included: `docker run … booking-tests -DexcludedGroups=`
- One class: `docker run … booking-tests -Dtest=CreateBookingApiTest`

---

## Using the image in CI

Any CI runner with a Docker daemon follows the same five steps:

1. Check out this repository and the AUT.
2. Start the AUT with Compose and wait until it is healthy.
3. Build the test image (or pull a prebuilt one from a registry).
4. Run the test container on the AUT's network. Its exit code fails or passes the job.
5. Always publish `target/allure-results` (and `target/surefire-reports`) as artifacts, then stop
   the AUT.

### Example: GitHub Actions

```yaml
name: tests

on: [push, pull_request]

jobs:
  e2e:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Check out the application under test
        uses: actions/checkout@v4
        with:
          repository: uekita/fullstack-booking-system
          path: aut

      # -p fixes the project name, so the network is fullstack-booking-system_default
      # whatever directory the AUT was checked out to. --wait blocks until healthchecks pass.
      - name: Start the application under test
        run: docker compose -p fullstack-booking-system -f aut/docker-compose.yml up -d --build --wait

      - name: Build the test image
        run: docker build -t booking-tests .

      - name: Run the tests
        run: |
          docker run --rm --ipc=host \
            --network fullstack-booking-system_default \
            -e API_BASE_URL=http://backend:8080/api \
            -e UI_BASE_URL=http://frontend:80 \
            -v "$PWD/target:/app/target" \
            booking-tests

      - name: Publish Allure results
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: allure-results
          path: target/allure-results

      - name: Stop the application under test
        if: always()
        run: docker compose -p fullstack-booking-system -f aut/docker-compose.yml down -v
```

### CI notes

- **Separate job for known bugs.** The default run excludes the `bugs` tag, so the gate stays green
  until something regresses. To keep the bug tests visible without blocking merges, run them in a
  second job with `-Dgroups=bugs -DexcludedGroups=` and mark that job as allowed to fail.
- **Prebuilt image.** Instead of building on every run, publish the image to a registry (for
  example GHCR) when `pom.xml` or `src` changes, and `docker pull` it in CI. Tag it with the commit
  SHA so every test run can be traced to the exact code it ran.
- **Other CI systems.** GitLab CI, Jenkins, Azure Pipelines and others work the same way as long as
  the job can reach a Docker daemon (a shell runner, or docker-in-docker). With docker-in-docker,
  the AUT and the test container must run on the same daemon, or they can't share a network.
- **Clean `down -v`.** Removing the AUT's database volume after each run keeps runs independent.
  The tests don't depend on seed rows anyway, but a fresh volume removes a whole class of questions.

---

## Using the image in other developer environments

- **No local toolchain.** A developer with only Docker can build the image and run the suite with
  the command above. No JDK, Maven or browser installation is needed.
- **macOS and Windows (Docker Desktop).** The commands work as written. On Windows, run them from
  WSL 2 or replace `$(pwd)` with `${PWD}` in PowerShell.
- **Apple Silicon.** The Playwright base image is published for `amd64` and `arm64`. Docker pulls
  the native variant, so there is no emulation.
- **AUT running on the host, not in Compose.** Leave out `--network`. Docker Desktop resolves
  `host.docker.internal` to the host, so use
  `-e API_BASE_URL=http://host.docker.internal:8080/api -e UI_BASE_URL=http://host.docker.internal:3000`.
  On Linux, add `--add-host=host.docker.internal:host-gateway` to get the same name.
- **AUT deployed elsewhere (staging, a review app).** Leave out `--network` and pass that
  environment's URLs in `API_BASE_URL` and `UI_BASE_URL`. The tests create their own data through
  the API, so they can run against any environment where creating test couriers and shifts is
  acceptable.

---

## Known limits

- **Results owned by root on Linux.** The container runs as root, so on a Linux host the files it
  writes into `target/` belong to root, and a later host `mvn clean` can fail on them. Docker
  Desktop on macOS and Windows maps ownership and isn't affected. On Linux CI runners this is
  harmless, because the workspace is discarded after the job.
- **Network access at run time.** `dependency:go-offline` fetches the declared dependencies, but
  Maven plugins can resolve extra artifacts when tests run (for example Surefire's JUnit Platform
  provider). The container should be able to reach Maven Central, or a mirror configured in
  `settings.xml`.
- **Headless only.** The container has no display, so `-Dui.headless=false` doesn't work there. To
  watch the browser, run the tests on the host, or open the Playwright trace attached to a failed
  test in the Allure report.
- **Image and pom versions are coupled.** Updating Playwright means changing `playwright.version` in
  `pom.xml` and the `FROM` tag in the `Dockerfile` in the same commit.
