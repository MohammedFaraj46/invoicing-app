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
import java.util.List;
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
 * GET /api/customers/{id}/invoices over real HTTP.
 *
 * <p>The route is nested under the customer, so the ownership question is asked
 * about the customer before any invoice is read: a customer of another company
 * must answer 404 rather than an empty list, which is the reason this is not a
 * filter on /api/invoices.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@DisplayName("Invoices of one customer")
class CustomerInvoicesIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String EMAIL_A = "klantfacturen.a@example.nl";
    private static final String EMAIL_B = "klantfacturen.b@example.nl";
    private static final String PASSWORD = "geheim123";
    private static final String CUSTOMERS = "/api/customers";
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
    private long otherCustomerA;
    private long customerB;

    @BeforeEach
    void resetAndSeedTwoUsers() {
        clearAll();

        register(EMAIL_A);
        register(EMAIL_B);
        sessionA = loginCookie(EMAIL_A);
        sessionB = loginCookie(EMAIL_B);
        customerA = createCustomer(sessionA, "Zon en Zo");
        otherCustomerA = createCustomer(sessionA, "Andere klant van A");
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
    @DisplayName("the customer's invoices come back, newest first, with the figures")
    void invoicesOfOneCustomer() {
        createDraft(sessionA, customerA, "100.00");
        long sent = sendInvoice(sessionA, customerA, "200.00");
        long paid = payInvoice(sessionA, customerA, "500.00");

        JsonNode body = invoicesOf(sessionA, customerA);

        // 242.00 sent + 605.00 paid; the draft carries no number, so it is not
        // invoiced yet and stays out of the total
        assertThat(body.get("totalInvoiced").decimalValue()).isEqualByComparingTo("847.00");
        assertThat(body.get("outstandingAmount").decimalValue()).isEqualByComparingTo("242.00");

        JsonNode invoices = body.get("invoices");
        assertThat(invoices).hasSize(3);
        // newest first: the paid one was created last
        assertThat(ids(invoices)).startsWith(paid).contains(sent);

        JsonNode newest = invoices.get(0);
        assertThat(newest.get("invoiceNumber").asString()).isNotBlank();
        assertThat(newest.get("status").asString()).isEqualTo("PAID");
        assertThat(newest.get("totalAmount").decimalValue()).isEqualByComparingTo("605.00");
        assertThat(newest.get("invoiceDate").isNull()).isFalse();
        assertThat(newest.get("customerName").asString()).isEqualTo("Zon en Zo");

        // the draft has no number and no date; the frontend words that as "concept"
        JsonNode draft = invoices.get(2);
        assertThat(draft.get("status").asString()).isEqualTo("DRAFT");
        assertThat(draft.get("invoiceNumber").isNull()).isTrue();
        assertThat(draft.get("invoiceDate").isNull()).isTrue();
    }

    @Test
    @DisplayName("a customer without invoices gets an empty list and zeros")
    void customerWithoutInvoices() {
        // the other customer of the same company does have one, to prove the
        // emptiness is about this customer and not about the company
        createDraft(sessionA, otherCustomerA, "100.00");

        JsonNode body = invoicesOf(sessionA, customerA);

        assertThat(body.get("invoices")).isEmpty();
        assertThat(body.get("totalInvoiced").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(body.get("outstandingAmount").decimalValue()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("only this customer's invoices come back")
    void invoicesOfOtherCustomersAreNotIncluded() {
        long mine = sendInvoice(sessionA, customerA, "100.00");
        sendInvoice(sessionA, otherCustomerA, "900.00");

        JsonNode body = invoicesOf(sessionA, customerA);

        assertThat(ids(body.get("invoices"))).containsExactly(mine);
        assertThat(body.get("totalInvoiced").decimalValue()).isEqualByComparingTo("121.00");
    }

    @Test
    @DisplayName("A cannot see the invoices of a customer of B")
    void foreignCustomerIsNotFound() {
        sendInvoice(sessionB, customerB, "999.00");

        ResponseEntity<String> response = get(sessionA, CUSTOMERS + "/" + customerB + "/invoices");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        // and B still sees them
        assertThat(invoicesOf(sessionB, customerB).get("invoices")).hasSize(1);
    }

    @Test
    @DisplayName("a foreign customer id is indistinguishable from one that does not exist")
    void unknownCustomerIsAlsoNotFound() {
        ResponseEntity<String> unknown = get(sessionA, CUSTOMERS + "/999999/invoices");
        ResponseEntity<String> foreign = get(sessionA, CUSTOMERS + "/" + customerB + "/invoices");

        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(foreign.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(detailOf(foreign)).isEqualTo(detailOf(unknown).replace("999999", String.valueOf(customerB)));
    }

    @Test
    @DisplayName("a customer with invoices cannot be deleted")
    void customerWithInvoicesCannotBeDeleted() {
        sendInvoice(sessionA, customerA, "100.00");

        ResponseEntity<String> response =
                send(sessionA, HttpMethod.DELETE, CUSTOMERS + "/" + customerA, null);

        // 409, not the foreign key's 500: the customer exists and is yours, it is
        // just still named on an invoice
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(detailOf(response)).contains("still has invoices");
        assertThat(customerRepository.findById(customerA)).isPresent();
        assertThat(invoicesOf(sessionA, customerA).get("invoices")).hasSize(1);
    }

    @Test
    @DisplayName("a customer without invoices can still be deleted")
    void customerWithoutInvoicesCanBeDeleted() {
        sendInvoice(sessionA, otherCustomerA, "100.00");

        ResponseEntity<String> response =
                send(sessionA, HttpMethod.DELETE, CUSTOMERS + "/" + customerA, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(customerRepository.findById(customerA)).isEmpty();
    }

    @Test
    @DisplayName("the route requires authentication")
    void requiresAuthentication() {
        assertThat(restTemplate.getForEntity(CUSTOMERS + "/" + customerA + "/invoices", String.class)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // --- helpers ---------------------------------------------------------

    private JsonNode invoicesOf(String sessionCookie, long customerId) {
        ResponseEntity<String> response = get(sessionCookie, CUSTOMERS + "/" + customerId + "/invoices");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return objectMapper.readTree(response.getBody());
    }

    private String detailOf(ResponseEntity<String> response) {
        return objectMapper.readTree(response.getBody()).get("detail").asString();
    }

    private List<Long> ids(JsonNode rows) {
        return rows.valueStream().map(row -> row.get("id").asLong()).toList();
    }

    private long createDraft(String sessionCookie, long customerId, String unitPrice) {
        ResponseEntity<String> response = send(sessionCookie, HttpMethod.POST, INVOICES, """
                {
                  "customerId": %d,
                  "lines": [
                    {"description": "Werk", "quantity": 1, "unitPrice": %s, "vatRate": 21, "vatType": "STANDARD"}
                  ]
                }
                """.formatted(customerId, unitPrice));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return objectMapper.readTree(response.getBody()).get("id").asLong();
    }

    private long sendInvoice(String sessionCookie, long customerId, String unitPrice) {
        long id = createDraft(sessionCookie, customerId, unitPrice);
        assertThat(send(sessionCookie, HttpMethod.POST, INVOICES + "/" + id + "/send", null)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        return id;
    }

    private long payInvoice(String sessionCookie, long customerId, String unitPrice) {
        long id = sendInvoice(sessionCookie, customerId, unitPrice);
        assertThat(send(sessionCookie, HttpMethod.POST, INVOICES + "/" + id + "/pay", null)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        return id;
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
        ResponseEntity<String> response = send(sessionCookie, HttpMethod.POST, CUSTOMERS, """
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
