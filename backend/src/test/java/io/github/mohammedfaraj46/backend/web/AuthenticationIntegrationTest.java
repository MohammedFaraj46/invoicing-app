package io.github.mohammedfaraj46.backend.web;

import static io.github.mohammedfaraj46.backend.support.HttpTestSupport.CSRF_HEADER_NAME;
import static io.github.mohammedfaraj46.backend.support.HttpTestSupport.JWT_COOKIE_NAME;
import static io.github.mohammedfaraj46.backend.support.HttpTestSupport.asRequestCookie;
import static io.github.mohammedfaraj46.backend.support.HttpTestSupport.cookieValue;
import static io.github.mohammedfaraj46.backend.support.HttpTestSupport.fetchCsrf;
import static io.github.mohammedfaraj46.backend.support.HttpTestSupport.maxAgeOf;
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
 * Covers the whole cookie login chain over real HTTP: log in, use the cookie on
 * a protected endpoint, and log out again.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@DisplayName("JWT cookie authentication")
class AuthenticationIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String EMAIL = "inlog@example.nl";
    private static final String PASSWORD = "geheim123";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private CompanyRepository companyRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void resetAndRegister() {
        companyRepository.deleteAll();
        appUserRepository.deleteAll();

        ResponseEntity<String> registration = postJson("/api/auth/register", """
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
                """.formatted(EMAIL, PASSWORD));
        assertThat(registration.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("valid credentials return 200 and set an httpOnly cookie")
    void successfulLoginSetsCookie() {
        ResponseEntity<String> response = login(EMAIL, PASSWORD);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        String jwtCookie = setCookie(response, JWT_COOKIE_NAME).orElseThrow();
        assertThat(jwtCookie).contains("HttpOnly");
        assertThat(jwtCookie).contains("SameSite=Lax");
        assertThat(jwtCookie).contains("Path=/");
        // cookie-secure is off for local HTTP development
        assertThat(jwtCookie).doesNotContain("Secure");
        assertThat(maxAgeOf(jwtCookie)).isGreaterThan(0);

        // the token itself must not be readable as plain data
        String token = cookieValue(response, JWT_COOKIE_NAME).orElseThrow();
        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3);
        assertThat(response.getBody()).doesNotContain(PASSWORD);
    }

    @Test
    @DisplayName("/me returns the signed-in user when the cookie is sent")
    void meReturnsUserWithCookie() {
        String cookie = jwtCookieHeaderFrom(login(EMAIL, PASSWORD));

        ResponseEntity<String> response = exchange("/api/auth/me", HttpMethod.GET, cookie, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.get("email").asString()).isEqualTo(EMAIL);
        assertThat(body.get("id").asLong())
                .isEqualTo(appUserRepository.findByEmail(EMAIL).orElseThrow().getId());
        assertThat(body.get("companyId").asLong())
                .isEqualTo(companyRepository.findAll().getFirst().getId());
    }

    @Test
    @DisplayName("/me returns 401 without a cookie")
    void meIsUnauthorizedWithoutCookie() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/auth/me", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("/me returns 401 when the cookie holds a garbage token")
    void meIsUnauthorizedWithTamperedCookie() {
        ResponseEntity<String> response =
                exchange("/api/auth/me", HttpMethod.GET, JWT_COOKIE_NAME + "=not.a.valid.token", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("wrong password returns 401 without a login cookie")
    void wrongPasswordIsRejected() {
        ResponseEntity<String> response = login(EMAIL, "verkeerd-wachtwoord");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(setCookie(response, JWT_COOKIE_NAME)).isEmpty();
    }

    @Test
    @DisplayName("unknown email returns the same 401 as a wrong password")
    void unknownEmailIsRejectedIdentically() {
        ResponseEntity<String> unknown = login("bestaat.niet@example.nl", PASSWORD);
        ResponseEntity<String> wrongPassword = login(EMAIL, "verkeerd-wachtwoord");

        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        // identical body, so the response cannot reveal whether the account exists
        assertThat(unknown.getBody()).isEqualTo(wrongPassword.getBody());
    }

    @Test
    @DisplayName("logout clears the cookie and /me stops working")
    void logoutClearsCookie() {
        String jwtCookie = jwtCookieHeaderFrom(login(EMAIL, PASSWORD));
        Csrf csrf = fetchCsrf(restTemplate);

        // logout changes state, so it needs a CSRF token like any other POST
        ResponseEntity<String> logout = exchange("/api/auth/logout", HttpMethod.POST,
                jwtCookie + "; " + csrf.cookieHeader(), csrf.token());

        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        String cleared = setCookie(logout, JWT_COOKIE_NAME).orElseThrow();
        assertThat(cookieValue(logout, JWT_COOKIE_NAME)).contains("");
        assertThat(maxAgeOf(cleared)).isZero();

        // the browser would now send nothing, which is a 401 again
        assertThat(restTemplate.getForEntity("/api/auth/me", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private ResponseEntity<String> login(String email, String password) {
        return postJson("/api/auth/login", """
                {"email": "%s", "password": "%s"}
                """.formatted(email, password));
    }

    private ResponseEntity<String> postJson(String path, String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.postForEntity(path, new HttpEntity<>(json, headers), String.class);
    }

    private ResponseEntity<String> exchange(String path, HttpMethod method,
                                            String cookieHeader, String csrfToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, cookieHeader);
        if (csrfToken != null) {
            headers.add(CSRF_HEADER_NAME, csrfToken);
        }
        return restTemplate.exchange(path, method, new HttpEntity<>(headers), String.class);
    }

    private static String jwtCookieHeaderFrom(ResponseEntity<String> response) {
        return asRequestCookie(setCookie(response, JWT_COOKIE_NAME).orElseThrow());
    }
}
