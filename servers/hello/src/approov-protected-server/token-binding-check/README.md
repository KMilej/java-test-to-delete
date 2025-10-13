
## TOC - Table of Contents

* [Requirements](#requirements)
* [Automated script](#automated-script-use-this-to-quickly-run-all-examples-with-a-single-command)
* [Manual version](#manual-version-follow-the-steps-below-for-a-detailed-step-by-step-setup)
* [Approov Token Integration Example](#approov-token-integration-example)
* [Adding Approov Features](#adding-approov-features)
* [Approov Token protection](#approov-token-protection)
* [Approov Token Binding Integration Example](#approov-token-binding-integration-example)


## How quickstart works?

The Java Spring API server is intentionally simple. It exposes a single endpoint:

```json
{"message": "Hello, World!"}
```

By default, this endpoint can be called by **any client**, as no security check is performed.  
When **Approov protection** is added, the server verifies that each request truly comes from a **trusted and untampered mobile app**.

### 🔍 Under the Hood

1. **Token Request:**  
   The Approov SDK inside the mobile app securely communicates with the **Approov Cloud Service** to obtain a short-lived **Approov Token** (a signed JWT).

2. **Token Attachment:**  
   The app attaches this token to every API request using the `Approov-Token` HTTP header.

3. **Server Validation:**  
   The server verifies the token using the shared **Approov secret**, checking its:
    - Signature authenticity
    - Expiration (`exp` claim)
    - Other claims if configured

4. **Optional Token Binding:**  
   For extra protection, the app may include an additional `Approov-Token-Binding` header.  
   This binds the token to specific request data (for example, an access token or session ID).  
   The server ensures that this value matches the hash inside the token, preventing **token reuse or replay attacks**.

5. **Request Decision:**
    -  If all checks pass → the request is trusted and processed.
    -  If validation fails → the server responds with **`401 Unauthorized`**.

## Requirements

To run this example you will need to have installed:

* [OpenJDK](https://openjdk.java.net/install/) - This server example uses version `11.0.3`. It should work with earlier or later versions but was not tested.
* [Java Spring](https://docs.spring.io/spring-boot/docs/current/reference/html/getting-started.html#getting-started.installing) - Version `2.6.4` of the Spring Framework plugin is being used. The code should work with prior versions but wasn't tested.

### ===========================================================
### 1. Unprotected Endpoint (No Approov)
### ===========================================================

- The client sends a normal HTTPS request.
- The server **does not verify** any Approov token or extra authentication header.
- This means **any client** (even tampered or unauthorized) can call the API if they know the URL.

### ===========================================================
### 2. Approov Token Integration check
### ===========================================================

- The client includes an **`Approov-Token`** (a short-lived JWT) in each API request header.
- The server verifies this token using the **shared Approov secret** and checks:
    -  Token signature (authenticity)
    -  Expiration (`exp` claim)
    -  Audience or payload claims if configured
- If the token is valid → request is trusted.
- If invalid → server returns **`401 Unauthorized`**.
- **Use case:** secure endpoints ensuring the request comes from an **unmodified Approov-protected app**.

### ===========================================================
### 3. Approov Token Binding Integration
### ===========================================================

- The client sends both:
    - **`Approov-Token`**
    - **`Approov-Token-Binding`** header containing a hash of specific request data (e.g., access token or session ID).
- The server verifies the token **and** ensures that the bound value matches what the app used.
- Prevents token replay — the Approov token **cannot be reused or stolen** for another session.
- **Use case:** strongest protection for **authenticated API calls** tied to a specific user or device.

```json
"Try It Out Yourself, follow the steps below to run automatically or manually. "
```
[Back to Table of Contents](#toc---table-of-contents)
## Automated script: Use this to quickly run all examples with a single command.

```bash
bash approov_script.sh
```

## Manual version: Follow the steps below for a detailed, step-by-step setup.
### 🧩 Unprotected Server Example

This example runs the server **without any Approov protection** — useful for verifying that your setup works before enabling token checks.

---

### Build the Server

From the `./servers/hello/src/approov-server` directory, run:

```bash
./gradlew build
```

```bash
set -a  # auto-export all assignments
source .env && ./gradlew bootRun
set +a  # stop exporting variables
```

Finally, you can test that it works with:

```bash
curl -iX GET 'http://localhost:8002'
```

The response will be:

```json
{"message":"Hello, World!"}
```
<details>
<summary>Show detailed HTTP response</summary>

```http
HTTP/1.1 200
Vary: Origin
Vary: Access-Control-Request-Method
Vary: Access-Control-Request-Headers
X-Content-Type-Options: nosniff
X-XSS-Protection: 1; mode=block
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Pragma: no-cache
Expires: 0
X-Frame-Options: DENY
Content-Type: application/json
Transfer-Encoding: chunked
```
</details>

[Back to Table of Contents](#toc---table-of-contents)
# Approov Token Integration Example

Approov protection adds a verification step to ensure that every API request comes from a **trusted and authorized client** — not from tampered or unknown sources.

> 💡 **To enable Approov protection**, open `WebSecurityConfig.java`[here](src/main/java/com/criticalblue/approov/jwt/WebSecurityConfig.java).

> - Comment out **line 54**
> - Uncomment **lines 62–75**
> 
> > This activates the Approov token verification for incoming API requests.


### Try it again:

```bash
lsof -ti:8002 | xargs kill -9 2>/dev/null || true
set -a  # auto-export all assignments
source .env && ./gradlew bootRun
set +a  # stop exporting variables
```

Next, you can test that it works with:

```bash
curl -iX GET 'http://localhost:8002'
```

The response will be a `400` bad request beacuse code throws `ApproovAuthenticationException` it looks for the Approov token in the header of the request.


```text
HTTP/1.1 400
Vary: Origin
Vary: Access-Control-Request-Method
Vary: Access-Control-Request-Headers
X-Content-Type-Options: nosniff
X-XSS-Protection: 1; mode=block
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Pragma: no-cache
Expires: 0
X-Frame-Options: DENY
Content-Type: application/json
Transfer-Encoding: chunked

```
[Back to Table of Contents](#toc---table-of-contents)
## Adding Approov Features
 
`Make sure you have the Approov CLI installed. If you don't have it yet, please follow the instructions` [here](https://ext.approov.io/docs/latest/approov-installation/).
<details><summary>The Approov CLI installation example via the brew</summary>

```http
brew update
brew install approov
```
</details>

`Also you need Approov account if you dont have yet. You can sign up for a free trial` [here](https://approov.io/signup/) `. You will receive an email with the subject Approov Onboarding with activation information.`

## setting all settings

getting the account secret key requires an admin role
```bash
approov secret -get base64 -plain
```
The Approov account secret is highly sensitive — it can be used to generate valid tokens and must never be exposed or stored in public code.

Now, set the Approov account secret in the environment variable `APPROOV_BASE64_SECRET` inside the `.env` line 18, file [here](./.env).

After setting the Approov account secret you can run the server again:

```bash
lsof -ti:8002 | xargs kill -9 2>/dev/null || true
set -a  # auto-export all assignments
source .env && ./gradlew bootRun
set +a  # stop exporting variables
```

Now, register the API domain for which Approov will issues tokens:

```bash
approov api -add api.example.com
```

You can check the registered APIs with:

```bash
approov api -list
```
[Back to Table of Contents](#toc---table-of-contents)
## Approov Token protection

Approov Token with Valid Signature and Expire Time (1 hour on trial account). The Approov token was signed with a secret only known by the Approov Cloud service and the GoLang server.

Get an Approov token for the registered API domain with:

```bash
approov token -genExample api.example.com
```

Request:
```bash
curl -iX GET http://localhost:8002/ \
  --header 'Approov-Token: <Paste the Approov token here>'
```
EXAMPLE:
```html
curl -iX GET http://localhost:8002/ \
  --header 'Approov-Token: eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJleHAiOjQ3MDg2ODMyMDUuODkxOTEyfQ._ZdLOZmK4KXSIpVlhOpHBgboSHHTWer-X6oLqFIDQWI'
```

The response will be a `200` for request:

```text
HTTP/1.1 200
Vary: Origin
Vary: Access-Control-Request-Method
Vary: Access-Control-Request-Headers
X-Content-Type-Options: nosniff
X-XSS-Protection: 1; mode=block
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Pragma: no-cache
Expires: 0
X-Frame-Options: DENY
Content-Type: application/json
Transfer-Encoding: chunked
```
### > The Approov Token Check verifies each request includes a valid, non-expired JWT (Approov token) signed with the server’s secret to confirm the app’s authenticity. If the token is missing, invalid, or expired, the request is rejected to prevent untrusted or tampered clients from accessing the backend.

[Back to Table of Contents](#toc---table-of-contents)
# Approov Token Binding Integration Example


When **token binding** is enabled, each request must prove that the Approov token truly belongs to that specific client session.

- The client sends both:
    - **`Approov-Token`** — a signed JWT issued by Approov
    - **`Approov-Token-Binding`** — a hash of sensitive request data (e.g., an access token or session ID)

- The server verifies:
    -  That the binding hash matches the claim inside the Approov token

If both checks succeed, the request is trusted.  
If not, the server rejects it to prevent **token replay or misuse**.

Core implementation:
- [`ApproovAuthentication.java`](./src/main/java/com/criticalblue/approov/jwt/authentication/ApproovAuthentication.java) — verifies token and binding
- [`ApproovSecurityContextRepository.java`](./src/main/java/com/criticalblue/approov/jwt/authentication/ApproovSecurityContextRepository.java) — applies security context for the validation

> 💡 **Step 1:**  
> In [`ApproovSecurityContextRepository.java`](./src/main/java/com/criticalblue/approov/jwt/authentication/ApproovSecurityContextRepository.java):
> - **Uncomment lines 53–54**
> - **Comment out line 57**
>
> 💡 **Step 2:**  
> In [`ApproovAuthentication.java`](./src/main/java/com/criticalblue/approov/jwt/authentication/ApproovAuthentication.java):
> - **Uncomment line 95** to enable Approov token binding validation.


After code changing you need to run the server again:

```bash
lsof -ti:8002 | xargs kill -9 2>/dev/null || true
set -a  # auto-export all assignments
source .env && ./gradlew bootRun
set +a  # stop exporting variables
````

if you use the same command as in the token check example, you will receive a 400 Bad Request response because the server now expects an additional Approov-Token-Binding header in the request.

```bash
curl -iX GET http://localhost:8002/ \
  --header 'Approov-Token: <Paste the Approov token here>'
```

we need to add the Approov-Token-Binding header to the request, for example:

```bash
approov token -setDataHashInToken chosen_header_name -genExample api.example.com
```

we get token with the hash of the string "chosen_header_name" in the claim `pay`:
we can check both the token and the hash with by:

```bash
curl -iX GET 'http://localhost:8002/' \
  --header 'Approov-Token: <Paste the Approov token here>' \
  --header 'Authorization: chosen_header_name'
```

<details>
<summary>Example of the Approov token with the hash of the string <code>"chosen_header_name"</code> in the claim <code>pay</code></summary>

```html
curl -iX GET 'http://localhost:8002/' \
  --header 'Approov-Token: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJhdWQiOiIiLCJleHAiOjE3NjAwODc0NjMsImlwIjoiMS4yLjMuNCIsImRpZCI6IkV4YW1wbGVBcHByb292VG9rZW5ESUQ9PSIsInBheSI6Ikh6UlFQMlcwbzFXcFR1Vk5xT05GUVFCOHhtN0ZTTVliamErK29ob2FCNGM9In0.qYBHm1byrJt2weP1BrwkYrsZrtsEuvNI2-JNBRe6Y5w' \
  --header 'Authorization: chosen_header_name'
  ```
</details>

The response will be a `200` for request:

```text
HTTP/1.1 200
Vary: Origin
Vary: Access-Control-Request-Method
Vary: Access-Control-Request-Headers
X-Content-Type-Options: nosniff
X-XSS-Protection: 1; mode=block
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Pragma: no-cache
Expires: 0
X-Frame-Options: DENY
Content-Type: application/json
```

## Issues

If you find any issue while following our instructions then just report it [here](https://github.com/approov/quickstart-java-spring-token-check/issues), with the steps to reproduce it, and we will sort it out and/or guide you to the correct path.

[Back to Table of Contents](#toc---table-of-contents)

## Useful Links

If you wish to explore the Approov solution in more depth, then why not try one of the following links as a jumping off point:

* [Approov token binding check quickstart](/docs/APPROOV_TOKEN_BINDING_QUICKSTART.md)
* [Approov token check quickstart](/docs/APPROOV_TOKEN_QUICKSTART.md)
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

