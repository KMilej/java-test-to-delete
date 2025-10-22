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
<details>
  <summary><b>Docker Environment:</b></summary>
  <pre><code>* Docker version: 28.5.1
* Services:
    * app → Spring Boot application container (built from Dockerfile.dev)
    * tests → JDK 17 test container running ./testall.sh
* Base Image (Container OS): eclipse-temurin:17-jdk (Debian-based)
</code></pre>
</details>


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
   The server verifies the token using the shared **Approov secret**, checking its:
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








## Useful Commands

### Disable or enable the approov service

The quickstart can be enabled/disabled by running the following commands:

```bash
curl "http://localhost:8111/approov-toggle?enabled=true"
curl "http://localhost:8111/approov-toggle?enabled=false" 
```

### Restart NGINX After Code Changes

To test code changes live:

```bash
./reload_nginx.sh    
```

This script will reload NGINX automatically.

## Get Up and Running

#### 1. Approov Role should be selected beforehand, an admin role is needed

```bash
approov role
```

* For the Windows powershell:

```bash
set APPROOV_ROLE=admin:___YOUR_APPROOV_ACCOUNT_NAME_HERE___
```

#### 2. Add your api domain, if not added yet

```bash
approov api -add example.com 
approov api -list
```
#### 3. Run the automation setup script:

```bash
./run_cli.sh
```

This script will:
* Run the api command list, to ensure user is logged in.
* Build the Docker image.
* Run a container using the image. The script maps docker volumes with the server
  nginx config and required lua code. It uses the default host port 8111 to
  expose the nginx server.

**NOTE:** If user is not logged in yet, approov cli will prompt the user to enter their password. If the user is logged in, then user will see a list of api domains.

## Running Automated Tests

#### 1. Run the token check curl requests
Run them in a different shell, the first 2 tests should report a good token, the last 3 should report a bad token:

```bash
./openresty-server/tests/request_tests_approov.sh
```

Message signing tests are covered by other test scripts.

