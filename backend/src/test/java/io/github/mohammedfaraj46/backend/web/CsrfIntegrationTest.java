package io.github.mohammedfaraj46.backend.web;

import static io.github.mohammedfaraj46.backend.support.HttpTestSupport.CSRF_COOKIE_NAME;
import static io.github.mohammedfaraj46.backend.support.HttpTestSupport.CSRF_HEADER_NAME;
import static io.github.mohammedfaraj46.backend.support.HttpTestSupport.JWT_COOKIE_NAME;
import static io.github.mohammedfaraj46.backend.support.HttpTestSupport.asRequestCookie;
import static io.github.mohammedfaraj46.backend.support.HttpTestSupport.cookieValue;
import static io.github.mohammedfaraj46.backend.support.HttpTestSupport.fetchCsrf;
import static io.github.mohammedfaraj46.backend.support.HttpTestSupport.setCookie;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.mohammedfaraj46.backend.repository.AppUserRepository;
import io.github.mohammedfaraj46.backend.repository.CompanyRepository;
import io.github.mohammedfaraj46.backend.support.AbstractPostgresIntegrationTest;
import io.github.mohammedfaraj46.backend.support.HttpTestSupport.Csrf;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * CSRF protection for a SPA on cookie-based auth: state-changing calls need a
 * token that only same-origin JavaScript can read and echo back, while the two
 * endpoints you need before you have any token stay reachable.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@DisplayName("CSRF protection")
class CsrfIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String EMAIL = "csrf@example.nl";
    private static final String PASSWORD = "geheim123";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private CompanyRepository companyRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void resetDatabase() {
        companyRepository.deleteAll();
        appUserRepository.deleteAll();
    }

    @Test
    @DisplayName("a GET hands out a readable XSRF-TOKEN cookie")
    void getYieldsReadableCsrfCookie() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/auth/csrf", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        String cookie = setCookie(response, CSRF_COOKIE_NAME).orElseThrow();
        // the SPA has to read this one, so it must NOT be httpOnly
        assertThat(cookie).doesNotContain("HttpOnly");
        assertThat(cookie).contains("Path=/");

        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.get("headerName").asString()).isEqualTo(CSRF_HEADER_NAME);
        // body and cookie carry the same token, so the SPA can use either
        assertThat(body.get("token").asString())
                .isEqualTo(cookieValue(response, CSRF_COOKIE_NAME).orElseThrow());
    }

    @Test
    @DisplayName("register works without a CSRF token")
    void registerIsExemptFromCsrf() {
        assertThat(register().getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("login works without a CSRF token")
    void loginIsExemptFromCsrf() {
        register();

        ResponseEntity<String> response = login();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(setCookie(response, JWT_COOKIE_NAME)).isPresent();
    }

    @Test
    @DisplayName("a state-changing request without a CSRF token is refused with 403")
    void stateChangingRequestWithoutTokenIsRefused() {
        register();
        String jwtCookie = asRequestCookie(setCookie(login(), JWT_COOKIE_NAME).orElseThrow());

        ResponseEntity<String> response = post("/api/auth/logout", jwtCookie, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        // and the login cookie is untouched, so the request really did not happen
        assertThat(setCookie(response, JWT_COOKIE_NAME)).isEmpty();
    }

    @Test
    @DisplayName("a state-changing request with a valid CSRF token is accepted")
    void stateChangingRequestWithTokenIsAccepted() {
        register();
        String jwtCookie = asRequestCookie(setCookie(login(), JWT_COOKIE_NAME).orElseThrow());
        Csrf csrf = fetchCsrf(restTemplate);

        ResponseEntity<String> response =
                post("/api/auth/logout", jwtCookie + "; " + csrf.cookieHeader(), csrf.token());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    @DisplayName("a header that does not match the cookie is refused with 403")
    void mismatchedTokenIsRefused() {
        register();
        String jwtCookie = asRequestCookie(setCookie(login(), JWT_COOKIE_NAME).orElseThrow());
        Csrf csrf = fetchCsrf(restTemplate);

        // what a cross-site attacker could manage at best: the browser attaches the
        // cookies, but the attacker cannot read them to fill in the header
        ResponseEntity<String> response = post("/api/auth/logout",
                jwtCookie + "; " + csrf.cookieHeader(), "een-verzonnen-token");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("cookies alone, without the header, are not enough")
    void cookiesWithoutHeaderAreRefused() {
        register();
        String jwtCookie = asRequestCookie(setCookie(login(), JWT_COOKIE_NAME).orElseThrow());
        Csrf csrf = fetchCsrf(restTemplate);

        ResponseEntity<String> response =
                post("/api/auth/logout", jwtCookie + "; " + csrf.cookieHeader(), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private ResponseEntity<String> register() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.postForEntity("/api/auth/register", new HttpEntity<>("""
                {
                  "email": "%s",
                  "password": "%s",
                  "company": {
                    "name": "Test ZZP",
                    "kvkNumber": "12345678",
                    "vatNumber": "NL001234567B01",
                    "addressLine": "Straat 1",
                    "postalCode": "1011AB",
                    "city": "Amsterdam"
                  }
                }
                """.formatted(EMAIL, PASSWORD), headers), String.class);
    }

    private ResponseEntity<String> login() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.postForEntity("/api/auth/login", new HttpEntity<>("""
                {"email": "%s", "password": "%s"}
                """.formatted(EMAIL, PASSWORD), headers), String.class);
    }

    private ResponseEntity<String> post(String path, String cookieHeader, String csrfToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, cookieHeader);
        if (csrfToken != null) {
            headers.add(CSRF_HEADER_NAME, csrfToken);
        }
        return restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(headers), String.class);
    }
}
