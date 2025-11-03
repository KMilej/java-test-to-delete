package com.criticalblue.approov.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApplicationTests {

    @Autowired
    private TestRestTemplate restTemplate;

    @AfterEach
    void resetApproovState() {
        restTemplate.postForEntity("/approov/enable", null, Void.class);
    }

    @Test
    void contextLoads() {
        // Ensures the application context starts successfully.
    }

    @Test
    void indexReturnsWelcomeMessage() {
        ResponseEntity<String> response = restTemplate.getForEntity("/", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("Welcome to the Approov JWT Demo API Server!");
    }

    @Test
    void approovStateReflectsEnableDisableEndpoints() {
        restTemplate.postForEntity("/approov/disable", null, Void.class);
        ResponseEntity<java.util.Map<String, Object>> disabledState =
                restTemplate.exchange(
                        "/approov-state",
                        HttpMethod.GET,
                        null,
                        new ParameterizedTypeReference<java.util.Map<String, Object>>() {});

        assertThat(disabledState.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(disabledState.getBody())
                .containsEntry("approovEnabled", false)
                .containsEntry("tokenBindingEnabled", false);

        restTemplate.postForEntity("/approov/enable", null, Void.class);
        ResponseEntity<java.util.Map<String, Object>> enabledState =
                restTemplate.exchange(
                        "/approov-state",
                        HttpMethod.GET,
                        null,
                        new ParameterizedTypeReference<java.util.Map<String, Object>>() {});

        assertThat(enabledState.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(enabledState.getBody())
                .containsEntry("approovEnabled", true)
                .containsEntry("tokenBindingEnabled", true);
    }
}
