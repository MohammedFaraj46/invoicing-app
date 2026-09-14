package io.github.mohammedfaraj46.backend.web;

import static io.github.mohammedfaraj46.backend.support.HttpTestSupport.CSRF_HEADER_NAME;
import static io.github.mohammedfaraj46.backend.support.HttpTestSupport.JWT_COOKIE_NAME;
import static io.github.mohammedfaraj46.backend.support.HttpTestSupport.asRequestCookie;
import static io.github.mohammedfaraj46.backend.support.HttpTestSupport.fetchCsrf;
import static io.github.mohammedfaraj46.backend.support.HttpTestSupport.setCookie;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.mohammedfaraj46.backend.domain.Invoice;
import io.github.mohammedfaraj46.backend.repository.AppUserRepository;
import io.github.mohammedfaraj46.backend.repository.CompanyRepository;
import io.github.mohammedfaraj46.backend.repository.CustomerRepository;
import io.github.mohammedfaraj46.backend.repository.InvoiceRepository;
import io.github.mohammedfaraj46.backend.repository.NumberSequenceRepository;
import io.github.mohammedfaraj46.backend.support.AbstractPostgresIntegrationTest;
import io.github.mohammedfaraj46.backend.support.HttpTestSupport.Csrf;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
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
 * The dashboard figures over real HTTP, against a known set of invoices.
 *
 * <p>The interesting cases are the ones a single happy-path assertion would
 * miss: a company with nothing yet, money that was paid but not this month, an
 * invoice that is late rather than merely sent, and a second company whose
 * numbers must stay out of the first one.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@DisplayName("Dashboard overview")
class DashboardIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String EMAIL_A = "dashboard.a@example.nl";
    private static final String EMAIL_B = "dashboard.b@example.nl";
    private static final String PASSWORD = "geheim123";
    private static final String DASHBOARD = "/api/dashboard";
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

        register(EMAIL_A, "Bedrijf A");
        register(EMAIL_B, "Bedrijf B");
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
    @DisplayName("a company without invoices gets zeros and an empty list")
    void emptyState() {
        JsonNode body = dashboard(sessionA);

        assertThat(body.get("outstandingAmount").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(body.get("paidThisMonth").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(body.get("overdueCount").asLong()).isZero();
        assertThat(body.get("draftCount").asLong()).isZero();
        assertThat(body.get("recentInvoices")).isEmpty();
    }

    @Test
    @DisplayName("the figures match a known set of invoices")
    void figuresMatchTheSeededInvoices() {
        // two drafts: they count, but they are not money yet
        createDraft(sessionA, "100.00");
        createDraft(sessionA, "100.00");

        // sent and still within its term
        sendInvoice(sessionA, "200.00");
        // sent and past its due date
        long late = sendInvoice(sessionA, "100.00");
        backdateDueDate(late, LocalDate.now().minusDays(1));

        // paid this month
        payInvoice(sessionA, "500.00");
        // paid, but in the month before this one
        long earlier = payInvoice(sessionA, "1000.00");
        backdatePaidAt(earlier, duringPreviousMonth());

        JsonNode body = dashboard(sessionA);

        // 242.00 + 121.00; what has been paid is no longer outstanding
        assertThat(body.get("outstandingAmount").decimalValue()).isEqualByComparingTo("363.00");
        // 605.00 only: last month's 1210.00 is not this month's income
        assertThat(body.get("paidThisMonth").decimalValue()).isEqualByComparingTo("605.00");
        assertThat(body.get("overdueCount").asLong()).isEqualTo(1);
        assertThat(body.get("draftCount").asLong()).isEqualTo(2);
    }

    @Test
    @DisplayName("recent invoices: at most five, newest first, a draft has no number")
    void recentInvoicesAreTheFiveNewest() {
        long oldest = createDraft(sessionA, "10.00");
        createDraft(sessionA, "20.00");
        createDraft(sessionA, "30.00");
        createDraft(sessionA, "40.00");
        createDraft(sessionA, "50.00");
        long newest = sendInvoice(sessionA, "60.00");

        JsonNode recent = dashboard(sessionA).get("recentInvoices");

        assertThat(recent).hasSize(5);
        assertThat(recent.get(0).get("id").asLong()).isEqualTo(newest);
        assertThat(ids(recent)).doesNotContain(oldest);

        JsonNode first = recent.get(0);
        assertThat(first.get("invoiceNumber").asString()).isNotBlank();
        assertThat(first.get("status").asString()).isEqualTo("SENT");
        assertThat(first.get("customerName").asString()).isEqualTo("Klant van A");
        assertThat(first.get("totalAmount").decimalValue()).isEqualByComparingTo("72.60");
        assertThat(first.get("date").asString()).isEqualTo(LocalDate.now().toString());

        // a draft has no number yet, and falls back to the day it was last touched
        JsonNode draft = recent.get(1);
        assertThat(draft.get("invoiceNumber").isNull()).isTrue();
        assertThat(draft.get("status").asString()).isEqualTo("DRAFT");
        assertThat(draft.get("date").asString()).isEqualTo(LocalDate.now().toString());
    }

    @Test
    @DisplayName("the figures of A contain nothing of B")
    void figuresAreScopedToTheOwnCompany() {
        createDraft(sessionA, "100.00");
        sendInvoice(sessionA, "100.00");

        // B has more of everything; none of it may show up for A
        createDraft(sessionB, customerB, "999.00");
        createDraft(sessionB, customerB, "999.00");
        long lateOfB = sendInvoice(sessionB, customerB, "999.00");
        backdateDueDate(lateOfB, LocalDate.now().minusDays(5));
        payInvoice(sessionB, customerB, "999.00");

        JsonNode forA = dashboard(sessionA);

        assertThat(forA.get("outstandingAmount").decimalValue()).isEqualByComparingTo("121.00");
        assertThat(forA.get("paidThisMonth").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(forA.get("overdueCount").asLong()).isZero();
        assertThat(forA.get("draftCount").asLong()).isEqualTo(1);
        assertThat(forA.get("recentInvoices")).hasSize(2);
        assertThat(forA.get("recentInvoices")).allSatisfy(
                row -> assertThat(row.get("customerName").asString()).isEqualTo("Klant van A"));

        // and B still sees its own, undisturbed
        JsonNode forB = dashboard(sessionB);
        assertThat(forB.get("draftCount").asLong()).isEqualTo(2);
        assertThat(forB.get("overdueCount").asLong()).isEqualTo(1);
        assertThat(forB.get("paidThisMonth").decimalValue()).isEqualByComparingTo("1208.79");
    }

    @Test
    @DisplayName("a payment at the very start of the month still counts as this month")
    void monthBoundaryIsInclusiveAtTheStart() {
        long paid = payInvoice(sessionA, "100.00");
        backdatePaidAt(paid, firstInstantOfThisMonth());

        assertThat(dashboard(sessionA).get("paidThisMonth").decimalValue())
                .isEqualByComparingTo("121.00");

        // one millisecond earlier and it belongs to the month before
        backdatePaidAt(paid, firstInstantOfThisMonth().minusNanos(1_000_000));

        assertThat(dashboard(sessionA).get("paidThisMonth").decimalValue())
                .isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("the dashboard requires authentication")
    void requiresAuthentication() {
        assertThat(restTemplate.getForEntity(DASHBOARD, String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // --- helpers ---------------------------------------------------------

    private JsonNode dashboard(String sessionCookie) {
        ResponseEntity<String> response = get(sessionCookie, DASHBOARD);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return objectMapper.readTree(response.getBody());
    }

    private List<Long> ids(JsonNode rows) {
        return rows.valueStream().map(row -> row.get("id").asLong()).toList();
    }

    private OffsetDateTime firstInstantOfThisMonth() {
        return LocalDate.now().withDayOfMonth(1)
                .atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime();
    }

    /**
     * Noon on the last day before this month: unambiguously the month before,
     * whatever today happens to be.
     */
    private OffsetDateTime duringPreviousMonth() {
        return LocalDate.now().withDayOfMonth(1).minusDays(1)
                .atTime(12, 0).atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }

    /** Rewrites a stored date the API has no way to set, to stage the past. */
    private void backdateDueDate(long invoiceId, LocalDate dueDate) {
        Invoice invoice = invoiceRepository.findById(invoiceId).orElseThrow();
        invoice.setDueDate(dueDate);
        invoiceRepository.saveAndFlush(invoice);
    }

    private void backdatePaidAt(long invoiceId, OffsetDateTime paidAt) {
        Invoice invoice = invoiceRepository.findById(invoiceId).orElseThrow();
        invoice.setPaidAt(paidAt);
        invoiceRepository.saveAndFlush(invoice);
    }

    private long createDraft(String sessionCookie, String unitPrice) {
        return createDraft(sessionCookie, customerA, unitPrice);
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

    private long sendInvoice(String sessionCookie, String unitPrice) {
        return sendInvoice(sessionCookie, customerA, unitPrice);
    }

    private long sendInvoice(String sessionCookie, long customerId, String unitPrice) {
        long id = createDraft(sessionCookie, customerId, unitPrice);
        assertThat(send(sessionCookie, HttpMethod.POST, INVOICES + "/" + id + "/send", null)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        return id;
    }

    private long payInvoice(String sessionCookie, String unitPrice) {
        return payInvoice(sessionCookie, customerA, unitPrice);
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
