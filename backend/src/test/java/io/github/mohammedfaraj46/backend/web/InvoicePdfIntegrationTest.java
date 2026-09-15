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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.text.PDFTextStripper;
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
import tools.jackson.databind.ObjectMapper;

/**
 * The invoice PDF: who may download one, and when there is one to download.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@DisplayName("Invoice PDF")
class InvoicePdfIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String EMAIL_A = "pdf.a@example.nl";
    private static final String EMAIL_B = "pdf.b@example.nl";
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
        customerA = createCustomer(sessionA, "Bakkerij De Korenbloem");
        customerB = createCustomer(sessionB, "Klant van B");
    }

    @AfterEach
    void clearSequences() {
        invoiceRepository.deleteAll();
        numberSequenceRepository.deleteAll();
    }

    @Test
    @DisplayName("a sent invoice yields a real PDF with the number in the filename")
    void sentInvoiceYieldsPdf() {
        long id = createDraft(sessionA, customerA);
        String number = sendAndGetNumber(sessionA, id);

        ResponseEntity<byte[]> response = downloadPdf(sessionA, id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("attachment")
                .contains("factuur-" + number + ".pdf");

        byte[] body = response.getBody();
        assertThat(body).isNotNull();
        // a PDF starts with %PDF- and ends with the EOF marker; anything else is
        // not a document a reader will open
        assertThat(new String(body, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        assertThat(new String(body, StandardCharsets.ISO_8859_1)).contains("%%EOF");
        assertThat(body.length).isGreaterThan(1000);

        // and it actually says what an invoice has to say
        String text = textOf(body);
        assertThat(text)
                .contains(number)
                .contains("Bakkerij De Korenbloem")
                .contains("Werk")
                // the labels are uppercased by the stylesheet
                .contains("FACTUURNUMMER")
                .contains("VERVALDATUM")
                // the sender's own details belong on it too
                .contains("Keizersgracht 12")
                .contains("NL001234567B01");
    }

    @Test
    @DisplayName("the PDF carries the amounts, not just the labels")
    void pdfContainsAmounts() {
        long id = createAndGetId(sessionA, """
                {
                  "customerId": %d,
                  "lines": [
                    {"description": "Advies", "quantity": 3, "unitPrice": 19.99, "vatRate": 21, "vatType": "STANDARD"}
                  ]
                }
                """.formatted(customerA));
        sendAndGetNumber(sessionA, id);

        String text = textOf(downloadPdf(sessionA, id).getBody());

        // 3 x 19,99 = 59,97 net, 12,59 VAT, 72,56 total — Dutch notation
        assertThat(text).contains("59,97");
        assertThat(text).contains("12,59");
        assertThat(text).contains("72,56");
        assertThat(text).contains("19,99");
    }

    @Test
    @DisplayName("the euro sign renders, in a font that travels with the document")
    void euroSignRendersInAnEmbeddedFont() {
        long id = createDraft(sessionA, customerA);
        sendAndGetNumber(sessionA, id);

        byte[] pdf = downloadPdf(sessionA, id).getBody();
        assertThat(pdf).isNotNull();

        // the character itself, not an approximation of it: a missing glyph
        // comes out as a box, or as nothing at all
        assertThat(textOf(pdf)).contains("\u20ac");

        // and it is our own font, embedded, so a reader without DejaVu installed
        // sees the same document instead of a substituted one. This is what a
        // bare container would otherwise get wrong.
        assertThat(embeddedFontNames(pdf))
                .isNotEmpty()
                .allSatisfy(name -> assertThat(name).contains("DejaVuSans"));
    }

    @Test
    @DisplayName("a paid invoice still has a PDF")
    void paidInvoiceYieldsPdf() {
        long id = createDraft(sessionA, customerA);
        sendAndGetNumber(sessionA, id);
        assertThat(post(sessionA, INVOICES + "/" + id + "/pay").getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(downloadPdf(sessionA, id).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("an invoice with reverse charge and two rates renders too")
    void mixedRatesRender() {
        long id = createAndGetId(sessionA, """
                {
                  "customerId": %d,
                  "notes": "Bedankt voor de opdracht.",
                  "lines": [
                    {"description": "Advies", "quantity": 3, "unitPrice": 19.99, "vatRate": 21, "vatType": "STANDARD"},
                    {"description": "Drukwerk", "quantity": 1, "unitPrice": 50.00, "vatRate": 9, "vatType": "REDUCED"},
                    {"description": "EU-werk", "quantity": 2, "unitPrice": 250.00, "vatRate": 0, "vatType": "REVERSE_CHARGE"}
                  ]
                }
                """.formatted(customerA));
        sendAndGetNumber(sessionA, id);

        ResponseEntity<byte[]> response = downloadPdf(sessionA, id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        String text = textOf(response.getBody());
        // one breakdown row per rate, and the reverse-charge line is marked
        assertThat(text).contains("BTW 0%").contains("BTW 9%").contains("BTW 21%");
        assertThat(text).contains("BTW verlegd");
        assertThat(text).contains("Bedankt voor de opdracht.");
    }

    @Test
    @DisplayName("a draft has no PDF")
    void draftHasNoPdf() {
        long id = createDraft(sessionA, customerA);

        ResponseEntity<byte[]> response = downloadPdf(sessionA, id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("A cannot download the PDF of an invoice of B")
    void cannotDownloadForeignPdf() {
        long idOfB = createDraft(sessionB, customerB);
        sendAndGetNumber(sessionB, idOfB);

        assertThat(downloadPdf(sessionA, idOfB).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        // B can, so it is the owner filter that stopped A
        assertThat(downloadPdf(sessionB, idOfB).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("an unknown invoice is a 404, indistinguishable from a foreign one")
    void unknownInvoiceIsNotFound() {
        long idOfB = createDraft(sessionB, customerB);
        sendAndGetNumber(sessionB, idOfB);

        assertThat(downloadPdf(sessionA, 999_999L).getStatusCode())
                .isEqualTo(downloadPdf(sessionA, idOfB).getStatusCode());
    }

    @Test
    @DisplayName("downloading requires authentication")
    void requiresAuthentication() {
        long id = createDraft(sessionA, customerA);
        sendAndGetNumber(sessionA, id);

        assertThat(downloadPdf(null, id).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // --- helpers ---------------------------------------------------------

    /** The visible text of the PDF, so assertions can be about content. */
    private static String textOf(byte[] pdf) {
        assertThat(pdf).isNotNull();
        try (var document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        } catch (IOException exception) {
            throw new AssertionError("Could not read the generated PDF", exception);
        }
    }

    /**
     * The names of the fonts actually embedded in the document, asserting along
     * the way that each one is embedded rather than merely referenced. A
     * subsetted font carries a six-letter tag, so a name reads like
     * {@code ABCDEF+DejaVuSans}.
     */
    private static List<String> embeddedFontNames(byte[] pdf) {
        try (var document = Loader.loadPDF(pdf)) {
            List<String> names = new ArrayList<>();
            PDResources resources = document.getPage(0).getResources();
            for (COSName fontName : resources.getFontNames()) {
                PDFont font = resources.getFont(fontName);
                assertThat(font.isEmbedded())
                        .as("font %s is embedded", font.getName())
                        .isTrue();
                names.add(font.getName());
            }
            return names;
        } catch (IOException exception) {
            throw new AssertionError("Could not read the generated PDF", exception);
        }
    }

    private ResponseEntity<byte[]> downloadPdf(String sessionCookie, long id) {
        HttpHeaders headers = new HttpHeaders();
        if (sessionCookie != null) {
            headers.add(HttpHeaders.COOKIE, sessionCookie);
        }
        return restTemplate.exchange(
                INVOICES + "/" + id + "/pdf", HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
    }

    private String sendAndGetNumber(String sessionCookie, long id) {
        ResponseEntity<String> response = post(sessionCookie, INVOICES + "/" + id + "/send");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return objectMapper.readTree(response.getBody()).get("invoiceNumber").asString();
    }

    private long createDraft(String sessionCookie, long customerId) {
        return createAndGetId(sessionCookie, """
                {
                  "customerId": %d,
                  "lines": [
                    {"description": "Werk", "quantity": 1, "unitPrice": 100.00, "vatRate": 21, "vatType": "STANDARD"}
                  ]
                }
                """.formatted(customerId));
    }

    private long createAndGetId(String sessionCookie, String json) {
        ResponseEntity<String> response = send(sessionCookie, HttpMethod.POST, INVOICES, json);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return objectMapper.readTree(response.getBody()).get("id").asLong();
    }

    private ResponseEntity<String> post(String sessionCookie, String path) {
        return send(sessionCookie, HttpMethod.POST, path, null);
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
                  "vatNumber": "NL009876543B01",
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
