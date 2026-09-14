package io.github.mohammedfaraj46.backend.web;

import static io.github.mohammedfaraj46.backend.support.HttpTestSupport.CSRF_HEADER_NAME;
import static io.github.mohammedfaraj46.backend.support.HttpTestSupport.JWT_COOKIE_NAME;
import static io.github.mohammedfaraj46.backend.support.HttpTestSupport.asRequestCookie;
import static io.github.mohammedfaraj46.backend.support.HttpTestSupport.fetchCsrf;
import static io.github.mohammedfaraj46.backend.support.HttpTestSupport.setCookie;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.mohammedfaraj46.backend.domain.Company;
import io.github.mohammedfaraj46.backend.domain.Customer;
import io.github.mohammedfaraj46.backend.repository.AppUserRepository;
import io.github.mohammedfaraj46.backend.repository.CompanyRepository;
import io.github.mohammedfaraj46.backend.repository.CustomerRepository;
import io.github.mohammedfaraj46.backend.support.AbstractPostgresIntegrationTest;
import io.github.mohammedfaraj46.backend.support.HttpTestSupport.Csrf;
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
 * Input validation on the three forms that take profile data: registration, the
 * customer form and the company profile.
 *
 * <p>Three things are checked for each of them. Blank and whitespace-only input
 * is refused with a message on the field. Input that is longer than its column
 * comes back as a 400 naming the field, not as a 500 from the database. And what
 * is stored is the stripped text, so the same name does not end up with and
 * without spaces depending on which screen wrote it.
 *
 * <p>The last group is the point of the exercise: a postal code and a company
 * registration number are only checked for presence and length, so an address
 * outside the Netherlands goes through unchanged.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@DisplayName("Input validation")
class InputValidationIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String EMAIL = "validatie@example.nl";
    private static final String PASSWORD = "geheim123";
    private static final String REGISTER = "/api/auth/register";
    private static final String CUSTOMERS = "/api/customers";
    private static final String COMPANY_ME = "/api/companies/me";

    /** One character past the varchar(255) columns. */
    private static final String TOO_LONG_255 = "a".repeat(256);
    /** One character past the varchar(100) city column. */
    private static final String TOO_LONG_100 = "a".repeat(101);

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private CustomerRepository customerRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void clearAll() {
        customerRepository.deleteAll();
        companyRepository.deleteAll();
        appUserRepository.deleteAll();
    }

    @Nested
    @DisplayName("registration")
    class Registration {

        @Test
        @DisplayName("a whitespace-only company name is refused")
        void whitespaceOnlyNameIsRefused() {
            ResponseEntity<String> response = postJson(REGISTER, registerJson("   ", "1011AB"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(errorsOf(response).get("company.name").asString()).contains("name is required");
            assertThat(appUserRepository.count()).isZero();
        }

        @Test
        @DisplayName("a company name past its column length is a 400, not a 500")
        void tooLongNameIsRejectedCleanly() {
            ResponseEntity<String> response = postJson(REGISTER, registerJson(TOO_LONG_255, "1011AB"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(errorsOf(response).get("company.name").asString())
                    .contains("at most 255 characters");
            assertThat(appUserRepository.count()).isZero();
        }

        @Test
        @DisplayName("a short password is refused, and so is an absurdly long one")
        void passwordLengthIsBounded() {
            assertThat(postJson(REGISTER, registerJson("Bedrijf", "1011AB", "kort12", EMAIL))
                    .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

            ResponseEntity<String> tooLong = postJson(REGISTER,
                    registerJson("Bedrijf", "1011AB", "x".repeat(101), EMAIL));

            assertThat(tooLong.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(errorsOf(tooLong).get("password").asString())
                    .contains("between 8 and 100 characters");
            assertThat(appUserRepository.count()).isZero();
        }

        @Test
        @DisplayName("surrounding spaces are stripped before anything is stored")
        void valuesAreStripped() {
            ResponseEntity<String> response = postJson(REGISTER, registerJson(
                    "  Faraj Development  ", "  3511 AA  ", PASSWORD, "  Validatie@Example.NL  "));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

            Company stored = companyRepository.findAll().getFirst();
            assertThat(stored.getName()).isEqualTo("Faraj Development");
            assertThat(stored.getPostalCode()).isEqualTo("3511 AA");
            // the email is stripped and lower-cased, so it can only be registered once
            assertThat(appUserRepository.findAll().getFirst().getEmail())
                    .isEqualTo("validatie@example.nl");
        }

        @Test
        @DisplayName("a non-Dutch postal code and registration number go through unchanged")
        void foreignIdentifiersAreAccepted() {
            // Irish Eircode and a Belgian enterprise number: neither fits a Dutch
            // pattern, and neither is this app's business to judge
            ResponseEntity<String> response = postJson(REGISTER, """
                    {
                      "email": "dublin@example.ie",
                      "password": "%s",
                      "company": {
                        "name": "Grafton Ltd",
                        "kvkNumber": "0123.456.789",
                        "vatNumber": "IE1234567FA",
                        "addressLine": "12 Grafton Street",
                        "postalCode": "D02 X285",
                        "city": "Dublin",
                        "country": "ie"
                      }
                    }
                    """.formatted(PASSWORD));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

            Company stored = companyRepository.findAll().getFirst();
            assertThat(stored.getPostalCode()).isEqualTo("D02 X285");
            assertThat(stored.getKvkNumber()).isEqualTo("0123.456.789");
            assertThat(stored.getCountry()).isEqualTo("IE");
        }
    }

    @Nested
    @DisplayName("customer")
    class CustomerForm {

        private String session;

        @BeforeEach
        void signIn() {
            assertThat(postJson(REGISTER, registerJson("Bedrijf", "1011AB")).getStatusCode())
                    .isEqualTo(HttpStatus.CREATED);
            session = loginCookie();
        }

        @Test
        @DisplayName("a whitespace-only name is refused")
        void whitespaceOnlyNameIsRefused() {
            ResponseEntity<String> response =
                    send(HttpMethod.POST, CUSTOMERS, customerJson("  ", "klant@example.nl", "1011AB"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(errorsOf(response).get("name").asString()).contains("name is required");
            assertThat(customerRepository.count()).isZero();
        }

        @Test
        @DisplayName("an address past its column length is a 400, not a 500")
        void tooLongAddressIsRejectedCleanly() {
            ResponseEntity<String> response = send(HttpMethod.POST, CUSTOMERS, """
                    {
                      "name": "Zon en Zo",
                      "email": "klant@example.nl",
                      "addressLine": "%s",
                      "postalCode": "1011AB",
                      "city": "%s"
                    }
                    """.formatted(TOO_LONG_255, TOO_LONG_100));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            JsonNode errors = errorsOf(response);
            assertThat(errors.get("addressLine").asString()).contains("at most 255 characters");
            assertThat(errors.get("city").asString()).contains("at most 100 characters");
            assertThat(customerRepository.count()).isZero();
        }

        @Test
        @DisplayName("a malformed email is refused")
        void malformedEmailIsRefused() {
            ResponseEntity<String> response =
                    send(HttpMethod.POST, CUSTOMERS, customerJson("Zon en Zo", "geen-adres", "1011AB"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(errorsOf(response).get("email").asString()).contains("valid email address");
        }

        @Test
        @DisplayName("values are stripped and an empty VAT number becomes absent")
        void valuesAreStrippedAndBlankVatBecomesNull() {
            ResponseEntity<String> response = send(HttpMethod.POST, CUSTOMERS, """
                    {
                      "name": "  Zon en Zo  ",
                      "email": "  Klant@Example.NL  ",
                      "vatNumber": "   ",
                      "addressLine": "  Havenweg 3  ",
                      "postalCode": "  1011 AB  ",
                      "city": "  Amsterdam  "
                    }
                    """);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

            Customer stored = customerRepository.findAll().getFirst();
            assertThat(stored.getName()).isEqualTo("Zon en Zo");
            assertThat(stored.getEmail()).isEqualTo("klant@example.nl");
            assertThat(stored.getAddressLine()).isEqualTo("Havenweg 3");
            assertThat(stored.getPostalCode()).isEqualTo("1011 AB");
            assertThat(stored.getCity()).isEqualTo("Amsterdam");
            // blank rather than absent, and still stored as nothing at all
            assertThat(stored.getVatNumber()).isNull();
            assertThat(objectMapper.readTree(response.getBody()).get("vatNumber").isNull()).isTrue();
        }

        @Test
        @DisplayName("a non-Dutch postal code is accepted")
        void foreignPostalCodeIsAccepted() {
            ResponseEntity<String> response = send(HttpMethod.POST, CUSTOMERS, """
                    {
                      "name": "Brussel Bureau",
                      "email": "info@example.be",
                      "addressLine": "Rue Neuve 1",
                      "postalCode": "1000",
                      "city": "Brussel",
                      "country": "BE"
                    }
                    """);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(customerRepository.findAll().getFirst().getPostalCode()).isEqualTo("1000");
        }

        private ResponseEntity<String> send(HttpMethod method, String path, String json) {
            return sendAs(session, method, path, json);
        }
    }

    @Nested
    @DisplayName("company profile")
    class CompanyProfile {

        private String session;

        @BeforeEach
        void signIn() {
            assertThat(postJson(REGISTER, registerJson("Bedrijf", "1011AB")).getStatusCode())
                    .isEqualTo(HttpStatus.CREATED);
            session = loginCookie();
        }

        @Test
        @DisplayName("a whitespace-only KvK number is refused")
        void whitespaceOnlyKvkIsRefused() {
            ResponseEntity<String> response =
                    sendAs(session, HttpMethod.PUT, COMPANY_ME, profileJson("Faraj", "   ", 14));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(errorsOf(response).get("kvkNumber").asString()).contains("kvkNumber is required");
            assertThat(companyRepository.findAll().getFirst().getName()).isEqualTo("Bedrijf");
        }

        @Test
        @DisplayName("a name past its column length is a 400, not a 500")
        void tooLongNameIsRejectedCleanly() {
            ResponseEntity<String> response =
                    sendAs(session, HttpMethod.PUT, COMPANY_ME, profileJson(TOO_LONG_255, "8765", 14));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(errorsOf(response).get("name").asString()).contains("at most 255 characters");
            assertThat(companyRepository.findAll().getFirst().getName()).isEqualTo("Bedrijf");
        }

        @Test
        @DisplayName("a payment term of zero or less is refused")
        void paymentTermMustBePositive() {
            assertThat(sendAs(session, HttpMethod.PUT, COMPANY_ME, profileJson("Faraj", "8765", 0))
                    .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

            ResponseEntity<String> negative =
                    sendAs(session, HttpMethod.PUT, COMPANY_ME, profileJson("Faraj", "8765", -5));

            assertThat(negative.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(errorsOf(negative).get("defaultPaymentTermDays").asString())
                    .contains("greater than zero");
            assertThat(companyRepository.findAll().getFirst().getDefaultPaymentTermDays())
                    .isEqualTo(30);
        }

        @Test
        @DisplayName("valid input goes through, stripped")
        void validInputIsStoredStripped() {
            ResponseEntity<String> response = sendAs(session, HttpMethod.PUT, COMPANY_ME,
                    profileJson("  Faraj Development  ", "  87654321  ", 14));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            Company stored = companyRepository.findAll().getFirst();
            assertThat(stored.getName()).isEqualTo("Faraj Development");
            assertThat(stored.getKvkNumber()).isEqualTo("87654321");
            assertThat(stored.getDefaultPaymentTermDays()).isEqualTo(14);
        }
    }

    // --- helpers ---------------------------------------------------------

    private static String registerJson(String companyName, String postalCode) {
        return registerJson(companyName, postalCode, PASSWORD, EMAIL);
    }

    private static String registerJson(String companyName, String postalCode,
                                       String password, String email) {
        return """
                {
                  "email": "%s",
                  "password": "%s",
                  "company": {
                    "name": "%s",
                    "kvkNumber": "12345678",
                    "vatNumber": "NL001234567B01",
                    "addressLine": "Straat 1",
                    "postalCode": "%s",
                    "city": "Amsterdam"
                  }
                }
                """.formatted(email, password, companyName, postalCode);
    }

    private static String customerJson(String name, String email, String postalCode) {
        return """
                {
                  "name": "%s",
                  "email": "%s",
                  "addressLine": "Straat 1",
                  "postalCode": "%s",
                  "city": "Amsterdam"
                }
                """.formatted(name, email, postalCode);
    }

    private static String profileJson(String name, String kvkNumber, int paymentTermDays) {
        return """
                {
                  "name": "%s",
                  "kvkNumber": "%s",
                  "vatNumber": "NL009876543B02",
                  "addressLine": "Kerkstraat 2",
                  "postalCode": "3511AA",
                  "city": "Utrecht",
                  "country": "NL",
                  "defaultPaymentTermDays": %d
                }
                """.formatted(name, kvkNumber, paymentTermDays);
    }

    private JsonNode errorsOf(ResponseEntity<String> response) {
        return objectMapper.readTree(response.getBody()).get("errors");
    }

    private ResponseEntity<String> postJson(String path, String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.postForEntity(path, new HttpEntity<>(json, headers), String.class);
    }

    /** State-changing call: login cookie plus a CSRF token, like the SPA sends. */
    private ResponseEntity<String> sendAs(String sessionCookie, HttpMethod method,
                                          String path, String json) {
        Csrf csrf = fetchCsrf(restTemplate);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(HttpHeaders.COOKIE, sessionCookie + "; " + csrf.cookieHeader());
        headers.add(CSRF_HEADER_NAME, csrf.token());

        return restTemplate.exchange(path, method, new HttpEntity<>(json, headers), String.class);
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
