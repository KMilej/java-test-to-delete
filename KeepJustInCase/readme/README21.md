# Approov Quickstart - Java Spring Token Check
<b>[Approov](https://approov.io) is an API security solution used to verify that requests received by your backend services originate from trusted versions of your mobile apps.</b>

This repository implements the Approov server-side request verification code using the Java Spring Boot Framework.

> **NOTE:** It provides a simple example API (api.example.com) that performs Approov token verification before allowing requests to access protected endpoints.
>
>The example demonstrates how different API endpoints `/unprotected, /token-check, /token-binding-check, etc.` respond based on the current Approov configuration.
>
>Each endpoint reflects whether Approov is enabled or disabled.
>
>These endpoints are tested using `curl` requests to observe how the security settings affect the responses.

### The quickstart was tested with the following environment:
```text
* Operating System: macOS 15.6.1 (Sequoia)
* JVM: 17.0.14
* Spring Boot version: 2.6.4
* Gradle version: 7.6.6
```

### TOC - Table of Contents

* [Nginx Server](#nginx-server)
* [Get Up and Running](#get-up-and-running)
* [Useful Commands](#useful-commands)
* [Running Automated Tests](#running-automated-tests)
* [Running Manual Tests](#running-manual-tests)
* [Troubleshooting](#troubleshooting)
* [Useful Links](#useful-links)

### Requirements
#### Before you start, If you are new to Approov, make sure you have the following:
* Approov CLI initialized [Follow the Approov CLI initialization guide](https://ext.approov.io/docs/latest/approov-installation/#initializing-the-approov-cli).
* [Sign up for the Approov Free Trial](https://approov.io/signup)(no credit card needed)
* [Get Started with Approov](https://approov.io/product/demo)

### Approov Token Verification Flow

1. **Token Request:**  
   The `Approov SDK` inside the mobile app securely communicates with the `Approov Cloud Service` to obtain a short-lived `Approov Token` (a signed JWT).

2. **Token Attachment:**  
   The app attaches this token to every API request using the `Approov-Token` HTTP header.

3. **Server Validation:**  
   The server verifies the token using the shared `**Approov secret**`, checking its:
    - Signature authenticity
    - Expiration (`exp` claim)
    - Other claims if configured

4. **(Optional) Token Binding:**  
   For extra protection, the app may include an additional `Approov-Token-Binding` header.  
   This binds the token to specific request data (for example, an access token or session ID).  
   The server ensures that this value matches the hash inside the token, preventing **token reuse or replay attacks**.

5. **Request Decision:**
    -  If all checks pass → the request is trusted and processed **`200 OK`**.
    -  If validation fails → the server responds with **`401 Unauthorized`**.

```json
"Try It Out Yourself, follow the steps below to run semi-automatically, manually or build and run it automatically using Docker."
```

---

<details>
<summary style="font-size:1.6em; line-height:1.6; display:flex; align-items:center;">
  <img src="https://www.docker.com/wp-content/uploads/2022/03/Moby-logo.png" width="40" style="vertical-align:middle; margin-right:10px;" />
  <strong>Run with Docker</strong>
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

</details>

---

<details>
<summary style="font-size:1.6em; line-height:1.6; display:flex; align-items:center;">
  <img src="https://cdn-icons-png.flaticon.com/512/3097/3097412.png" width="40" style="vertical-align:middle; margin-right:10px;" />
  <strong>Run Semi-Automatically in IDE</strong>
</summary>

### you should have already:
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

```bash
set -a  # auto-export all assignments
source .env && ./gradlew bootRun
set +a  # stop exporting variables
```

<h4>When the server is running, you can test the endpoints using bash script in a different terminal.</h4>

```bash
bash testall.sh
```



</details>

---

<details>
<summary style="font-size:1.6em; line-height:1.6; display:flex; align-items:center;">
  <img src="https://cdn-icons-png.flaticon.com/512/1828/1828817.png" width="40" style="vertical-align:middle; margin-right:10px;" />
  <strong>Run Manually</strong>
</summary>

### you should have already:
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

### ===========================================================
### 3. Approov Token Binding check
### ===========================================================

- The client sends two headers on authenticated API calls:
    - **`Approov-Token`**
    - **`Authorization`** header containing a hash of specific request data (e.g., access token or session ID).
- The server verifies the token **and** ensures that the bound value matches what the app used.
- Prevents token replay — the Approov token **cannot be reused or stolen** for another session.
- **Use case:** stronger protection for **authenticated API calls** tied to a specific user or device.

### ===========================================================
### 3. Approov Token Binding check with two different bound values
### ===========================================================

- The client sends three headers on authenticated API calls:
    - **`Approov-Token`**
    - **`Authorization`**
    - **`Content-Digest`** It is combined with the `Authorization` header to create a stronger binding.
- Both are included in the hash inside the Approov token. This means the server verifies a single hash that covers both authentication credentials.
- **Use case:** This configuration provides the highest level of protection for authenticated API requests:





</details>

---



## Useful Commands

### Disable or enable the approov service

The quickstart can be enabled/disabled by running the following commands:

```bash
curl -X POST http://localhost:8002/admin/approov/disable

curl -X POST http://localhost:8002/admin/approov/enable
```

## Get Up and Running

#### 1. Approov Role should be selected beforehand, an admin role is needed

```bash
approov role
```

#### 2. Add your api domain, if not added yet

```bash
approov api -add example.com 
approov api -list
```
#### 3. Run the automation setup script:

This script will:
* Run the api command list, to ensure user is logged in.
* Build the Docker image.
* Run a container using the image. The script maps docker volumes with the server
  nginx config and required lua code. It uses the default host port 8111 to
  expose the nginx server.

**NOTE:** If user is not logged in yet, approov cli will prompt the user to enter their password. If the user is logged in, then user will see a list of api domains.


### Troubleshooting
- [Check approov service is enabled or reload the server](#useful-commands)
- Ensure your Approov account credentials are still configured and valid. Approov credentials typically expire after two hours, so you may need to refresh or re-authenticate before running tests or making API requests.

| Problem                 | Likely Cause                                              |
| ----------------------- | --------------------------------------------------------- |
| `Invalid Token`         | Secret mismatch or hash mismatch in pay claim             |
| `Signature mismatch`    | Token's `pay` doesn't match the hash of Authorization     |
| `401 Unauthorized`      | Token expired or required headers missing                 |
| `Token looks wrong`     | Use approov token -check <token> to inspect it            |
| `Wrong secret format`   | Use approov secret -get base64.                           |

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

[Back to Table of Contents](#toc---table-of-contents)

