package io.github.mohammedfaraj46.backend.web;

import static io.github.mohammedfaraj46.backend.support.HttpTestSupport.CSRF_HEADER_NAME;
import static io.github.mohammedfaraj46.backend.support.HttpTestSupport.JWT_COOKIE_NAME;
import static io.github.mohammedfaraj46.backend.support.HttpTestSupport.asRequestCookie;
import static io.github.mohammedfaraj46.backend.support.HttpTestSupport.fetchCsrf;
import static io.github.mohammedfaraj46.backend.support.HttpTestSupport.setCookie;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.mohammedfaraj46.backend.domain.Company;
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
 * Editing the own company profile through PUT /api/companies/me.
 *
 * <p>There is no id in the path, so the ownership question is not "may I edit
 * this one" but "which one does this cookie mean": the tests check that a change
 * by A lands on A and leaves B untouched.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@DisplayName("Company profile")
class CompanyProfileIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String EMAIL_A = "profiel.a@example.nl";
    private static final String EMAIL_B = "profiel.b@example.nl";
    private static final String PASSWORD = "geheim123";
    private static final String COMPANY_ME = "/api/companies/me";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private CompanyRepository companyRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String sessionA;
    private String sessionB;

    @BeforeEach
    void resetAndSeedTwoUsers() {
        companyRepository.deleteAll();
        appUserRepository.deleteAll();

        register(EMAIL_A, "Bedrijf van A");
        register(EMAIL_B, "Bedrijf van B");
        sessionA = loginCookie(EMAIL_A);
        sessionB = loginCookie(EMAIL_B);
    }

    @Test
    @DisplayName("an update is returned and stored")
    void updateIsStored() {
        ResponseEntity<String> response = put(sessionA, profileJson("Faraj Advies", "Kerkstraat 2"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.get("name").asString()).isEqualTo("Faraj Advies");
        assertThat(body.get("addressLine").asString()).isEqualTo("Kerkstraat 2");
        assertThat(body.get("kvkNumber").asString()).isEqualTo("87654321");
        assertThat(body.get("vatNumber").asString()).isEqualTo("NL009876543B02");
        assertThat(body.get("defaultPaymentTermDays").asInt()).isEqualTo(14);

        Company stored = companyOf(EMAIL_A);
        assertThat(stored.getName()).isEqualTo("Faraj Advies");
        assertThat(stored.getCity()).isEqualTo("Utrecht");
        assertThat(stored.getDefaultPaymentTermDays()).isEqualTo(14);
        // the payload said "nl"; the DTO normalises the country code
        assertThat(stored.getCountry()).isEqualTo("NL");
        // and a later read returns the same thing
        assertThat(objectMapper.readTree(get(sessionA).getBody()).get("name").asString())
                .isEqualTo("Faraj Advies");
    }

    @Test
    @DisplayName("A editing their own profile leaves B untouched")
    void updateOnlyTouchesTheOwnCompany() {
        String nameOfBBefore = companyOf(EMAIL_B).getName();

        assertThat(put(sessionA, profileJson("Nieuwe naam", "Kerkstraat 2")).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(companyOf(EMAIL_B).getName()).isEqualTo(nameOfBBefore);
        assertThat(objectMapper.readTree(get(sessionB).getBody()).get("name").asString())
                .isEqualTo(nameOfBBefore);
    }

    @Test
    @DisplayName("missing fields give a 400 with a message per field")
    void missingFieldsAreRejected() {
        ResponseEntity<String> response = put(sessionA, """
                {
                  "name": "  ",
                  "kvkNumber": "87654321",
                  "vatNumber": "NL009876543B02",
                  "addressLine": "Kerkstraat 2",
                  "postalCode": "3511AA",
                  "city": "Utrecht",
                  "country": "NL",
                  "defaultPaymentTermDays": 0
                }
                """);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode errors = objectMapper.readTree(response.getBody()).get("errors");
        assertThat(errors.get("name").asString()).contains("name is required");
        assertThat(errors.get("defaultPaymentTermDays").asString()).contains("greater than zero");

        // nothing was written
        assertThat(companyOf(EMAIL_A).getName()).isEqualTo("Bedrijf van A");
    }

    @Test
    @DisplayName("editing requires authentication")
    void updateRequiresAuthentication() {
        assertThat(put(null, profileJson("Van een vreemde", "Straat 9")).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(companyOf(EMAIL_A).getName()).isEqualTo("Bedrijf van A");
    }

    // --- helpers ---------------------------------------------------------

    private static String profileJson(String name, String addressLine) {
        return """
                {
                  "name": "%s",
                  "kvkNumber": "87654321",
                  "vatNumber": "NL009876543B02",
                  "addressLine": "%s",
                  "postalCode": "3511AA",
                  "city": "Utrecht",
                  "country": "nl",
                  "defaultPaymentTermDays": 14
                }
                """.formatted(name, addressLine);
    }

    private Company companyOf(String email) {
        Long userId = appUserRepository.findByEmail(email).orElseThrow().getId();
        return companyRepository.findByUserId(userId).orElseThrow();
    }

    private ResponseEntity<String> get(String sessionCookie) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, sessionCookie);
        return restTemplate.exchange(COMPANY_ME, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    /** State-changing call: login cookie plus a CSRF token, like the SPA sends. */
    private ResponseEntity<String> put(String sessionCookie, String json) {
        Csrf csrf = fetchCsrf(restTemplate);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(HttpHeaders.COOKIE,
                (sessionCookie == null ? "" : sessionCookie + "; ") + csrf.cookieHeader());
        headers.add(CSRF_HEADER_NAME, csrf.token());

        return restTemplate.exchange(COMPANY_ME, HttpMethod.PUT, new HttpEntity<>(json, headers), String.class);
    }

    private void register(String email, String companyName) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = restTemplate.postForEntity("/api/auth/register",
                new HttpEntity<>("""
                        {
                          "email": "%s",
                          "password": "%s",
                          "company": {
                            "name": "%s",
                            "kvkNumber": "12345678",
                            "vatNumber": "NL001234567B01",
                            "addressLine": "Straat 1",
                            "postalCode": "1011AB",
                            "city": "Amsterdam"
                          }
                        }
                        """.formatted(email, PASSWORD, companyName), headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private String loginCookie(String email) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> login = restTemplate.postForEntity("/api/auth/login",
                new HttpEntity<>("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(email, PASSWORD), headers), String.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        return asRequestCookie(setCookie(login, JWT_COOKIE_NAME).orElseThrow());
    }
}
