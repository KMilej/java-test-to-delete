# Approov Quickstart - Java Spring Token Check

<details>
  <summary style="font-size:1.6em; line-height:1.6; display:flex; align-items:center;">
    <img src="https://approov.io/hs-fs/hubfs/approov%20logo%20300ppi-1.webp?height=80&width=164&name=approov+logo+300ppi-1.webp"
         width="40"
         style="background-color:white; padding:4px; border-radius:6px; margin-right:10px;"
         alt="Approov logo" />
    <strong>Click “Approov Overview” for more details</strong>
  </summary>

---
<b>[**Approov**](https://approov.io) is an API security solution that verifies whether requests received by your backend services originate from **trusted versions** of your mobile apps.</b>

### Why Approov?

You can learn more about Approov, the motives for adopting it, and more detail on how it works by following this [link](https://approov.io/product). In brief, Approov:

---
- Ensures that accesses to your API come only from **official app versions**, blocking republished, modified, or tampered ones
- Protects **sensitive backend data** by preventing API abuse from bots or scripts
- Secures the communication channel between your app and API using [Approov Dynamic Certificate Pinning](https://approov.io/docs/latest/approov-usage-documentation/#approov-dynamic-pinning), which provides the benefits of traditional pinning without its maintenance drawbacks
- Removes the need for **API keys** embedded in mobile apps
- Provides **DoS protection**, mitigating attacks that attempt to exhaust API server resources or degrade service for legitimate users

### How It Works

---
The following is a high-level overview of how the **Approov Cloud Service** and the **backend server** operate together from a backend perspective.  
For a full explanation of how the mobile app, backend, and Approov SDK interact, see the [Approov overview page](https://approov.io/product).

### Approov Cloud Service

The Approov Cloud Service attests that a device is running a legitimate and untampered version of your mobile app.

- If the integrity check **passes**, a **valid token** is returned to the app
- If the integrity check **fails**, a **legitimate-looking (invalid) token** is returned

In either case, the app, unaware of the token's validity, adds it to every request it makes to the Approov protected API(s).

### Backend Server

The backend server verifies that the token supplied in the `Approov-Token` header is both present and valid.  
Validation is performed using a **shared secret** known only to the Approov Cloud Service and your backend.

The request flow:

- If the Approov Token is **valid**, the request is processed by the API endpoint
- If the Approov Token is **invalid**, the server returns **HTTP 401 Unauthorized**

> You can choose to log JWT verification failures, but we left it out on purpose so that you can have the choice of how you prefer to do it and decide the right amount of information you want to log. 
---

</details>

This `quickstart` implements the [Approov](https://approov.io) server-side request verification code using the Java Spring Boot Framework.

Approov provides comprehensive [runtime application self protection](https://approov.io/mobile-app-security/rasp) delivering app attestation, dynamic certificate pinning, runtime secrets protection, and API shielding - all unified across Android, iOS, and HarmonyOS.

It provides a simple example API `api.example.com` that performs Approov token verification before allowing requests to access protected endpoints.

---
- The example demonstrates how different API endpoints `/unprotected, /token-check, /token-binding-check, etc.` respond based on the current [Approov configuration](https://ext.approov.io/docs/latest/approov-usage-documentation/#getting-all-api-configuration).
- Each endpoint reflects whether Approov is enabled or disabled.
- These endpoints are tested using `curl` requests to observe how the security settings affect the responses.

### The quickstart was tested with the following environment:
```text
* Operating System: macOS 15.6.1 (Sequoia)
* JVM: 17.0.14
* Spring Boot version: 2.6.4
* Gradle version: 7.6.6
```

---

### Requirements
#### Before you start, If you are new to Approov, make sure you have the following:
* Approov CLI initialized [Follow the Approov CLI initialization guide](https://ext.approov.io/docs/latest/approov-installation/#initializing-the-approov-cli).
* [Sign up for the Approov Free Trial](https://approov.io/signup)(no credit card needed)
* [Get Started with Approov](https://approov.io/product/demo)


### Approov Token Verification Flow (Short Version)

1. **Token Request:**  
   The `Approov SDK` inside the mobile app securely communicates with the `Approov Cloud Service` to obtain a short-lived [Approov Token](https://ext.approov.io/docs/latest/approov-usage-documentation/#approov-tokens) (a signed JWT).

2. **Token Attachment:**  
   The app attaches this token to every API request using the `Approov-Token` HTTP header.

3. [Server Validation:](https://ext.approov.io/docs/latest/approov-usage-documentation/#approov-architecture)

   The server verifies the token using the shared `Approov secret`, checking its:
    - Signature authenticity
    - Expiration (`exp` claim)
    - Other claims if configured

4. [(Optional) Token Binding:](https://ext.approov.io/docs/latest/approov-usage-documentation/#token-binding)
   - For extra protection, the app may include an additional `Approov-Token-Binding` header.  
   - This binds the token to specific request data (for example, an access token or session ID).  
   - The server ensures that this value matches the hash inside the token, preventing **token reuse or replay attacks**.

5. **Request Decision:**
    -  If all checks pass → the request is trusted and processed **`200 OK`**.
    -  If validation fails → the server responds with **`401 Unauthorized`**.

```json
"Try it yourself by following the steps below with any of the available options"
```

---

<details>
<summary style="font-size:1.6em; line-height:1.6; display:flex; align-items:center;">
  <img src="https://www.docker.com/wp-content/uploads/2022/03/Moby-logo.png" width="40" style="vertical-align:middle; margin-right:10px;" />
  <strong>Run Using Docker</strong>
</summary>

## Requirements

```test
Docker Environment:
Docker version: 28.5.1
* Services:
    * app → Spring Boot application container (built from Dockerfile.dev)
    * tests → JDK 17 test container running ./testall.sh
* Base Image (Container OS): eclipse-temurin:17-jdk (Debian-based)

### Docker, Colima and Docker-Compose
Docker Compose version 2.40.1
colima version 0.9.1
```

#### Install Docker, Colima and Docker-Compose via Homebrew
```bash # On macOS install with Homebrew
brew install docker colima docker-compose
```

If you have all requirements installed, you can build and run the example inside `quickstart-java-spring`:

```bash
bash javaSpringApproov.sh
```

This script will:
- Checks environment requirements - verifies that Approov CLI, Docker, Docker Compose v2, and Colima are installed and running.
- Builds and starts containers – runs docker compose up -d --build to build the image and launch the Spring Boot application in the background.
- Runs approov-test.sh – executes all endpoint tests `unprotected (no headers)` `token-check (with Approov-Token)` `token-binding (with Approov-Token + Authorization header`
- Displays results and stops containers when finished.

</details>

---

<details>
<summary style="font-size:1.6em; line-height:1.6; display:flex; align-items:center;">
   <img src="https://img.shields.io/badge/IDE-gray?style=for-the-badge&logoColor=white" width="40" style="vertical-align:middle; margin-right:10px;" />
  <strong>Run in IDE (Automatic)</strong>
</summary>

### You should have already:
```text
* JVM: 17.0.14
* Spring Boot version: 2.6.4
* Gradle version: 7.6.6
* Approov CLI initialized
```

#### Now, open your IDE (IntelliJ, Eclipse, Android Studio, etc.) and import the project as a Gradle project.
#### inside the project folder `quickstart-java-spring`, run:

```bash
./gradlew build
```

#### Environment Setup Script — `set-approov-secret.sh`
```bash
bash set-approov-secret.sh
```

What is does:
- Creates the `.env` file automatically if `.env does not exist, the script copies it from `.env.example`.
- Run `approov secret -get base64` to retrieve the Approov secret from the Approov CLI and sets it in the `.env` file.
- Registers the domain api.example.com in the Approov cloud configuration so it can be protected by Approov.
```bash
set -a  # auto-export all assignments
source .env && ./gradlew bootRun
set +a  # stop exporting variables
```

<h4>When the server is running, you can test the endpoints using bash script in a different terminal.</h4>

```bash
bash testall.sh
```

This script will:
- Checks Approov authentication and required tools — verifies that approov and curl are available, and confirms Approov CLI login works.
- tests Approov state and setup — calls /approov-state to detect whether token checking is enabled or disabled.
- Runs endpoint tests — performs HTTP requests for: `/unprotected (no token), /token-check (valid/invalid tokens), /token-binding-1 and /token-binding-2 (with bound headers like Authorization and Content-Digest).`

</details>

---

<details>
<summary style="font-size:1.6em; line-height:1.6; display:flex; align-items:center;">
  <img src="https://img.shields.io/badge/IDE-gray?style=for-the-badge&logoColor=white" width="40" style="vertical-align:middle; margin-right:10px;" />
 <strong>Run in IDE (Manual Setup)</strong>
</summary>

### You should have already:
```text
* JVM: 17.0.14
* Spring Boot version: 2.6.4
* Gradle version: 7.6.6
* Approov CLI initialized
```

#### Now, open your IDE (IntelliJ, Eclipse, Android Studio, etc.) and import the project as a Gradle project.
#### inside the project folder `quickstart-java-spring`, run:

```bash
./gradlew build
```

#### Create a .env file in the project root by :
```bash
cp .env.example .env
```
#### Generate a new Approov secret and add it to the `.env` file in `APPROOV_BASE64_SECRET=` by use CLI command:
```bash
approov secret -get base64
```
#### Register the API domain `api.example.com` with the Approov CLI by:
```bash
approov api -add api.example.com
```

#### Finally, run the Spring Boot application with:
```bash
set -a  # auto-export all assignments
source .env && ./gradlew bootRun
set +a  # stop exporting variables
```

### ===========================================================
### 1. Unprotected Endpoint (No Approov)
### ===========================================================

- The client sends a normal HTTPS request.
- The server **does not verify** any Approov token or extra authentication header.
- This means **any client** (even tampered or unauthorized) can call the API if they know the URL.

#### The following example shows how the API responds when no Approov protection is applied.
```bash
curl -X GET http://localhost:8080/unprotected
```

The response will be a `200` for request:
```text
HTTP/1.1 200 OK
Content-Type: application/json
Cache-Control: no-cache
```

### ===========================================================
### 2. Approov Token check
### ===========================================================

- The client includes an **`Approov-Token`** (a short-lived JWT) in each API request header.
- The server verifies this token using the ** Approov secret key** that is securely configured on the backend and checks:
    -  Token verification - confirms the token is still valid.
    -  Expiration (`exp` claim) - ensures the token is still valid.
- If the token is valid → request is trusted.
- If invalid → server returns **`401 Unauthorized`**.
- **Purpose**: Protect API endpoints so that only authentic, unmodified Approov-integrated apps can access them.

####  The following example shows how the API responds when an Approov token is required.

#### Valid Approov Token request:
```bash
approov token -genExample api.example.com
```

#### Use the generated token in the `Approov-Token` header and /token-check endpoint. 
```bash
curl -X GET http://localhost:8080/token-check \
     -H "Approov-Token: <valid_approov_token_here>"
```

The response will be a `200` for request:

```text
HTTP/1.1 200 OK
Content-Type: application/json
Cache-Control: no-cache
```

#### If you use an invalid or missing token, the server will respond with `401 Unauthorized`.

### ===========================================================
### 3. Approov Token Binding check
### ===========================================================

- The client sends two headers on authenticated API calls:
    - **`Approov-Token`**
    - **`Authorization`** header containing a hash of specific request data (e.g., access token or session ID).
- The server verifies the token **and** ensures that the bound value matches what the app used.
- Prevents token replay — the Approov token **cannot be reused or stolen** for another session.
- **Use case:** stronger protection for **authenticated API calls** tied to a specific user or device.

#### The following example shows how the API responds when an Approov token with binding is required.

#### Generate a valid Approov Token with binding:
```bash
approov token -setDataHashInToken your-custom-name -genExample api.example.com
```

#### Use the generated token with binding in the Approov-Token and Authorization headers when calling the /token-binding-1 endpoint.

```bash
curl -X GET http://localhost:8080/token-binding-1 \
     -H "Approov-Token: <valid_approov_token_here>" \
     -H "Authorization: <your-custom-name>"
```

The response will be a `200` for request:

```text
HTTP/1.1 200 OK
Content-Type: application/json
Cache-Control: no-cache
```

#### If you use an invalid or missing header or token, the server will respond with `401 Unauthorized`. 

### ===========================================================
### 3. Approov Token Binding check with two different bound values
### ===========================================================

- The client sends three headers on authenticated API calls:
    - **`Approov-Token`**
    - **`Authorization`**
    - **`Content-Digest`** It is combined with the `Authorization` header to create a stronger binding.
- Both are included in the hash inside the Approov token. This means the server verifies a single hash that covers both authentication credentials.
- **Use case:** This configuration provides the highest level of protection for authenticated API requests:

#### The following example shows how the API responds when an Approov token with two bindings is required.

#### Generate a valid Approov Token with two binding:
```bash
approov token -setDataHashInToken ExampleAuthToken==ContentDigest== -genExample api.example.com
```

#### Use the generated token with two binding in the Approov-Token and Authorization headers when calling the /token-binding-2 endpoint.

```bash
curl -X GET http://localhost:8080/token-binding-2 \
     -H "Approov-Token: <valid_approov_token_here>" \
     -H "Authorization: ExampleAuthToken==" \
     -H "Content-Digest: ContentDigest=="
```

The response will be a `200` for request:

```text
HTTP/1.1 200 OK
Content-Type: application/json
Cache-Control: no-cache
```

#### If you use an invalid or missing header or token, the server will respond with `401 Unauthorized`.

</details>

---

### Disable or enable the approov service

The quickstart can be enabled/disabled by running the following commands:

```HTML
curl -X POST http://localhost:8080/approov/disable    # disable the approov service

curl -X POST http://localhost:8080/approov/enable     # enable the approov service

curl -X GET http://localhost:8080/approov-state      # check current state
```

#### You can rerun the tests with Approov disabled to observe how the application behaves when the Approov service is `no longer active`.

### Troubleshooting
- Ensure your Approov account credentials are still configured and valid. Approov credentials typically expire after two hours, so you may need to refresh or re-authenticate before running tests or making API requests.

| Problem                 | Likely Cause                                              |
| ----------------------- | --------------------------------------------------------- |
| `Invalid Token`         | Secret mismatch or hash mismatch in pay claim             |
| `Signature mismatch`    | Token's `pay` doesn't match the hash of Authorization     |
| `401 Unauthorized`      | Token expired or required headers missing                 |
| `Token looks wrong`     | Use approov token -check <token> to inspect it            |
| `Wrong secret format`   | Use approov secret -get base64.                           |

## Issues

If you find any issue while following our instructions then just report it [here](https://github.com/approov/quickstart-java-spring-token-check/issues), with the steps to reproduce it, and we will sort it out and/or guide you to the correct path.

### Useful Links

* [Approov Free Trial](https://approov.io/signup)(no credit card needed)
* [Approov Get Started](https://approov.io/product/demo)
* [Approov QuickStarts](https://approov.io/docs/latest/approov-integration-examples/)
* [Approov Docs](https://approov.io/docs)
* [Approov Blog](https://approov.io/blog/)
* [Approov Resources](https://approov.io/resource/)
* [Approov Customer Stories](https://approov.io/customer)
* [Approov Support](https://approov.io/contact)
* [About Us](https://approov.io/company)
* [Contact Us](https://approov.io/contact)


