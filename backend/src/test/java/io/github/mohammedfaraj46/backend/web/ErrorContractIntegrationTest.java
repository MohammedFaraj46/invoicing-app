package io.github.mohammedfaraj46.backend.web;

import static io.github.mohammedfaraj46.backend.support.HttpTestSupport.CSRF_HEADER_NAME;
import static io.github.mohammedfaraj46.backend.support.HttpTestSupport.JWT_COOKIE_NAME;
import static io.github.mohammedfaraj46.backend.support.HttpTestSupport.asRequestCookie;
import static io.github.mohammedfaraj46.backend.support.HttpTestSupport.fetchCsrf;
import static io.github.mohammedfaraj46.backend.support.HttpTestSupport.setCookie;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.mohammedfaraj46.backend.repository.AppUserRepository;
import io.github.mohammedfaraj46.backend.repository.CompanyRepository;
import io.github.mohammedfaraj46.backend.repository.CustomerRepository;
import io.github.mohammedfaraj46.backend.repository.InvoiceRepository;
import io.github.mohammedfaraj46.backend.repository.NumberSequenceRepository;
import io.github.mohammedfaraj46.backend.support.AbstractPostgresIntegrationTest;
import io.github.mohammedfaraj46.backend.support.HttpTestSupport.Csrf;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
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
 * The error contract: every failure, from any layer, answers the same way.
 *
 * <p>Three layers can reject a request and they used to disagree. The security
 * filter chain wrote a bare status with no body at all. Spring itself answered a
 * malformed body or an unknown path in the {@code timestamp/status/error/path}
 * shape. Only the controllers produced problem+json. A client had to know which
 * layer it was talking to in order to read the message.
 *
 * <p>These tests pin all three to one shape, and check that none of them leaks
 * an exception name, a stack frame or SQL.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@DisplayName("Error contract")
class ErrorContractIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String EMAIL = "fouten@example.nl";
    private static final String PASSWORD = "geheim123";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private NumberSequenceRepository numberSequenceRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String session;
    private long customerId;

    @BeforeEach
    void resetAndSignIn() {
        invoiceRepository.deleteAll();
        numberSequenceRepository.deleteAll();
        customerRepository.deleteAll();
        companyRepository.deleteAll();
        appUserRepository.deleteAll();

        register();
        session = loginCookie();
        customerId = createCustomer();
    }

    @AfterEach
    void clearSequences() {
        invoiceRepository.deleteAll();
        numberSequenceRepository.deleteAll();
    }

    @Test
    @DisplayName("401 carries a problem+json body, not an empty one")
    void unauthenticatedHasABody() {
        ResponseEntity<String> response =
                restTemplate.getForEntity("/api/invoices", String.class);

        assertProblem(response, HttpStatus.UNAUTHORIZED);
        assertThat(bodyOf(response).get("detail").asString()).isEqualTo("Authentication required");
    }

    @Test
    @DisplayName("403 from a missing CSRF token is problem+json")
    void csrfRejectionIsProblemJson() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(HttpHeaders.COOKIE, session);

        ResponseEntity<String> response = restTemplate.exchange("/api/customers", HttpMethod.POST,
                new HttpEntity<>("{}", headers), String.class);

        assertProblem(response, HttpStatus.FORBIDDEN);
        assertThat(bodyOf(response).get("detail").asString()).contains("CSRF");
    }

    @Test
    @DisplayName("a malformed JSON body is a problem+json 400")
    void malformedBodyIsProblemJson() {
        ResponseEntity<String> response = send(HttpMethod.POST, "/api/customers", "{kapot");

        assertProblem(response, HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("an id that is not a number is a problem+json 400")
    void unparseableIdIsProblemJson() {
        assertProblem(get("/api/invoices/abc"), HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("an unknown path is a problem+json 404")
    void unknownPathIsProblemJson() {
        assertProblem(get("/api/bestaat-niet"), HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("a wrong method is a problem+json 405")
    void wrongMethodIsProblemJson() {
        assertProblem(get("/api/auth/logout"), HttpStatus.METHOD_NOT_ALLOWED);
    }

    @Test
    @DisplayName("validation keeps its per-field map inside the same shape")
    void validationKeepsItsFieldErrors() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = restTemplate.postForEntity("/api/auth/register",
                new HttpEntity<>("{}", headers), String.class);

        assertProblem(response, HttpStatus.BAD_REQUEST);
        JsonNode errors = bodyOf(response).get("errors");
        assertThat(errors.get("email").asString()).isNotBlank();
        assertThat(errors.get("password").asString()).isNotBlank();
    }

    @Test
    @DisplayName("404 for an unknown id and 409 for a conflicting state keep the shape")
    void domainFailuresKeepTheShape() {
        assertProblem(get("/api/invoices/999999"), HttpStatus.NOT_FOUND);

        // a draft has no number, so it has no PDF: yours, but not in a state
        // that allows what was asked
        long draft = createDraft();
        assertProblem(get("/api/invoices/" + draft + "/pdf"), HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("no error response leaks an exception, a stack frame or SQL")
    void nothingInternalLeaks() {
        ResponseEntity<String>[] responses = new ResponseEntity[]{
                restTemplate.getForEntity("/api/invoices", String.class),
                get("/api/bestaat-niet"),
                get("/api/invoices/abc"),
                get("/api/invoices/999999"),
                get("/api/auth/logout"),
                send(HttpMethod.POST, "/api/customers", "{kapot"),
                send(HttpMethod.POST, "/api/invoices", "{\"customerId\": 999999, \"lines\": []}"),
        };

        for (ResponseEntity<String> response : responses) {
            String body = response.getBody() == null
                    ? "" : response.getBody().toLowerCase(Locale.ROOT);

            assertThat(body)
                    .as("body of %s", response.getStatusCode())
                    .doesNotContain("exception")
                    .doesNotContain("org.springframework")
                    .doesNotContain("io.github.mohammedfaraj46")
                    .doesNotContain("\tat ")
                    .doesNotContain("select ")
                    .doesNotContain("insert into")
                    .doesNotContain("constraint");
        }
    }

    // --- helpers ---------------------------------------------------------

    /** Every failure is problem+json with the four fields a client reads. */
    private void assertProblem(ResponseEntity<String> response, HttpStatus expected) {
        assertThat(response.getStatusCode()).isEqualTo(expected);
        assertThat(response.getHeaders().getContentType())
                .as("content type of %s", expected)
                .isNotNull()
                .satisfies(type -> assertThat(type.toString()).startsWith("application/problem+json"));

        JsonNode body = bodyOf(response);
        assertThat(body.get("status").asInt()).isEqualTo(expected.value());
        assertThat(body.get("title").asString()).isNotBlank();
        assertThat(body.get("detail").asString()).isNotBlank();
        assertThat(body.get("instance").asString()).startsWith("/api/");
    }

    private JsonNode bodyOf(ResponseEntity<String> response) {
        assertThat(response.getBody()).as("response body").isNotNull();
        return objectMapper.readTree(response.getBody());
    }

    private long createDraft() {
        ResponseEntity<String> response = send(HttpMethod.POST, "/api/invoices", """
                {
                  "customerId": %d,
                  "lines": [
                    {"description": "Werk", "quantity": 1, "unitPrice": 100.00, "vatRate": 21, "vatType": "STANDARD"}
                  ]
                }
                """.formatted(customerId));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return objectMapper.readTree(response.getBody()).get("id").asLong();
    }

    private long createCustomer() {
        ResponseEntity<String> response = send(HttpMethod.POST, "/api/customers", """
                {
                  "name": "Zon en Zo",
                  "email": "klant@example.nl",
                  "addressLine": "Straat 1",
                  "postalCode": "1011AB",
                  "city": "Amsterdam"
                }
                """);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return objectMapper.readTree(response.getBody()).get("id").asLong();
    }

    private ResponseEntity<String> get(String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, session);
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private ResponseEntity<String> send(HttpMethod method, String path, String json) {
        Csrf csrf = fetchCsrf(restTemplate);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(HttpHeaders.COOKIE, session + "; " + csrf.cookieHeader());
        headers.add(CSRF_HEADER_NAME, csrf.token());

        return restTemplate.exchange(path, method, new HttpEntity<>(json, headers), String.class);
    }

    private void register() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = restTemplate.postForEntity("/api/auth/register",
                new HttpEntity<>("""
                        {
                          "email": "%s",
                          "password": "%s",
                          "company": {
                            "name": "Foutenbedrijf",
                            "kvkNumber": "12345678",
                            "vatNumber": "NL001234567B01",
                            "addressLine": "Straat 1",
                            "postalCode": "1011AB",
                            "city": "Amsterdam"
                          }
                        }
                        """.formatted(EMAIL, PASSWORD), headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private String loginCookie() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> login = restTemplate.postForEntity("/api/auth/login",
                new HttpEntity<>("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(EMAIL, PASSWORD), headers), String.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        return asRequestCookie(setCookie(login, JWT_COOKIE_NAME).orElseThrow());
    }
}
