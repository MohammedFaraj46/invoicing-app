package io.github.mohammedfaraj46.backend.web;

import static io.github.mohammedfaraj46.backend.support.HttpTestSupport.CSRF_HEADER_NAME;
import static io.github.mohammedfaraj46.backend.support.HttpTestSupport.JWT_COOKIE_NAME;
import static io.github.mohammedfaraj46.backend.support.HttpTestSupport.asRequestCookie;
import static io.github.mohammedfaraj46.backend.support.HttpTestSupport.fetchCsrf;
import static io.github.mohammedfaraj46.backend.support.HttpTestSupport.setCookie;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.mohammedfaraj46.backend.repository.AppUserRepository;
import io.github.mohammedfaraj46.backend.repository.CompanyRepository;
import io.github.mohammedfaraj46.backend.repository.CreditNoteRepository;
import io.github.mohammedfaraj46.backend.repository.CustomerRepository;
import io.github.mohammedfaraj46.backend.repository.InvoiceRepository;
import io.github.mohammedfaraj46.backend.repository.NumberSequenceRepository;
import io.github.mohammedfaraj46.backend.support.AbstractPostgresIntegrationTest;
import io.github.mohammedfaraj46.backend.support.HttpTestSupport.Csrf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.ObjectMapper;

/**
 * What the application writes to its log, and what it must never write.
 *
 * <p>The second half is the reason this exists. A log line is easy to add and
 * easy to widen later, and the password is one field away from the ones that are
 * logged on purpose. Asserting its absence means a careless "log the request"
 * fails a test instead of quietly ending up in a file someone else can read.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("Logging")
class LoggingIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String EMAIL = "logging@example.nl";
    private static final String PASSWORD = "zeergeheim123";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private CreditNoteRepository creditNoteRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private NumberSequenceRepository numberSequenceRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void reset() {
        creditNoteRepository.deleteAll();
        invoiceRepository.deleteAll();
        numberSequenceRepository.deleteAll();
        customerRepository.deleteAll();
        companyRepository.deleteAll();
        appUserRepository.deleteAll();
    }

    @AfterEach
    void clearSequences() {
        creditNoteRepository.deleteAll();
        invoiceRepository.deleteAll();
        numberSequenceRepository.deleteAll();
    }

    @Test
    @DisplayName("registering and signing in are logged, the password is not")
    void registrationAndLoginAreLogged(CapturedOutput output) {
        register();
        String session = loginCookie();

        assertThat(session).isNotBlank();
        assertThat(output).contains("Registered user");
        assertThat(output).contains("signed in");
        assertThat(output).doesNotContain(PASSWORD);
    }

    @Test
    @DisplayName("a failed login is a warning that names the account, never the password")
    void failedLoginIsLogged(CapturedOutput output) {
        register();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = restTemplate.postForEntity("/api/auth/login",
                new HttpEntity<>("""
                        {"email": "%s", "password": "helemaal-fout-wachtwoord"}
                        """.formatted(EMAIL), headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(output).contains("Failed login");
        assertThat(output).doesNotContain("helemaal-fout-wachtwoord");
    }

    @Test
    @DisplayName("an unknown email in a failed login does not leak whether it exists")
    void failedLoginForUnknownEmail(CapturedOutput output) {
        register();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = restTemplate.postForEntity("/api/auth/login",
                new HttpEntity<>("""
                        {"email": "niemand@example.nl", "password": "%s"}
                        """.formatted(PASSWORD), headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        // the log may say which half was wrong; the response may not
        assertThat(output).contains("unknown email");
        assertThat(objectMapper.readTree(response.getBody()).get("detail").asString())
                .isEqualTo("Invalid email or password");
        assertThat(output).doesNotContain(PASSWORD);
    }

    @Test
    @DisplayName("sending, paying and crediting are logged with their numbers")
    void moneyEventsAreLogged(CapturedOutput output) {
        register();
        String session = loginCookie();
        long customer = createCustomer(session);

        long invoice = createDraft(session, customer);
        assertThat(send(session, HttpMethod.POST, "/api/invoices/" + invoice + "/send", null)
                .getStatusCode()).isEqualTo(HttpStatus.OK);

        long lineId = firstLineId(session, invoice);
        ResponseEntity<String> credited = send(session, HttpMethod.POST, "/api/credit-notes", """
                {
                  "originalInvoiceId": %d,
                  "lines": [{"invoiceLineId": %d, "netAmount": 40.00}]
                }
                """.formatted(invoice, lineId));
        assertThat(credited.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        assertThat(send(session, HttpMethod.POST, "/api/invoices/" + invoice + "/pay", null)
                .getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(output).contains("Sent invoice");
        assertThat(output).contains("marked paid");
        assertThat(output).contains("Created credit note");
        // the invoice number is what makes a line findable afterwards
        assertThat(output).contains("2026-0001");
    }

    @Test
    @DisplayName("deletions are logged")
    void deletionsAreLogged(CapturedOutput output) {
        register();
        String session = loginCookie();
        long customer = createCustomer(session);
        long draft = createDraft(session, customer);

        assertThat(send(session, HttpMethod.DELETE, "/api/invoices/" + draft, null)
                .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(send(session, HttpMethod.DELETE, "/api/customers/" + customer, null)
                .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(output).contains("Deleted draft invoice");
        assertThat(output).contains("Deleted customer");
    }

    // --- helpers ---------------------------------------------------------

    private long firstLineId(String session, long invoiceId) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, session);
        ResponseEntity<String> response = restTemplate.exchange("/api/invoices/" + invoiceId,
                HttpMethod.GET, new HttpEntity<>(headers), String.class);
        return objectMapper.readTree(response.getBody()).get("lines").get(0).get("id").asLong();
    }

    private long createDraft(String session, long customerId) {
        ResponseEntity<String> response = send(session, HttpMethod.POST, "/api/invoices", """
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

    private long createCustomer(String session) {
        ResponseEntity<String> response = send(session, HttpMethod.POST, "/api/customers", """
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

    private ResponseEntity<String> send(String session, HttpMethod method, String path, String json) {
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
                            "name": "Logbedrijf",
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
