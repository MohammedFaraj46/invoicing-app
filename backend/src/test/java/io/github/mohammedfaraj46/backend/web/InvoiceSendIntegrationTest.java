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
import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
 * Sending an invoice: numbering, dates, immutability and the VAT rules.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@DisplayName("Sending an invoice")
class InvoiceSendIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String EMAIL_A = "verzend.a@example.nl";
    private static final String EMAIL_B = "verzend.b@example.nl";
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
        invoiceRepository.deleteAll();
        numberSequenceRepository.deleteAll();
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

    @org.junit.jupiter.api.AfterEach
    void clearSequences() {
        // number_sequence references company; leaving rows behind would break the
        // company cleanup of whichever test class runs next
        invoiceRepository.deleteAll();
        numberSequenceRepository.deleteAll();
    }

    @Nested
    @DisplayName("numbering and dates")
    class NumberingAndDates {

        @Test
        @DisplayName("assigns a number, the dates and the SENT status")
        void sendAssignsNumberAndDates() {
            long id = createAndGetId(sessionA, simpleInvoice(customerA, 14));

            ResponseEntity<String> response = send(sessionA, HttpMethod.POST, INVOICES + "/" + id + "/send", null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            LocalDate today = LocalDate.now();
            JsonNode body = objectMapper.readTree(response.getBody());
            assertThat(body.get("status").asString()).isEqualTo("SENT");
            assertThat(body.get("invoiceNumber").asString()).isEqualTo(Year.now().getValue() + "-0001");
            assertThat(body.get("invoiceDate").asString()).isEqualTo(today.toString());
            assertThat(body.get("dueDate").asString()).isEqualTo(today.plusDays(14).toString());

            Invoice stored = invoiceRepository.findById(id).orElseThrow();
            assertThat(stored.getStatus()).isEqualTo(InvoiceStatus.SENT);
            assertThat(stored.getInvoiceNumber()).isEqualTo(Year.now().getValue() + "-0001");
            assertThat(stored.getInvoiceDate()).isEqualTo(today);
            assertThat(stored.getDueDate()).isEqualTo(today.plusDays(14));
            assertThat(stored.getSentAt()).isNotNull();
        }

        @Test
        @DisplayName("numbers run up within the year")
        void numbersAreSequential() {
            long first = createAndGetId(sessionA, simpleInvoice(customerA, 30));
            long second = createAndGetId(sessionA, simpleInvoice(customerA, 30));

            String year = String.valueOf(Year.now().getValue());
            assertThat(numberOf(sendOk(sessionA, first))).isEqualTo(year + "-0001");
            assertThat(numberOf(sendOk(sessionA, second))).isEqualTo(year + "-0002");
        }

        @Test
        @DisplayName("each company has its own series")
        void seriesIsPerCompany() {
            long ofA = createAndGetId(sessionA, simpleInvoice(customerA, 30));
            long ofB = createAndGetId(sessionB, simpleInvoice(customerB, 30));

            String year = String.valueOf(Year.now().getValue());
            // both start at 0001: the counter is per company, not global
            assertThat(numberOf(sendOk(sessionA, ofA))).isEqualTo(year + "-0001");
            assertThat(numberOf(sendOk(sessionB, ofB))).isEqualTo(year + "-0001");
        }

        @Test
        @DisplayName("a deleted draft burns no number")
        void deletedDraftDoesNotConsumeANumber() {
            long doomed = createAndGetId(sessionA, simpleInvoice(customerA, 30));
            assertThat(send(sessionA, HttpMethod.DELETE, INVOICES + "/" + doomed, null).getStatusCode())
                    .isEqualTo(HttpStatus.NO_CONTENT);

            long kept = createAndGetId(sessionA, simpleInvoice(customerA, 30));

            assertThat(numberOf(sendOk(sessionA, kept)))
                    .isEqualTo(Year.now().getValue() + "-0001");
        }
    }

    @Nested
    @DisplayName("concurrency")
    class Concurrency {

        /**
         * The heart of the numbering: ten sends at once, from ten threads, for one
         * company. If the counter row were read without a lock, two of them would
         * see the same last_value and hand out the same number.
         */
        @Test
        @DisplayName("parallel sends never hand out the same number")
        void parallelSendsGetUniqueNumbers() throws Exception {
            int count = 10;
            List<Long> ids = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                ids.add(createAndGetId(sessionA, simpleInvoice(customerA, 30)));
            }

            ExecutorService pool = Executors.newFixedThreadPool(count);
            // every thread waits on the same latch, so they go off together
            CountDownLatch startSignal = new CountDownLatch(1);

            try {
                List<Future<ResponseEntity<String>>> futures = new ArrayList<>(count);
                for (Long id : ids) {
                    futures.add(pool.submit(() -> {
                        startSignal.await();
                        return send(sessionA, HttpMethod.POST, INVOICES + "/" + id + "/send", null);
                    }));
                }

                startSignal.countDown();

                List<String> numbers = new ArrayList<>(count);
                for (Future<ResponseEntity<String>> future : futures) {
                    ResponseEntity<String> response = future.get(60, TimeUnit.SECONDS);
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    numbers.add(numberOf(response));
                }

                String year = String.valueOf(Year.now().getValue());
                assertThat(numbers).hasSize(count).doesNotHaveDuplicates();
                assertThat(numbers).allSatisfy(number -> assertThat(number).startsWith(year + "-"));
                // and they are exactly 0001..0010, so none was skipped either
                assertThat(numbers).containsExactlyInAnyOrderElementsOf(
                        java.util.stream.IntStream.rangeClosed(1, count)
                                .mapToObj(n -> "%s-%04d".formatted(year, n))
                                .toList());
            } finally {
                pool.shutdownNow();
            }

            // the database agrees: ten invoices, ten distinct numbers, counter at ten
            List<String> stored = invoiceRepository.findAll().stream()
                    .map(Invoice::getInvoiceNumber)
                    .toList();
            assertThat(stored).hasSize(count).doesNotHaveDuplicates();
            assertThat(numberSequenceRepository.findAll()).singleElement()
                    .satisfies(sequence -> assertThat(sequence.getLastValue()).isEqualTo(count));
        }
    }

    @Nested
    @DisplayName("immutability")
    class Immutability {

        @Test
        @DisplayName("a sent invoice cannot be edited, deleted or sent again")
        void sentInvoiceIsFrozen() {
            long id = createAndGetId(sessionA, simpleInvoice(customerA, 30));
            String number = numberOf(sendOk(sessionA, id));

            assertThat(send(sessionA, HttpMethod.PUT, INVOICES + "/" + id, simpleInvoice(customerA, 30))
                    .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(send(sessionA, HttpMethod.DELETE, INVOICES + "/" + id, null).getStatusCode())
                    .isEqualTo(HttpStatus.CONFLICT);
            assertThat(send(sessionA, HttpMethod.POST, INVOICES + "/" + id + "/send", null).getStatusCode())
                    .isEqualTo(HttpStatus.CONFLICT);

            // still readable, and untouched
            Invoice stored = invoiceRepository
                    .findByIdAndCompanyId(id, companyIdOf(EMAIL_A)).orElseThrow();
            assertThat(stored.getStatus()).isEqualTo(InvoiceStatus.SENT);
            assertThat(stored.getInvoiceNumber()).isEqualTo(number);
            assertThat(stored.getLines()).hasSize(1);

            // a refused re-send burnt no number either
            assertThat(numberSequenceRepository.findAll()).singleElement()
                    .satisfies(sequence -> assertThat(sequence.getLastValue()).isEqualTo(1));
        }

        @Test
        @DisplayName("A cannot send an invoice of B")
        void cannotSendForeignInvoice() {
            long idOfB = createAndGetId(sessionB, simpleInvoice(customerB, 30));

            assertThat(send(sessionA, HttpMethod.POST, INVOICES + "/" + idOfB + "/send", null)
                    .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(invoiceRepository.findById(idOfB).orElseThrow().getStatus())
                    .isEqualTo(InvoiceStatus.DRAFT);
        }
    }

    @Nested
    @DisplayName("VAT consistency")
    class VatConsistency {

        @Test
        @DisplayName("REVERSE_CHARGE with a rate above zero is rejected")
        void reverseChargeWithRateIsRejected() {
            ResponseEntity<String> response = create(sessionA, """
                    {
                      "customerId": %d,
                      "lines": [
                        {"description": "Werk", "quantity": 1, "unitPrice": 100.00, "vatRate": 21, "vatType": "REVERSE_CHARGE"}
                      ]
                    }
                    """.formatted(customerA));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(objectMapper.readTree(response.getBody()).get("errors")
                    .get("lines[0].vatRate").asString()).contains("must be 0");
            assertThat(invoiceRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("ZERO with a rate above zero is rejected")
        void zeroTypeWithRateIsRejected() {
            ResponseEntity<String> response = create(sessionA, """
                    {
                      "customerId": %d,
                      "lines": [
                        {"description": "Werk", "quantity": 1, "unitPrice": 100.00, "vatRate": 9, "vatType": "ZERO"}
                      ]
                    }
                    """.formatted(customerA));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(objectMapper.readTree(response.getBody()).get("errors").has("lines[0].vatRate")).isTrue();
        }

        @Test
        @DisplayName("STANDARD at 0% is rejected")
        void standardWithoutRateIsRejected() {
            ResponseEntity<String> response = create(sessionA, """
                    {
                      "customerId": %d,
                      "lines": [
                        {"description": "Werk", "quantity": 1, "unitPrice": 100.00, "vatRate": 0, "vatType": "STANDARD"}
                      ]
                    }
                    """.formatted(customerA));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(objectMapper.readTree(response.getBody()).get("errors")
                    .get("lines[0].vatRate").asString()).contains("greater than 0");
        }

        @Test
        @DisplayName("the offending line is named, in a mixed set")
        void reportsTheOffendingLine() {
            ResponseEntity<String> response = create(sessionA, """
                    {
                      "customerId": %d,
                      "lines": [
                        {"description": "Goed", "quantity": 1, "unitPrice": 100.00, "vatRate": 21, "vatType": "STANDARD"},
                        {"description": "Fout", "quantity": 1, "unitPrice": 100.00, "vatRate": 21, "vatType": "ZERO"}
                      ]
                    }
                    """.formatted(customerA));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

            JsonNode errors = objectMapper.readTree(response.getBody()).get("errors");
            assertThat(errors.has("lines[1].vatRate")).isTrue();
            assertThat(errors.has("lines[0].vatRate")).isFalse();
        }

        @Test
        @DisplayName("a REVERSE_CHARGE line carries no VAT and can be sent")
        void reverseChargeCarriesNoVat() {
            long id = createAndGetId(sessionA, """
                    {
                      "customerId": %d,
                      "lines": [
                        {"description": "Werk voor EU-klant", "quantity": 2, "unitPrice": 250.00, "vatRate": 0, "vatType": "REVERSE_CHARGE"}
                      ]
                    }
                    """.formatted(customerA));

            JsonNode body = objectMapper.readTree(sendOk(sessionA, id).getBody());

            assertThat(body.get("subtotalAmount").decimalValue()).isEqualByComparingTo("500.00");
            assertThat(body.get("vatAmount").decimalValue()).isEqualByComparingTo("0.00");
            assertThat(body.get("totalAmount").decimalValue()).isEqualByComparingTo("500.00");
            assertThat(body.get("lines").get(0).get("lineVatAmount").decimalValue())
                    .isEqualByComparingTo("0.00");
            assertThat(body.get("status").asString()).isEqualTo("SENT");
        }

        @Test
        @DisplayName("a draft that went inconsistent is refused at send time")
        void inconsistentDraftIsRefusedOnSend() {
            long id = createAndGetId(sessionA, simpleInvoice(customerA, 30));

            // bend the stored line behind the service's back, the way a bad
            // migration or a direct database edit could
            Invoice invoice = invoiceRepository
                    .findByIdAndCompanyId(id, companyIdOf(EMAIL_A)).orElseThrow();
            invoice.getLines().getFirst().setVatType(
                    io.github.mohammedfaraj46.backend.domain.VatType.REVERSE_CHARGE);
            invoiceRepository.save(invoice);

            ResponseEntity<String> response =
                    send(sessionA, HttpMethod.POST, INVOICES + "/" + id + "/send", null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(invoiceRepository.findById(id).orElseThrow().getStatus())
                    .isEqualTo(InvoiceStatus.DRAFT);
            // and no number was taken for the failed attempt
            assertThat(numberSequenceRepository.findAll()).isEmpty();
        }
    }

    // --- helpers ---------------------------------------------------------

    private static String simpleInvoice(long customerId, int paymentTermDays) {
        return """
                {
                  "customerId": %d,
                  "paymentTermDays": %d,
                  "lines": [
                    {"description": "Werk", "quantity": 1, "unitPrice": 100.00, "vatRate": 21, "vatType": "STANDARD"}
                  ]
                }
                """.formatted(customerId, paymentTermDays);
    }

    private Long companyIdOf(String email) {
        return companyRepository
                .findByUserId(appUserRepository.findByEmail(email).orElseThrow().getId())
                .orElseThrow()
                .getId();
    }

    private String numberOf(ResponseEntity<String> response) {
        return objectMapper.readTree(response.getBody()).get("invoiceNumber").asString();
    }

    private ResponseEntity<String> sendOk(String sessionCookie, long id) {
        ResponseEntity<String> response =
                send(sessionCookie, HttpMethod.POST, INVOICES + "/" + id + "/send", null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response;
    }

    private ResponseEntity<String> create(String sessionCookie, String json) {
        return send(sessionCookie, HttpMethod.POST, INVOICES, json);
    }

    private long createAndGetId(String sessionCookie, String json) {
        ResponseEntity<String> response = create(sessionCookie, json);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return objectMapper.readTree(response.getBody()).get("id").asLong();
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
