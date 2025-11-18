# Approov Quickstart - Java Spring Token Check

#### This `quickstart` implements the [Approov](https://approov.io) server-side request verification code using the Java Spring Boot Framework.

Approov provides comprehensive [runtime application self protection](https://approov.io/mobile-app-security/rasp) delivering app attestation, dynamic certificate pinning, runtime secrets protection, and API shielding - all unified across Android, iOS, and HarmonyOS.

It provides a simple example API `example.com` that performs Approov token verification before allowing requests to access protected endpoints.

---
- The example demonstrates how different API endpoints `/unprotected, /token-check, /token-binding-check, etc.` respond based on the current [Approov configuration](https://ext.approov.io/docs/latest/approov-usage-documentation/#getting-all-api-configuration).
- Each endpoint reflects whether Approov is enabled or disabled.
- These endpoints are tested using `curl` requests to observe how the security settings affect the responses.

### The quickstart was tested with the following environment:
```text
* Operating System: macOS Sequoia 15.6.1 (Darwin 15.6, ARM64)
* JVM: 17.0.14
* Spring Boot version: 2.6.4
* Gradle version: 7.6.6
```

---

### Requirements:

* **_[Follow the Approov CLI initialization guide](https://ext.approov.io/docs/latest/approov-installation/#initializing-the-approov-cli)._**
* **_[Sign up for the Approov Free Trial](https://approov.io/signup) (no credit card needed)._**

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

#### _"Try it yourself by following the steps below with any of the available options"_

<details>
<summary style="font-size:1.6em; line-height:1.6; display:flex; align-items:center;">
  <img src="https://www.docker.com/wp-content/uploads/2022/03/Moby-logo.png" width="40" style="vertical-align:middle; margin-right:10px;" />
  <strong>Run Using Docker</strong>
</summary>

## Requirements

```test

Docker Environment:
- Docker: 28.5.1+
- Docker Compose: v2.40.2+ 
- Docker Colima: 0.9.1+

* Base Image (Container OS): eclipse-temurin:17-jdk

(Optional) Host Platform: Colima on macOS or Docker Desktop
```

#### Install Docker, Colima and Docker-Compose via Homebrew
```bash # On macOS install with Homebrew
brew install docker colima docker-compose
```

#### Enable Docker Compose v2 plugin
```text
mkdir -p ~/.docker/cli-plugins
ln -sfn $(which docker-compose) ~/.docker/cli-plugins/docker-compose
```

#### Configure API secrets
```bash
bash set-secret-api.sh
```

If you have all requirements installed, you can build and run the example inside `quickstart-java-spring-token-check`:
```bash
bash run-server.sh
```

This script will:
- Checks environment requirements - verifies that Approov CLI, Docker, Docker Compose v2, and Colima are installed and running.
- Builds and starts containers – runs `docker compose -f compose.yaml up -d --build app` to build the image and launch the Spring Boot application in the background.
- Runs test.sh – executes all endpoint tests `unprotected (no headers)` `token-check (with Approov-Token)` `token-binding (with Approov-Token + Authorization header`
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

#### inside the project folder `quickstart-java-spring-token-check`, run:

#### Build the project:
```bash
./gradlew build
```

#### Environment Setup Script - `set-approov-secret.sh`
```bash
bash set-secret-api.sh
```

This script will:
- Creates the `.env` file automatically if `.env does not exist, the script copies it from `.env.example`.
- Run `approov secret -get base64` to retrieve the Approov secret from the Approov CLI and sets it in the `.env` file.
- Registers the domain example.com in the Approov cloud configuration so it can be protected by Approov.

#### Run the server:
```bash
set -a  # auto-export all assignments
source .env && ./gradlew bootRun
set +a  # stop exporting variables
```

<h4>When the server is running, you can test the endpoints using bash script in a different terminal.</h4>

```bash
bash test.sh
```

This script will:
- Checks Approov authentication and required tools - verifies that approov and curl are available, and confirms Approov CLI login works.
- tests Approov state and setup - calls /approov-state to detect whether token checking is enabled or disabled.
- Runs endpoint tests - performs HTTP requests for: `/unprotected (no token), /token-check (valid/invalid tokens), /token-binding-1 and /token-binding-2 (request is sent with bound headers: Authorization and Content-Digest).`

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

#### inside the project folder `quickstart-java-spring-token-check`, run:

#### Build the project:
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
#### Register the API domain `example.com` with the Approov CLI by:
```bash
approov api -add example.com
```

#### Run the server:
```bash
set -a  # auto-export all assignments
source .env && ./gradlew bootRun
set +a  # stop exporting variables
```

## *1. Unprotected Endpoint (No Approov)*

- The client sends a normal HTTPS request.
- The server **does not verify** any Approov token or extra authentication header.
- This means **any client** (even tampered or unauthorized) can call the API if they know the URL.

#### The following example shows how the API responds when no Approov protection is applied.
```bash
curl -iX GET http://localhost:8080/unprotected
```

The response will be a `200` for request:
```text
HTTP/1.1 200 OK
Content-Type: application/json
Cache-Control: no-cache
```

## *2. Approov Token Check*

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
approov token -genExample example.com
```

#### Use the generated token in the `Approov-Token` header and /token-check endpoint.
```bash
curl -iX GET http://localhost:8080/token-check \
     -H "Approov-Token: valid_approov_token_here"
```

The response will be a `200` for request:

```text
HTTP/1.1 200 OK
Content-Type: application/json
Cache-Control: no-cache
```

#### If you use an invalid or missing token, the server will respond with `401 Unauthorized`.

## *3. Approov Token Binding Check*

- The client sends two headers on authenticated API calls:
    - **`Approov-Token`**
    - **`Authorization`** header containing a hash of specific request data (e.g., access token or session ID).
- The server verifies the token **and** ensures that the bound value matches what the app used.
- Prevents token replay - the Approov token **cannot be reused or stolen** for another session.
- **Use case:** stronger protection for **authenticated API calls** tied to a specific user or device.

#### The following example shows how the API responds when an Approov token with binding is required.

#### Generate a valid Approov Token with binding:
```bash
approov token -setDataHashInToken your-custom-name -genExample example.com
```

#### Use the generated token with binding in the Approov-Token and Authorization headers when calling the /token-binding-1 endpoint.

```bash
curl -iX GET http://localhost:8080/token-binding-1 \
     -H "Approov-Token: valid_approov_token_here" \
     -H "Authorization: your-custom-name"
```

The response will be a `200` for request:

```text
HTTP/1.1 200 OK
Content-Type: application/json
Cache-Control: no-cache
```

#### If you use an invalid or missing header or token, the server will respond with `401 Unauthorized`.

## *4. Approov Token Binding Check with Two Different Bound Values*

- The client sends three headers on authenticated API calls:
    - **`Approov-Token`**
    - **`Authorization`**
    - **`Content-Digest`** It is combined with the `Authorization` header to create a stronger binding.
- Both are included in the hash inside the Approov token. This means the server verifies a single hash that covers both authentication credentials.
- **Use case:** This configuration provides the highest level of protection for authenticated API requests:

### <em>The following example shows how the API responds when an Approov token with two bindings is required.</em>

#### Generate a valid Approov Token with two binding:
```bash
approov token -setDataHashInToken ExampleAuthToken==ContentDigest== -genExample example.com
```

#### Use the generated token with two binding in the Approov-Token and Authorization headers when calling the /token-binding-2 endpoint.

```bash
curl -iX GET http://localhost:8080/token-binding-2 \
     -H "Approov-Token: valid_approov_token_here" \
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

<details>
<summary style="font-size:1.6em; line-height:1.6; display:flex; align-items:center;">
  <img src="https://img.shields.io/badge/IDE-gray?style=for-the-badge&logoColor=white" width="40" style="vertical-align:middle; margin-right:10px;" />
 <strong>HTTP Message Signing </strong>
</summary>

---
### Message signing protects against tampering and replay by requiring each request to carry an [HTTP message signature](https://ext.approov.io/docs/latest/approov-usage-documentation/#installation-message-signing) generated on the device. 

#### This guide explains how the Java Spring quickstart verifies those signatures using the installation public key (ipk) delivered inside the token.

*_The ipk_message_sign_test endpoint is the one used to construct a message signature that is then fed back into the server in the token or token_binding endpoint for verification. It is used by msg-test.sh to build the request properties for checking the message signing flow._*

#### Add or change secret on .env file for:
##### `APPROOV_BASE64_SECRET=TEST+SECRET/TEST+SECRET/TEST+SECRET/TEST+SECRET/TEST+SECRET/TEST+SECRET/TEST+SECRET/AA==`

#### Build the project:
```bash
./gradlew build
```

#### Run the server:
```bash
set -a  # auto-export all assignments
source .env && ./gradlew bootRun
set +a  # stop exporting variables
```

If you changed the secret in the .env file, you can run the Bash script located in the `quickstart-java-spring-token-check` path

```bash
bash msg-test.sh
```

This script will do four tests to verify the message signing flow:
- *Test 1* checks that a signed GET including only the HTTP method and approov-token is accepted.
- *Test 2* adds the canonical request URI to the signature to confirm URI binding works for GET.
- *Test 3* switches to POST, includes the body hash as a string content-digest, and ensures the server validates it.
- *Test 4* repeats the POST but encodes content-digest as a byte sequence to verify that format also passes.

#### To reproduce the manual message-signing check without bash script:

### Test 1: GET with token and signature

```bash
export GOOD_IPK_TOKEN='eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJhdWQiOiJhcHByb292LmlvIiwiZXhwIjoxOTk5OTk5OTk5LCJpYXQiOjE3MDAwMDAwMDAsImlzcyI6IkFwcHJvb3ZBY2NvdW50SUQuYXBwcm9vdi5pbyIsInN1YiI6ImFwcHJvb3Z8RXhhbXBsZUFwcHJvb3ZUb2tlbkRJRD09IiwiaXAiOiIxLjIuMy40IiwiaXBrIjoiTUZrd0V3WUhLb1pJemowQ0FRWUlLb1pJemowREFRY0RRZ0FFSlNtNERNY2l2QXd2aE0rS05jZTJDL1gyNmNqM29HeVV3V1ZVUHVOdVpIdGQycXlWc00rMGc3cVg3M1FoME9mNmZuMTBBQXBMbmw4dlJRc3Z4OTRmWlE9PSIsImRpZCI6IkV4YW1wbGVBcHByb292VG9rZW5ESUQ9PSJ9.hV6xTkGsp9uWwrD-yKkIGTBJawbofJEsuRLw9Qa5YXY'
# A valid Approov token for testing the signature verification

export TEST_PRIVATE_KEY='MHcCAQEEIHWZ2Ueq6odQNG+aaYmEbp7C6nujYNGr7nYKK2jqQ2asoAoGCCqGSM49AwEHoUQDQgAEJSm4DMcivAwvhM+KNce2C/X26cj3oGyUwWVUPuNuZHtd2qyVsM+0g7qX73Qh0Of6fn10AApLnl8vRQsvx94fZQ=='
# DER-encoded EC P-256 private key used to generate the signature

TEST1_TARGET_URI="http://0.0.0.0:8080/token?param1=value1&param2=value2"
# The endpoint that will receive the signed request

TEST1_SIGNATURE_INPUT='("@method" "approov-token");alg="ecdsa-p256-sha256";created=1744292750;expires=1999999999'
# Structured fields included in the signature + algorithm + timestamps

TEST1_MESSAGE="$(cat <<EOF
"@method": GET
"approov-token": $GOOD_IPK_TOKEN
"@signature-params": ("@method" "approov-token");alg="ecdsa-p256-sha256";created=1744292750;expires=1999999999
EOF
)"
# Canonical message built exactly the way the server reconstructs it
# This MUST match the backend reconstruction for signature verification to work

TEST1_MESSAGE_B64=$(printf '%s' "$TEST1_MESSAGE" | base64 | tr -d '\n')
# Canonical message must be Base64-encoded before being signed

TEST1_SIG=$(curl -sS \
  -H "private-key: ${TEST_PRIVATE_KEY}" \
  -H "msg: ${TEST1_MESSAGE_B64}" \
  "http://0.0.0.0:8080/ipk_message_sign_test")
# The demo server signs the message using your private key
# The returned value is the detached ECDSA-P256 signature

curl -sS -i \
  -H "approov-token: ${GOOD_IPK_TOKEN}" \
  -H "signature: install=:${TEST1_SIG}:" \
  -H 'signature-input: install=("@method" "approov-token");alg="ecdsa-p256-sha256";created=1744292750;expires=1999999999' \
  "http://0.0.0.0:8080/token?param1=value1&param2=value2"
# Final request:
# - Includes the Approov token
# - Includes the signature
# - Includes the signature-input description
unset GOOD_IPK_TOKEN TEST_PRIVATE_KEY

```
***_If everything matches, the response will be a `200` for request:_***

```text
HTTP/1.1 200
Vary: Origin
Vary: Access-Control-Request-Method
Vary: Access-Control-Request-Headers
```

</details>

---

### Disable or enable the approov service

The quickstart can be enabled/disabled by running the following commands:

```HTML
curl -X POST http://localhost:8080/approov/disable    # disable the approov service

curl -X POST http://localhost:8080/approov/enable     # enable the approov service

curl -X GET http://localhost:8080/approov-state       # check current state
```

#### You can rerun the tests with Approov disabled to observe how the application behaves when the Approov service is `no longer active`.

## Issues

If you find any issue while following our instructions then just report it [here](https://github.com/approov/quickstart-java-spring-token-check/issues), with the steps to reproduce it, and we will sort it out and/or guide you to the correct path.

### Useful Links

* [Approov QuickStarts](https://approov.io/docs/latest/approov-integration-examples/)
* [Approov Docs](https://approov.io/docs)
* [Approov Blog](https://approov.io/blog/)
* [Approov Resources](https://approov.io/resource/)
* [Approov Customer Stories](https://approov.io/customer)
* [Approov Support](https://approov.io/contact)
* [About Us](https://approov.io/company)
* [Contact Us](https://approov.io/contact)