First, several tests checking the functionality of the HTTP structured field
values implementation, [RFC-9651](https://www.rfc-editor.org/rfc/rfc9651),
covered by sfv.lua. This was developed internally by Approov Ltd. base on other
open source implementations - specifically the Apache licensed implementation
used by the OkHttp Approov service layer.

```bash
./openresty-server/tests/request_tests_sfv.sh
```

Lastly, the Approov message signing tests use several steps to first build a
message and a signature and then send a request that verifies the message
reconstructed by the Approov token check flow. Signature properties are
provided by following the draft HTTP Message Signatures standard,
[RFC 9421](https://www.rfc-editor.org/rfc/rfc9421).

```bash
./tests/request_tests_approov_msg.sh
```
### Running Manual Tests

<details>
<summary>Manual Test Steps (step-by-step guide)</summary>

### Token Check

To use Approov with the Lua OpenResty server, a small amount of configuration is required.
- First, Approov must be configured with the domain of the API you are protecting.
- Second, the OpenResty server needs the Approov Base64-encoded secret, which is used to verify the tokens generated by the Approov cloud service.

This secret is included directly in the Lua token verification logic found in
[/openresty-server/lua/lua.d/approov.lua](/openresty-server/lua/lua.d/approov.lua), and must match the one assigned to your Approov account.

[Setup](#get-up-and-running) is the same one used for the automated tests

#### 1. Set up the Approov Secret
Approov tokens are signed with a symmetric secret. To verify tokens, we need to grab the secret using the [Approov secret command](https://approov.io/docs/latest/approov-cli-tool-reference/#secret-command) and plug it into the NodeJS API server environment to check the signatures of the [Approov Tokens](https://www.approov.io/docs/latest/approov-usage-documentation/#approov-tokens) that it processes.

* Health Check:
```bash
curl http://localhost:8111/hello
```

* Retrieve the Approov secret with:
```bash
approov secret -get base64
```

> **NOTE:** The `approov secret` command requires an [administration role](https://approov.io/docs/latest/approov-usage-documentation/#account-access-roles) to execute successfully.


* Set the Approov secret in the port80_server.conf, replacing the lines 100 and 158 in the NGINX config with the base64 secret.

```bash
set $jwt_secret “TEST+SECRET/TEST+SECRET/TEST+SECRET/TEST+SECRET/TEST+SECRET/TEST+SECRET/TEST+SECRET/AA”;
```
* Reload the server
```bash
./reload_server.sh
```

#### 2. Export Token
```bash
export TOKEN=$(approov token --genExample example.com | head -1)
```

* Call the endpoint
```bash
curl -i http://localhost:8111/token \
  -H "Approov-Token: $TOKEN"
```
> Expected: "200 OK"

### Token Binding with one-header

#### 1. Edit the NGINX Config
Configure your NGINX server to use only one-header for token binding. By setting the header list to {"authorization"}.

* Open the file ./openresty-server/conf/conf.d/port80_server.conf and uncomment the lines 149 to 151 to enable one-header binding.

```bash
rewrite_by_lua_block {
    ngx.ctx.approov_token_binding_headers = {"authorization"};
}
```
* Comment lines 154-156 to disable two-header binding.

#### 2. Choose header value to send
Pick the value you will use for the Authorization header in your test request. This value must be used consistently for both token generation and the request.
```bash
export HASH_INPUT="Bearer abc"
```

#### 3. Generate the Approov Token
Create an Approov token with a binding claim that matches the hash of your chosen header value. This ensures the token is valid only when the request includes the correct header.
```bash
export TOKEN_BIND1=$(approov token --genExample example.com \
  -setDataHashInToken "$HASH_INPUT" | head -1)
```

#### 4. Send the Test Request
Make a request to the /token_binding endpoint, including the Authorization header and the generated Approov token. The server will validate the token binding and respond with success if everything matches.
```bash
curl -i http://localhost:8111/token_binding \
  -H "Approov-Token: $TOKEN_BIND1" \
  -H "Authorization: Bearer abc"
```
> Expected response: HTTP/1.1 200 OK


### Token Binding with two headers

#### 1. Edit he NGINX Config
By uncommenting the specified lines, you instruct the Lua code to use both the authorization and x-device-id headers when validating the token binding. This ensures the server checks the combined values of these headers against the claim in the Approov token.

* Open the file ./openresty-server/conf/conf.d/port80_server.conf and uncomment the lines 154 to 156 to enable the two header binding test,
```bash
rewrite_by_lua_block {
    ngx.ctx.approov_token_binding_headers = {"authorization", "x-device-id"};
}
```
* Comment the line 149 to 151 to disable one-header test.

#### 2. Choose header values to send
Select the actual values you will use for the Authorization and X-Device-Id headers in your test request. These values must be consistent throughout the process, as they will be used to generate the hash input for the token and sent in the request.
```bash
export AUTH_VAL="Bearer abc"
export DEVICE_VAL="dev-123"
```

#### 3. Concatenate header values (No separator)
Combine the chosen header values into a single string, without any separator. This concatenated string is what the Approov token binding logic expects and will be used to generate the hash claim in the token.
```bash
export HASH_INPUT="${AUTH_VAL}${DEVICE_VAL}"
```

#### 4. Generate the Approov Token
Create an Approov token whose binding claim matches the hash of your concatenated header values. This step ensures the token is valid only if the request includes the exact header values you specified.
```bash
export TOKEN_BIND2=$(approov token --genExample example.com \
  -setDataHashInToken "$HASH_INPUT" | head -1)
```

#### 5. Send the Test Request
Make a request to the /token_binding endpoint, including both headers and the generated Approov token. If everything matches, the server will validate the token binding and respond with a success response.
```bash
curl -i http://localhost:8111/token_binding \
  -H "Approov-Token: $TOKEN_BIND2" \
  -H "Authorization: ${AUTH_VAL}" \
  -H "X-Device-Id: ${DEVICE_VAL}"
```
> Expected response: HTTP/1.1 200 OK
</details>

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

- [Approov CLI Reference](https://ext.approov.io/docs/latest/approov-cli-tool-reference/)
- [OpenResty + Lua Docs](https://openresty.org/)
- [Support](https://approov.io/contact/)

