package io.github.mohammedfaraj46.backend.web;

import static io.github.mohammedfaraj46.backend.support.HttpTestSupport.CSRF_HEADER_NAME;
import static io.github.mohammedfaraj46.backend.support.HttpTestSupport.JWT_COOKIE_NAME;
import static io.github.mohammedfaraj46.backend.support.HttpTestSupport.asRequestCookie;
import static io.github.mohammedfaraj46.backend.support.HttpTestSupport.fetchCsrf;
import static io.github.mohammedfaraj46.backend.support.HttpTestSupport.setCookie;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.mohammedfaraj46.backend.domain.Invoice;
import io.github.mohammedfaraj46.backend.domain.InvoiceStatus;
import io.github.mohammedfaraj46.backend.repository.AppUserRepository;
import io.github.mohammedfaraj46.backend.repository.CompanyRepository;
import io.github.mohammedfaraj46.backend.repository.CustomerRepository;
import io.github.mohammedfaraj46.backend.repository.InvoiceRepository;
import io.github.mohammedfaraj46.backend.repository.NumberSequenceRepository;
import io.github.mohammedfaraj46.backend.support.AbstractPostgresIntegrationTest;
import io.github.mohammedfaraj46.backend.support.HttpTestSupport.Csrf;
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
 * The status workflow over real HTTP: what may move where, and what a paid
 * invoice still allows.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@DisplayName("Invoice status workflow")
class InvoiceStatusIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String EMAIL_A = "status.a@example.nl";
    private static final String EMAIL_B = "status.b@example.nl";
    private static final String PASSWORD = "geheim123";
    private static final String INVOICES = "/api/invoices";

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

    private String sessionA;
    private String sessionB;
    private long customerA;
    private long customerB;

    @BeforeEach
    void resetAndSeedTwoUsers() {
        clearAll();

        register(EMAIL_A);
        register(EMAIL_B);
        sessionA = loginCookie(EMAIL_A);
        sessionB = loginCookie(EMAIL_B);
        customerA = createCustomer(sessionA, "Klant van A");
        customerB = createCustomer(sessionB, "Klant van B");
    }

    @AfterEach
    void clearSequences() {
        // number_sequence references company; leaving rows behind would break the
        // company cleanup of whichever test class runs next
        invoiceRepository.deleteAll();
        numberSequenceRepository.deleteAll();
    }

    @Test
    @DisplayName("SENT becomes PAID and records when")
    void sentBecomesPaid() {
        long id = createDraft(sessionA);
        sendOk(sessionA, id);

        ResponseEntity<String> response = pay(sessionA, id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(response.getBody()).get("status").asString()).isEqualTo("PAID");

        Invoice stored = invoiceRepository.findById(id).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(stored.getPaidAt()).isNotNull();
        // the number and the amounts are untouched by being paid
        assertThat(stored.getInvoiceNumber()).isNotNull();
        assertThat(stored.getSentAt()).isNotNull();
    }

    @Test
    @DisplayName("a draft cannot be paid")
    void draftCannotBePaid() {
        long id = createDraft(sessionA);

        ResponseEntity<String> response = pay(sessionA, id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(objectMapper.readTree(response.getBody()).get("detail").asString())
                .contains("cannot go from DRAFT to PAID");

        Invoice stored = invoiceRepository.findById(id).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(InvoiceStatus.DRAFT);
        assertThat(stored.getPaidAt()).isNull();
    }

    @Test
    @DisplayName("paying twice is refused and keeps the first moment")
    void payingTwiceIsRefused() {
        long id = createDraft(sessionA);
        sendOk(sessionA, id);
        assertThat(pay(sessionA, id).getStatusCode()).isEqualTo(HttpStatus.OK);

        var paidAtAfterFirst = invoiceRepository.findById(id).orElseThrow().getPaidAt();

        ResponseEntity<String> second = pay(sessionA, id);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(objectMapper.readTree(second.getBody()).get("detail").asString())
                .contains("already has status PAID");
        // the original timestamp survived, rather than being quietly overwritten
        assertThat(invoiceRepository.findById(id).orElseThrow().getPaidAt())
                .isEqualTo(paidAtAfterFirst);
    }

    @Test
    @DisplayName("a paid invoice cannot be sent again, edited or deleted")
    void paidInvoiceIsFrozen() {
        long id = createDraft(sessionA);
        sendOk(sessionA, id);
        assertThat(pay(sessionA, id).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(send(sessionA, HttpMethod.POST, INVOICES + "/" + id + "/send", null).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(send(sessionA, HttpMethod.PUT, INVOICES + "/" + id, draftJson(customerA)).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(send(sessionA, HttpMethod.DELETE, INVOICES + "/" + id, null).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        // reading it still works, and nothing changed
        assertThat(get(sessionA, INVOICES + "/" + id).getStatusCode()).isEqualTo(HttpStatus.OK);
        Invoice stored = invoiceRepository.findById(id).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(invoiceRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("A cannot pay an invoice of B")
    void cannotPayForeignInvoice() {
        long idOfB = createDraft(sessionB, customerB);
        sendOk(sessionB, idOfB);

        ResponseEntity<String> response = pay(sessionA, idOfB);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        Invoice stored = invoiceRepository.findById(idOfB).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(InvoiceStatus.SENT);
        assertThat(stored.getPaidAt()).isNull();
    }

    @Test
    @DisplayName("paying an unknown invoice is a 404, not a 409")
    void payingUnknownInvoiceIsNotFound() {
        assertThat(pay(sessionA, 999_999L).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("paying requires authentication")
    void payingRequiresAuthentication() {
        long id = createDraft(sessionA);
        sendOk(sessionA, id);

        assertThat(pay(null, id).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(invoiceRepository.findById(id).orElseThrow().getStatus())
                .isEqualTo(InvoiceStatus.SENT);
    }

    @Test
    @DisplayName("the full happy path: draft, sent, paid")
    void fullLifecycle() {
        long id = createDraft(sessionA);

        assertThat(statusOf(get(sessionA, INVOICES + "/" + id))).isEqualTo("DRAFT");
        assertThat(statusOf(sendOk(sessionA, id))).isEqualTo("SENT");
        assertThat(statusOf(pay(sessionA, id))).isEqualTo("PAID");
        assertThat(statusOf(get(sessionA, INVOICES + "/" + id))).isEqualTo("PAID");
    }

    // --- helpers ---------------------------------------------------------

    private static String draftJson(long customerId) {
        return """
                {
                  "customerId": %d,
                  "lines": [
                    {"description": "Werk", "quantity": 1, "unitPrice": 100.00, "vatRate": 21, "vatType": "STANDARD"}
                  ]
                }
                """.formatted(customerId);
    }

    private String statusOf(ResponseEntity<String> response) {
        return objectMapper.readTree(response.getBody()).get("status").asString();
    }

    private long createDraft(String sessionCookie) {
        return createDraft(sessionCookie, customerA);
    }

    private long createDraft(String sessionCookie, long customerId) {
        ResponseEntity<String> response =
                send(sessionCookie, HttpMethod.POST, INVOICES, draftJson(customerId));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return objectMapper.readTree(response.getBody()).get("id").asLong();
    }

    private ResponseEntity<String> sendOk(String sessionCookie, long id) {
        ResponseEntity<String> response =
                send(sessionCookie, HttpMethod.POST, INVOICES + "/" + id + "/send", null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response;
    }

    private ResponseEntity<String> pay(String sessionCookie, long id) {
        return send(sessionCookie, HttpMethod.POST, INVOICES + "/" + id + "/pay", null);
    }

    private ResponseEntity<String> get(String sessionCookie, String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, sessionCookie);
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    /** State-changing call: login cookie plus a CSRF token, like the SPA sends. */
    private ResponseEntity<String> send(String sessionCookie, HttpMethod method, String path, String json) {
        Csrf csrf = fetchCsrf(restTemplate);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(HttpHeaders.COOKIE,
                (sessionCookie == null ? "" : sessionCookie + "; ") + csrf.cookieHeader());
        headers.add(CSRF_HEADER_NAME, csrf.token());

        return restTemplate.exchange(path, method, new HttpEntity<>(json, headers), String.class);
    }

    private void clearAll() {
        invoiceRepository.deleteAll();
        numberSequenceRepository.deleteAll();
        customerRepository.deleteAll();
        companyRepository.deleteAll();
        appUserRepository.deleteAll();
    }

    private long createCustomer(String sessionCookie, String name) {
        ResponseEntity<String> response = send(sessionCookie, HttpMethod.POST, "/api/customers", """
                {
                  "name": "%s",
                  "email": "klant@example.nl",
                  "addressLine": "Straat 1",
                  "postalCode": "1011AB",
                  "city": "Amsterdam"
                }
                """.formatted(name));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return objectMapper.readTree(response.getBody()).get("id").asLong();
    }

    private void register(String email) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = restTemplate.postForEntity("/api/auth/register",
                new HttpEntity<>("""
                        {
                          "email": "%s",
                          "password": "%s",
                          "company": {
                            "name": "Bedrijf van %s",
                            "kvkNumber": "12345678",
                            "vatNumber": "NL001234567B01",
                            "addressLine": "Straat 1",
                            "postalCode": "1011AB",
                            "city": "Amsterdam"
                          }
                        }
                        """.formatted(email, PASSWORD, email), headers), String.class);
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
