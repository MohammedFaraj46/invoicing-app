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
import io.github.mohammedfaraj46.backend.support.AbstractPostgresIntegrationTest;
import io.github.mohammedfaraj46.backend.support.HttpTestSupport.Csrf;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
 * Draft invoices over real HTTP: the calculator's figures end up in the
 * database, and nothing of user B's is reachable by user A.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@DisplayName("Draft invoices")
class InvoiceIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String EMAIL_A = "factuur.a@example.nl";
    private static final String EMAIL_B = "factuur.b@example.nl";
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

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String sessionA;
    private String sessionB;
    private long customerA;
    private long customerB;

    @BeforeEach
    void resetAndSeedTwoUsers() {
        invoiceRepository.deleteAll();
        customerRepository.deleteAll();
        companyRepository.deleteAll();
        appUserRepository.deleteAll();

        register(EMAIL_A);
        register(EMAIL_B);
        sessionA = loginCookie(EMAIL_A);
        sessionB = loginCookie(EMAIL_B);
        customerA = createCustomer(sessionA, "Klant van A");
        customerB = createCustomer(sessionB, "Klant van B");
    }

    @Nested
    @DisplayName("creating")
    class Creating {

        @Test
        @DisplayName("stores the calculator's amounts and leaves number and date empty")
        void createsDraftWithCalculatedAmounts() {
            // 3 x 19.99 at 21% -> net 59.97, VAT 12.59, total 72.56
            // 1 x 50.00 at 9%  -> net 50.00, VAT  4.50, total 54.50
            ResponseEntity<String> response = create(sessionA, """
                    {
                      "customerId": %d,
                      "notes": "Eerste concept",
                      "lines": [
                        {"description": "Advies", "quantity": 3, "unitPrice": 19.99, "vatRate": 21, "vatType": "STANDARD"},
                        {"description": "Drukwerk", "quantity": 1, "unitPrice": 50.00, "vatRate": 9, "vatType": "REDUCED"}
                      ]
                    }
                    """.formatted(customerA));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

            JsonNode body = objectMapper.readTree(response.getBody());
            assertThat(body.get("status").asString()).isEqualTo("DRAFT");
            assertThat(body.get("invoiceNumber").isNull()).isTrue();
            assertThat(body.get("invoiceDate").isNull()).isTrue();

            assertThat(body.get("subtotalAmount").decimalValue()).isEqualByComparingTo("109.97");
            assertThat(body.get("vatAmount").decimalValue()).isEqualByComparingTo("17.09");
            assertThat(body.get("totalAmount").decimalValue()).isEqualByComparingTo("127.06");

            JsonNode lines = body.get("lines");
            assertThat(lines.size()).isEqualTo(2);
            assertThat(lines.get(0).get("position").asInt()).isEqualTo(1);
            assertThat(lines.get(0).get("lineNetAmount").decimalValue()).isEqualByComparingTo("59.97");
            assertThat(lines.get(0).get("lineVatAmount").decimalValue()).isEqualByComparingTo("12.59");
            assertThat(lines.get(0).get("lineTotal").decimalValue()).isEqualByComparingTo("72.56");

            // and the same figures are actually in the database, not just in the response
            Invoice stored = invoiceRepository.findAll().getFirst();
            assertThat(stored.getStatus()).isEqualTo(InvoiceStatus.DRAFT);
            assertThat(stored.getInvoiceNumber()).isNull();
            assertThat(stored.getInvoiceDate()).isNull();
            assertThat(stored.getTotalAmount()).isEqualByComparingTo(new BigDecimal("127.06"));
            assertThat(stored.getPaymentTermDays()).isEqualTo(30); // the company default
        }

        @Test
        @DisplayName("returns a VAT breakdown grouped per rate")
        void returnsVatBreakdown() {
            long id = createAndGetId(sessionA, """
                    {
                      "customerId": %d,
                      "lines": [
                        {"description": "A", "quantity": 2, "unitPrice": 100.00, "vatRate": 21, "vatType": "STANDARD"},
                        {"description": "B", "quantity": 1, "unitPrice": 50.00, "vatRate": 9, "vatType": "REDUCED"},
                        {"description": "C", "quantity": 3, "unitPrice": 10.00, "vatRate": 21, "vatType": "STANDARD"}
                      ]
                    }
                    """.formatted(customerA));

            JsonNode breakdown = objectMapper.readTree(get(sessionA, INVOICES + "/" + id).getBody())
                    .get("vatBreakdown");

            assertThat(breakdown.size()).isEqualTo(2);
            assertThat(breakdown.get(0).get("vatRate").decimalValue()).isEqualByComparingTo("9.00");
            assertThat(breakdown.get(0).get("vatAmount").decimalValue()).isEqualByComparingTo("4.50");
            assertThat(breakdown.get(1).get("vatRate").decimalValue()).isEqualByComparingTo("21.00");
            assertThat(breakdown.get(1).get("netAmount").decimalValue()).isEqualByComparingTo("230.00");
            assertThat(breakdown.get(1).get("vatAmount").decimalValue()).isEqualByComparingTo("48.30");
        }

        @Test
        @DisplayName("refuses a customer belonging to another company")
        void refusesForeignCustomer() {
            ResponseEntity<String> response = create(sessionA, """
                    {
                      "customerId": %d,
                      "lines": [
                        {"description": "A", "quantity": 1, "unitPrice": 10.00, "vatRate": 21, "vatType": "STANDARD"}
                      ]
                    }
                    """.formatted(customerB));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(invoiceRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("honours an explicit payment term")
        void honoursExplicitPaymentTerm() {
            createAndGetId(sessionA, """
                    {
                      "customerId": %d,
                      "paymentTermDays": 14,
                      "lines": [
                        {"description": "A", "quantity": 1, "unitPrice": 10.00, "vatRate": 21, "vatType": "STANDARD"}
                      ]
                    }
                    """.formatted(customerA));

            assertThat(invoiceRepository.findAll().getFirst().getPaymentTermDays()).isEqualTo(14);
        }
    }

    @Nested
    @DisplayName("reading and changing")
    class ReadingAndChanging {

        @Test
        @DisplayName("the list shows only this company's invoices")
        void listIsScopedToOwnCompany() {
            createAndGetId(sessionA, simpleInvoice(customerA));
            createAndGetId(sessionB, simpleInvoice(customerB));

            JsonNode list = objectMapper.readTree(get(sessionA, INVOICES).getBody());

            assertThat(list.size()).isEqualTo(1);
            assertThat(list.get(0).get("customerName").asString()).isEqualTo("Klant van A");
        }

        @Test
        @DisplayName("updating recalculates the amounts and replaces the lines")
        void updateRecalculates() {
            long id = createAndGetId(sessionA, simpleInvoice(customerA));

            ResponseEntity<String> response = send(sessionA, HttpMethod.PUT, INVOICES + "/" + id, """
                    {
                      "customerId": %d,
                      "lines": [
                        {"description": "Nieuw werk", "quantity": 3, "unitPrice": 19.99, "vatRate": 21, "vatType": "STANDARD"}
                      ]
                    }
                    """.formatted(customerA));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            JsonNode body = objectMapper.readTree(response.getBody());
            assertThat(body.get("lines").size()).isEqualTo(1);
            assertThat(body.get("lines").get(0).get("description").asString()).isEqualTo("Nieuw werk");
            assertThat(body.get("subtotalAmount").decimalValue()).isEqualByComparingTo("59.97");
            assertThat(body.get("vatAmount").decimalValue()).isEqualByComparingTo("12.59");
            assertThat(body.get("totalAmount").decimalValue()).isEqualByComparingTo("72.56");

            // the old line is really gone, not just missing from the response
            Invoice stored = invoiceRepository.findByIdAndCompanyId(id, companyIdOf(EMAIL_A)).orElseThrow();
            assertThat(stored.getLines()).hasSize(1);
            assertThat(stored.getTotalAmount()).isEqualByComparingTo(new BigDecimal("72.56"));
        }

        @Test
        @DisplayName("a draft can be deleted")
        void deletesDraft() {
            long id = createAndGetId(sessionA, simpleInvoice(customerA));

            assertThat(send(sessionA, HttpMethod.DELETE, INVOICES + "/" + id, null).getStatusCode())
                    .isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(invoiceRepository.findAll()).isEmpty();
        }
    }

    @Nested
    @DisplayName("ownership")
    class Ownership {

        @Test
        @DisplayName("A cannot read, change or delete an invoice of B")
        void foreignInvoiceIsNotFound() {
            long idOfB = createAndGetId(sessionB, simpleInvoice(customerB));

            assertThat(get(sessionA, INVOICES + "/" + idOfB).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(send(sessionA, HttpMethod.PUT, INVOICES + "/" + idOfB, simpleInvoice(customerA))
                    .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(send(sessionA, HttpMethod.DELETE, INVOICES + "/" + idOfB, null).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND);

            // B's invoice is untouched and still readable by B
            assertThat(get(sessionB, INVOICES + "/" + idOfB).getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(invoiceRepository.findAll()).hasSize(1);
        }

        @Test
        @DisplayName("a foreign id is indistinguishable from an unknown one")
        void foreignIdLooksLikeUnknownId() {
            long idOfB = createAndGetId(sessionB, simpleInvoice(customerB));

            ResponseEntity<String> foreign = get(sessionA, INVOICES + "/" + idOfB);
            ResponseEntity<String> unknown = get(sessionA, INVOICES + "/999999");

            assertThat(foreign.getStatusCode()).isEqualTo(unknown.getStatusCode());
            assertThat(objectMapper.readTree(foreign.getBody()).get("title").asString())
                    .isEqualTo(objectMapper.readTree(unknown.getBody()).get("title").asString());
        }

        @Test
        @DisplayName("every route returns 401 without authentication")
        void requiresAuthentication() {
            long id = createAndGetId(sessionA, simpleInvoice(customerA));

            assertThat(restTemplate.getForEntity(INVOICES, String.class).getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(restTemplate.getForEntity(INVOICES + "/" + id, String.class).getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(create(null, simpleInvoice(customerA)).getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(send(null, HttpMethod.DELETE, INVOICES + "/" + id, null).getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);

            assertThat(invoiceRepository.findAll()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("status guard")
    class StatusGuard {

        @Test
        @DisplayName("a sent invoice can no longer be changed or deleted")
        void sentInvoiceIsLocked() {
            long id = createAndGetId(sessionA, simpleInvoice(customerA));

            // sending is the next step; flip the status directly to prove the guard
            Invoice invoice = invoiceRepository.findById(id).orElseThrow();
            invoice.setStatus(InvoiceStatus.SENT);
            invoice.setInvoiceNumber("2026-0001");
            invoice.setInvoiceDate(java.time.LocalDate.of(2026, 1, 5));
            invoiceRepository.save(invoice);

            assertThat(send(sessionA, HttpMethod.PUT, INVOICES + "/" + id, simpleInvoice(customerA))
                    .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(send(sessionA, HttpMethod.DELETE, INVOICES + "/" + id, null).getStatusCode())
                    .isEqualTo(HttpStatus.CONFLICT);

            // reading it is still fine
            assertThat(get(sessionA, INVOICES + "/" + id).getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(invoiceRepository.findAll()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("validation")
    class Validation {

        @Test
        @DisplayName("an invoice without lines is rejected with 400")
        void noLinesIsRejected() {
            ResponseEntity<String> response = create(sessionA, """
                    {"customerId": %d, "lines": []}
                    """.formatted(customerA));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(objectMapper.readTree(response.getBody()).get("errors").has("lines")).isTrue();
            assertThat(invoiceRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("a missing customerId is rejected with 400")
        void missingCustomerIsRejected() {
            ResponseEntity<String> response = create(sessionA, """
                    {
                      "lines": [
                        {"description": "A", "quantity": 1, "unitPrice": 10.00, "vatRate": 21, "vatType": "STANDARD"}
                      ]
                    }
                    """);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(objectMapper.readTree(response.getBody()).get("errors").has("customerId")).isTrue();
        }

        @Test
        @DisplayName("invalid line fields are reported per field")
        void invalidLineFieldsAreReported() {
            ResponseEntity<String> response = create(sessionA, """
                    {
                      "customerId": %d,
                      "lines": [
                        {"description": "", "quantity": 0, "unitPrice": -1, "vatRate": 21, "vatType": "STANDARD"}
                      ]
                    }
                    """.formatted(customerA));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

            JsonNode errors = objectMapper.readTree(response.getBody()).get("errors");
            assertThat(errors.has("lines[0].description")).isTrue();
            assertThat(errors.has("lines[0].quantity")).isTrue();
            assertThat(errors.has("lines[0].unitPrice")).isTrue();
        }
    }

    // --- helpers ---------------------------------------------------------

    private static String simpleInvoice(long customerId) {
        return """
                {
                  "customerId": %d,
                  "lines": [
                    {"description": "Werk", "quantity": 1, "unitPrice": 100.00, "vatRate": 21, "vatType": "STANDARD"}
                  ]
                }
                """.formatted(customerId);
    }

    private ResponseEntity<String> create(String sessionCookie, String json) {
        return send(sessionCookie, HttpMethod.POST, INVOICES, json);
    }

    private long createAndGetId(String sessionCookie, String json) {
        ResponseEntity<String> response = create(sessionCookie, json);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return objectMapper.readTree(response.getBody()).get("id").asLong();
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

    private Long companyIdOf(String email) {
        return companyRepository
                .findByUserId(appUserRepository.findByEmail(email).orElseThrow().getId())
                .orElseThrow()
                .getId();
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
