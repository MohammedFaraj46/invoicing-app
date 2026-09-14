package io.github.mohammedfaraj46.backend.web;

import static io.github.mohammedfaraj46.backend.support.HttpTestSupport.CSRF_HEADER_NAME;
import static io.github.mohammedfaraj46.backend.support.HttpTestSupport.JWT_COOKIE_NAME;
import static io.github.mohammedfaraj46.backend.support.HttpTestSupport.asRequestCookie;
import static io.github.mohammedfaraj46.backend.support.HttpTestSupport.fetchCsrf;
import static io.github.mohammedfaraj46.backend.support.HttpTestSupport.setCookie;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.mohammedfaraj46.backend.domain.CreditNote;
import io.github.mohammedfaraj46.backend.domain.CreditNoteStatus;
import io.github.mohammedfaraj46.backend.repository.AppUserRepository;
import io.github.mohammedfaraj46.backend.repository.CompanyRepository;
import io.github.mohammedfaraj46.backend.repository.CreditNoteRepository;
import io.github.mohammedfaraj46.backend.repository.CustomerRepository;
import io.github.mohammedfaraj46.backend.repository.InvoiceRepository;
import io.github.mohammedfaraj46.backend.repository.NumberSequenceRepository;
import io.github.mohammedfaraj46.backend.support.AbstractPostgresIntegrationTest;
import io.github.mohammedfaraj46.backend.support.HttpTestSupport.Csrf;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
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
 * Credit notes over real HTTP: deriving them from an invoice, the over-credit
 * limit, numbering from their own series, and the PDF.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@DisplayName("Credit notes")
class CreditNoteIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String EMAIL_A = "credit.a@example.nl";
    private static final String EMAIL_B = "credit.b@example.nl";
    private static final String PASSWORD = "geheim123";
    private static final String CREDIT_NOTES = "/api/credit-notes";
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
    private CreditNoteRepository creditNoteRepository;

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
        customerA = createCustomer(sessionA, "Bakkerij De Korenbloem");
        customerB = createCustomer(sessionB, "Klant van B");
    }

    @AfterEach
    void cleanUp() {
        clearAll();
    }

    @Nested
    @DisplayName("deriving from an invoice")
    class Deriving {

        @Test
        @DisplayName("credits one line, negative, with the invoice's own VAT rate")
        void creditsOneLine() {
            SentInvoice invoice = sentInvoice(sessionA);

            // credit 40.00 net of the 21% line
            ResponseEntity<String> response = create(sessionA, """
                    {
                      "originalInvoiceId": %d,
                      "reason": "Deel van de opdracht vervallen",
                      "lines": [{"invoiceLineId": %d, "netAmount": 40.00}]
                    }
                    """.formatted(invoice.id(), invoice.standardLineId()));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

            JsonNode body = objectMapper.readTree(response.getBody());
            assertThat(body.get("status").asString()).isEqualTo("DRAFT");
            assertThat(body.get("creditNoteNumber").isNull()).isTrue();
            assertThat(body.get("originalInvoiceNumber").asString()).isEqualTo(invoice.number());

            // 40.00 at 21% -> 8.40 VAT, all stored negative
            assertThat(body.get("subtotalAmount").decimalValue()).isEqualByComparingTo("-40.00");
            assertThat(body.get("vatAmount").decimalValue()).isEqualByComparingTo("-8.40");
            assertThat(body.get("totalAmount").decimalValue()).isEqualByComparingTo("-48.40");

            JsonNode line = body.get("lines").get(0);
            // quantity and unit price stay positive, so the line reads as "1 x 40,00"
            assertThat(line.get("quantity").decimalValue()).isEqualByComparingTo("1");
            assertThat(line.get("unitPrice").decimalValue()).isEqualByComparingTo("40.00");
            assertThat(line.get("lineNetAmount").decimalValue()).isEqualByComparingTo("-40.00");
            assertThat(line.get("lineVatAmount").decimalValue()).isEqualByComparingTo("-8.40");
            // the rate was inherited from the invoice line, not chosen
            assertThat(line.get("vatRate").decimalValue()).isEqualByComparingTo("21.00");
            assertThat(line.get("vatType").asString()).isEqualTo("STANDARD");
            assertThat(line.get("invoiceLineId").asLong()).isEqualTo(invoice.standardLineId());
        }

        @Test
        @DisplayName("inherits a reduced rate and a reverse charge line untouched")
        void inheritsRatesPerLine() {
            SentInvoice invoice = sentInvoice(sessionA);

            JsonNode body = objectMapper.readTree(create(sessionA, """
                    {
                      "originalInvoiceId": %d,
                      "lines": [
                        {"invoiceLineId": %d, "netAmount": 50.00},
                        {"invoiceLineId": %d, "netAmount": 100.00}
                      ]
                    }
                    """.formatted(invoice.id(), invoice.reducedLineId(), invoice.reverseChargeLineId()))
                    .getBody());

            JsonNode reduced = body.get("lines").get(0);
            assertThat(reduced.get("vatRate").decimalValue()).isEqualByComparingTo("9.00");
            assertThat(reduced.get("lineVatAmount").decimalValue()).isEqualByComparingTo("-4.50");

            JsonNode reverseCharge = body.get("lines").get(1);
            assertThat(reverseCharge.get("vatType").asString()).isEqualTo("REVERSE_CHARGE");
            assertThat(reverseCharge.get("lineVatAmount").decimalValue()).isEqualByComparingTo("0.00");

            assertThat(body.get("subtotalAmount").decimalValue()).isEqualByComparingTo("-150.00");
            assertThat(body.get("vatAmount").decimalValue()).isEqualByComparingTo("-4.50");
            assertThat(body.get("totalAmount").decimalValue()).isEqualByComparingTo("-154.50");
        }

        @Test
        @DisplayName("crediting the whole invoice mirrors it exactly")
        void fullCredit() {
            SentInvoice invoice = sentInvoice(sessionA);

            JsonNode body = objectMapper.readTree(create(sessionA, """
                    {
                      "originalInvoiceId": %d,
                      "lines": [
                        {"invoiceLineId": %d, "netAmount": 100.00},
                        {"invoiceLineId": %d, "netAmount": 50.00},
                        {"invoiceLineId": %d, "netAmount": 200.00}
                      ]
                    }
                    """.formatted(invoice.id(), invoice.standardLineId(),
                    invoice.reducedLineId(), invoice.reverseChargeLineId())).getBody());

            // the invoice was 350.00 net, 25.50 VAT, 375.50 total
            assertThat(body.get("subtotalAmount").decimalValue()).isEqualByComparingTo("-350.00");
            assertThat(body.get("vatAmount").decimalValue()).isEqualByComparingTo("-25.50");
            assertThat(body.get("totalAmount").decimalValue()).isEqualByComparingTo("-375.50");
        }

        @Test
        @DisplayName("lines with 0 are left out")
        void zeroLinesAreSkipped() {
            SentInvoice invoice = sentInvoice(sessionA);

            JsonNode body = objectMapper.readTree(create(sessionA, """
                    {
                      "originalInvoiceId": %d,
                      "lines": [
                        {"invoiceLineId": %d, "netAmount": 0.00},
                        {"invoiceLineId": %d, "netAmount": 25.00}
                      ]
                    }
                    """.formatted(invoice.id(), invoice.standardLineId(), invoice.reducedLineId()))
                    .getBody());

            assertThat(body.get("lines").size()).isEqualTo(1);
            assertThat(body.get("lines").get(0).get("invoiceLineId").asLong())
                    .isEqualTo(invoice.reducedLineId());
        }

        @Test
        @DisplayName("a draft invoice cannot be credited")
        void draftInvoiceCannotBeCredited() {
            long draftId = createDraftInvoice(sessionA);

            ResponseEntity<String> response = create(sessionA, """
                    {"originalInvoiceId": %d, "lines": [{"invoiceLineId": 1, "netAmount": 10.00}]}
                    """.formatted(draftId));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(objectMapper.readTree(response.getBody()).get("errors")
                    .has("originalInvoiceId")).isTrue();
        }
    }

    @Nested
    @DisplayName("the over-credit limit")
    class OverCredit {

        @Test
        @DisplayName("a single credit beyond the line amount is refused")
        void singleOverCreditIsRefused() {
            SentInvoice invoice = sentInvoice(sessionA);

            ResponseEntity<String> response = create(sessionA, """
                    {
                      "originalInvoiceId": %d,
                      "lines": [{"invoiceLineId": %d, "netAmount": 100.01}]
                    }
                    """.formatted(invoice.id(), invoice.standardLineId()));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(objectMapper.readTree(response.getBody()).get("errors")
                    .get("lines[0].netAmount").asString())
                    .contains("only 100.00 of 100.00 is left to credit");
            assertThat(creditNoteRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("several credit notes together may not exceed the line either")
        void creditsAccumulateAcrossNotes() {
            SentInvoice invoice = sentInvoice(sessionA);

            // 60 + 30 fits in the 100.00 line
            assertThat(creditLine(sessionA, invoice, 60).getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(creditLine(sessionA, invoice, 30).getStatusCode()).isEqualTo(HttpStatus.CREATED);

            // 20 more would make 110
            ResponseEntity<String> third = creditLine(sessionA, invoice, 20);

            assertThat(third.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(objectMapper.readTree(third.getBody()).get("errors")
                    .get("lines[0].netAmount").asString())
                    .contains("only 10.00 of 100.00 is left to credit");
            assertThat(creditNoteRepository.findAll()).hasSize(2);
        }

        @Test
        @DisplayName("hitting the limit exactly is allowed")
        void exactLimitIsAllowed() {
            SentInvoice invoice = sentInvoice(sessionA);

            assertThat(creditLine(sessionA, invoice, 60).getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(creditLine(sessionA, invoice, 40).getStatusCode()).isEqualTo(HttpStatus.CREATED);

            // and nothing is left afterwards
            assertThat(creditLine(sessionA, invoice, 1).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST);

            assertThat(creditNoteRepository.creditedNetFor(invoice.id(), invoice.standardLineId()))
                    .isEqualByComparingTo(new BigDecimal("-100.00"));
        }

        @Test
        @DisplayName("draft credit notes count towards the limit")
        void draftsCountTowardsTheLimit() {
            SentInvoice invoice = sentInvoice(sessionA);

            // left as a draft on purpose: it is not sent
            assertThat(creditLine(sessionA, invoice, 100).getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(creditNoteRepository.findAll().getFirst().getStatus())
                    .isEqualTo(CreditNoteStatus.DRAFT);

            assertThat(creditLine(sessionA, invoice, 1).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("deleting a draft frees its room again")
        void deletingADraftFreesRoom() {
            SentInvoice invoice = sentInvoice(sessionA);

            long draftId = objectMapper.readTree(creditLine(sessionA, invoice, 100).getBody())
                    .get("id").asLong();
            assertThat(creditLine(sessionA, invoice, 1).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST);

            assertThat(send(sessionA, HttpMethod.DELETE, CREDIT_NOTES + "/" + draftId, null)
                    .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

            assertThat(creditLine(sessionA, invoice, 100).getStatusCode())
                    .isEqualTo(HttpStatus.CREATED);
        }

        @Test
        @DisplayName("the same line twice in one request is added up")
        void sameLineTwiceInOneRequest() {
            SentInvoice invoice = sentInvoice(sessionA);

            ResponseEntity<String> response = create(sessionA, """
                    {
                      "originalInvoiceId": %d,
                      "lines": [
                        {"invoiceLineId": %d, "netAmount": 60.00},
                        {"invoiceLineId": %d, "netAmount": 60.00}
                      ]
                    }
                    """.formatted(invoice.id(), invoice.standardLineId(), invoice.standardLineId()));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(creditNoteRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("a line of another invoice is refused")
        void foreignLineIsRefused() {
            SentInvoice first = sentInvoice(sessionA);
            SentInvoice second = sentInvoice(sessionA);

            ResponseEntity<String> response = create(sessionA, """
                    {
                      "originalInvoiceId": %d,
                      "lines": [{"invoiceLineId": %d, "netAmount": 10.00}]
                    }
                    """.formatted(first.id(), second.standardLineId()));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(objectMapper.readTree(response.getBody()).get("errors")
                    .has("lines[0].invoiceLineId")).isTrue();
        }
    }

    @Nested
    @DisplayName("concurrency")
    class Concurrency {

        /**
         * Ten credit notes at once, each for 30.00 of a 100.00 line. Any three
         * of them fit; a fourth does not. Without the lock on the invoice they
         * would all read the same "nothing credited yet" and all be let through.
         */
        @Test
        @DisplayName("parallel credits together never exceed the line")
        void parallelCreditsRespectTheLimit() throws Exception {
            SentInvoice invoice = sentInvoice(sessionA);

            int attempts = 10;
            ExecutorService pool = Executors.newFixedThreadPool(attempts);
            CountDownLatch startSignal = new CountDownLatch(1);

            int accepted;
            try {
                List<Future<ResponseEntity<String>>> futures = new ArrayList<>(attempts);
                for (int index = 0; index < attempts; index++) {
                    futures.add(pool.submit(() -> {
                        startSignal.await();
                        return creditLine(sessionA, invoice, 30);
                    }));
                }
                startSignal.countDown();

                int created = 0;
                for (Future<ResponseEntity<String>> future : futures) {
                    HttpStatus status = (HttpStatus) future.get(60, TimeUnit.SECONDS).getStatusCode();
                    // either it fitted, or it was refused for lack of room
                    assertThat(status).isIn(HttpStatus.CREATED, HttpStatus.BAD_REQUEST);
                    if (status == HttpStatus.CREATED) {
                        created++;
                    }
                }
                accepted = created;
            } finally {
                pool.shutdownNow();
            }

            // 3 x 30 = 90 fits, 4 x 30 = 120 does not
            assertThat(accepted).isEqualTo(3);

            BigDecimal credited = creditNoteRepository
                    .creditedNetFor(invoice.id(), invoice.standardLineId()).negate();
            assertThat(credited).isEqualByComparingTo("90.00");
            assertThat(credited).isLessThanOrEqualTo(new BigDecimal("100.00"));
            assertThat(creditNoteRepository.findAll()).hasSize(accepted);
        }
    }

    @Nested
    @DisplayName("numbering and immutability")
    class Lifecycle {

        @Test
        @DisplayName("sending assigns a number from the credit note's own series")
        void sendAssignsNumberFromOwnSeries() {
            SentInvoice invoice = sentInvoice(sessionA);
            long creditNoteId = objectMapper.readTree(creditLine(sessionA, invoice, 40).getBody())
                    .get("id").asLong();

            ResponseEntity<String> response =
                    send(sessionA, HttpMethod.POST, CREDIT_NOTES + "/" + creditNoteId + "/send", null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            JsonNode body = objectMapper.readTree(response.getBody());
            assertThat(body.get("status").asString()).isEqualTo("SENT");
            // its own series starts at 0001, even though invoice 0001 already exists
            assertThat(body.get("creditNoteNumber").asString())
                    .isEqualTo(Year.now().getValue() + "-0001");
            assertThat(invoice.number()).isEqualTo(Year.now().getValue() + "-0001");
            assertThat(body.get("creditDate").isNull()).isFalse();

            // two sequences side by side: one per document type
            assertThat(numberSequenceRepository.findAll()).hasSize(2);
        }

        @Test
        @DisplayName("credit note numbers run up on their own")
        void numbersAreSequential() {
            SentInvoice invoice = sentInvoice(sessionA);
            long first = objectMapper.readTree(creditLine(sessionA, invoice, 40).getBody())
                    .get("id").asLong();
            long second = objectMapper.readTree(creditLine(sessionA, invoice, 30).getBody())
                    .get("id").asLong();

            String year = String.valueOf(Year.now().getValue());
            assertThat(numberOf(sendOk(sessionA, first))).isEqualTo(year + "-0001");
            assertThat(numberOf(sendOk(sessionA, second))).isEqualTo(year + "-0002");
        }

        @Test
        @DisplayName("a sent credit note cannot be sent again or deleted")
        void sentCreditNoteIsFrozen() {
            SentInvoice invoice = sentInvoice(sessionA);
            long id = objectMapper.readTree(creditLine(sessionA, invoice, 40).getBody())
                    .get("id").asLong();
            sendOk(sessionA, id);

            assertThat(send(sessionA, HttpMethod.POST, CREDIT_NOTES + "/" + id + "/send", null)
                    .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(send(sessionA, HttpMethod.DELETE, CREDIT_NOTES + "/" + id, null)
                    .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

            CreditNote stored = creditNoteRepository.findAll().getFirst();
            assertThat(stored.getStatus()).isEqualTo(CreditNoteStatus.SENT);
            assertThat(stored.getCreditNoteNumber()).isNotNull();
        }
    }

    @Nested
    @DisplayName("ownership")
    class Ownership {

        @Test
        @DisplayName("A cannot credit an invoice of B")
        void cannotCreditForeignInvoice() {
            SentInvoice ofB = sentInvoice(sessionB, customerB);

            ResponseEntity<String> response = create(sessionA, """
                    {
                      "originalInvoiceId": %d,
                      "lines": [{"invoiceLineId": %d, "netAmount": 10.00}]
                    }
                    """.formatted(ofB.id(), ofB.standardLineId()));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(creditNoteRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("A cannot read, send or delete a credit note of B")
        void cannotTouchForeignCreditNote() {
            SentInvoice ofB = sentInvoice(sessionB, customerB);
            long idOfB = objectMapper.readTree(creditLine(sessionB, ofB, 40).getBody())
                    .get("id").asLong();

            assertThat(get(sessionA, CREDIT_NOTES + "/" + idOfB).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(send(sessionA, HttpMethod.POST, CREDIT_NOTES + "/" + idOfB + "/send", null)
                    .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(send(sessionA, HttpMethod.DELETE, CREDIT_NOTES + "/" + idOfB, null)
                    .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

            assertThat(get(sessionB, CREDIT_NOTES + "/" + idOfB).getStatusCode())
                    .isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("the list shows only this company's credit notes")
        void listIsScoped() {
            SentInvoice ofA = sentInvoice(sessionA);
            SentInvoice ofB = sentInvoice(sessionB, customerB);
            creditLine(sessionA, ofA, 40);
            creditLine(sessionB, ofB, 40);

            JsonNode list = objectMapper.readTree(get(sessionA, CREDIT_NOTES).getBody());

            assertThat(list.size()).isEqualTo(1);
            assertThat(list.get(0).get("customerName").asString())
                    .isEqualTo("Bakkerij De Korenbloem");
        }

        @Test
        @DisplayName("every route needs authentication")
        void requiresAuthentication() {
            SentInvoice invoice = sentInvoice(sessionA);
            long id = objectMapper.readTree(creditLine(sessionA, invoice, 40).getBody())
                    .get("id").asLong();

            assertThat(restTemplate.getForEntity(CREDIT_NOTES, String.class).getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(restTemplate.getForEntity(CREDIT_NOTES + "/" + id, String.class).getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(send(null, HttpMethod.POST, CREDIT_NOTES + "/" + id + "/send", null)
                    .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Nested
    @DisplayName("PDF")
    class Pdf {

        @Test
        @DisplayName("a sent credit note yields a PDF with negative amounts and the invoice reference")
        void sentCreditNoteYieldsPdf() {
            SentInvoice invoice = sentInvoice(sessionA);
            long id = objectMapper.readTree(creditLine(sessionA, invoice, 40).getBody())
                    .get("id").asLong();
            String number = numberOf(sendOk(sessionA, id));

            ResponseEntity<byte[]> response = downloadPdf(sessionA, id);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
            assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                    .contains("creditnota-" + number + ".pdf");

            String text = textOf(response.getBody());
            assertThat(text).contains("CREDITNOTA");
            assertThat(text).contains(number);
            // it says which invoice it corrects
            assertThat(text).contains(invoice.number());
            assertThat(text).contains("corrigeert factuur");
            // and the amounts are negative
            assertThat(text).contains("-40,00");
            assertThat(text).contains("-8,40");
            assertThat(text).contains("-48,40");
        }

        @Test
        @DisplayName("a draft credit note has no PDF")
        void draftHasNoPdf() {
            SentInvoice invoice = sentInvoice(sessionA);
            long id = objectMapper.readTree(creditLine(sessionA, invoice, 40).getBody())
                    .get("id").asLong();

            assertThat(downloadPdf(sessionA, id).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        @DisplayName("A cannot download the PDF of a credit note of B")
        void cannotDownloadForeignPdf() {
            SentInvoice ofB = sentInvoice(sessionB, customerB);
            long idOfB = objectMapper.readTree(creditLine(sessionB, ofB, 40).getBody())
                    .get("id").asLong();
            sendOk(sessionB, idOfB);

            assertThat(downloadPdf(sessionA, idOfB).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(downloadPdf(sessionB, idOfB).getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    // --- helpers ---------------------------------------------------------

    /** A sent invoice plus the ids of its three lines. */
    private record SentInvoice(long id, String number, long standardLineId, long reducedLineId,
                               long reverseChargeLineId) {
    }

    /**
     * One sent invoice with three lines: 100.00 at 21%, 50.00 at 9% and 200.00
     * reverse charged. Net 350.00, VAT 25.50, total 375.50.
     */
    private SentInvoice sentInvoice(String sessionCookie) {
        return sentInvoice(sessionCookie, customerA);
    }

    private SentInvoice sentInvoice(String sessionCookie, long customerId) {
        ResponseEntity<String> created = send(sessionCookie, HttpMethod.POST, INVOICES, """
                {
                  "customerId": %d,
                  "lines": [
                    {"description": "Advies", "quantity": 1, "unitPrice": 100.00, "vatRate": 21, "vatType": "STANDARD"},
                    {"description": "Drukwerk", "quantity": 1, "unitPrice": 50.00, "vatRate": 9, "vatType": "REDUCED"},
                    {"description": "EU-werk", "quantity": 1, "unitPrice": 200.00, "vatRate": 0, "vatType": "REVERSE_CHARGE"}
                  ]
                }
                """.formatted(customerId));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        JsonNode body = objectMapper.readTree(created.getBody());
        long invoiceId = body.get("id").asLong();

        ResponseEntity<String> sent =
                send(sessionCookie, HttpMethod.POST, INVOICES + "/" + invoiceId + "/send", null);
        assertThat(sent.getStatusCode()).isEqualTo(HttpStatus.OK);
        String number = objectMapper.readTree(sent.getBody()).get("invoiceNumber").asString();

        JsonNode lines = body.get("lines");
        return new SentInvoice(invoiceId, number,
                lines.get(0).get("id").asLong(),
                lines.get(1).get("id").asLong(),
                lines.get(2).get("id").asLong());
    }

    private long createDraftInvoice(String sessionCookie) {
        ResponseEntity<String> created = send(sessionCookie, HttpMethod.POST, INVOICES, """
                {
                  "customerId": %d,
                  "lines": [
                    {"description": "Werk", "quantity": 1, "unitPrice": 10.00, "vatRate": 21, "vatType": "STANDARD"}
                  ]
                }
                """.formatted(customerA));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return objectMapper.readTree(created.getBody()).get("id").asLong();
    }

    /** Credits the 21% line of the given invoice for a whole number of euro. */
    private ResponseEntity<String> creditLine(String sessionCookie, SentInvoice invoice, int amount) {
        return create(sessionCookie, """
                {
                  "originalInvoiceId": %d,
                  "lines": [{"invoiceLineId": %d, "netAmount": %d.00}]
                }
                """.formatted(invoice.id(), invoice.standardLineId(), amount));
    }

    private ResponseEntity<String> create(String sessionCookie, String json) {
        return send(sessionCookie, HttpMethod.POST, CREDIT_NOTES, json);
    }

    private ResponseEntity<String> sendOk(String sessionCookie, long id) {
        ResponseEntity<String> response =
                send(sessionCookie, HttpMethod.POST, CREDIT_NOTES + "/" + id + "/send", null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response;
    }

    private String numberOf(ResponseEntity<String> response) {
        return objectMapper.readTree(response.getBody()).get("creditNoteNumber").asString();
    }

    private ResponseEntity<String> get(String sessionCookie, String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, sessionCookie);
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private ResponseEntity<byte[]> downloadPdf(String sessionCookie, long id) {
        HttpHeaders headers = new HttpHeaders();
        if (sessionCookie != null) {
            headers.add(HttpHeaders.COOKIE, sessionCookie);
        }
        return restTemplate.exchange(CREDIT_NOTES + "/" + id + "/pdf", HttpMethod.GET,
                new HttpEntity<>(headers), byte[].class);
    }

    private static String textOf(byte[] pdf) {
        assertThat(pdf).isNotNull();
        try (var document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        } catch (IOException exception) {
            throw new AssertionError("Could not read the generated PDF", exception);
        }
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
        creditNoteRepository.deleteAll();
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
                  "addressLine": "Molenstraat 8",
                  "postalCode": "3512JK",
                  "city": "Utrecht"
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
                            "addressLine": "Keizersgracht 12",
                            "postalCode": "1015CN",
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
