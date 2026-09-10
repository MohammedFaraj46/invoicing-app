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
 * Customer CRUD over real HTTP, and the ownership rule that runs through it:
 * user A cannot read, change or delete a customer of user B.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@DisplayName("Customer CRUD")
class CustomerIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String EMAIL_A = "klant.a@example.nl";
    private static final String EMAIL_B = "klant.b@example.nl";
    private static final String PASSWORD = "geheim123";
    private static final String CUSTOMERS = "/api/customers";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private CustomerRepository customerRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String sessionA;
    private String sessionB;

    @BeforeEach
    void resetAndRegisterTwoUsers() {
        customerRepository.deleteAll();
        companyRepository.deleteAll();
        appUserRepository.deleteAll();

        register(EMAIL_A);
        register(EMAIL_B);
        sessionA = loginCookie(EMAIL_A);
        sessionB = loginCookie(EMAIL_B);
    }

    @Nested
    @DisplayName("happy path")
    class HappyPath {

        @Test
        @DisplayName("POST creates a customer and returns 201 with a Location")
        void createReturns201() {
            ResponseEntity<String> response = create(sessionA, customerJson("Klant Een", "een@klant.nl"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

            JsonNode body = objectMapper.readTree(response.getBody());
            assertThat(body.get("id").asLong()).isPositive();
            assertThat(body.get("name").asString()).isEqualTo("Klant Een");
            assertThat(body.get("email").asString()).isEqualTo("een@klant.nl");
            // country was left out of the payload, so the NL default applies
            assertThat(body.get("country").asString()).isEqualTo("NL");

            assertThat(response.getHeaders().getLocation())
                    .hasToString(CUSTOMERS + "/" + body.get("id").asLong());
        }

        @Test
        @DisplayName("GET returns only this company's customers, by name")
        void listReturnsOwnCustomersSorted() {
            create(sessionA, customerJson("Zeeman", "zeeman@klant.nl"));
            create(sessionA, customerJson("Aannemer", "aannemer@klant.nl"));
            create(sessionB, customerJson("Van B", "vanb@klant.nl"));

            JsonNode list = objectMapper.readTree(get(sessionA, CUSTOMERS).getBody());

            assertThat(list.size()).isEqualTo(2);
            assertThat(list.get(0).get("name").asString()).isEqualTo("Aannemer");
            assertThat(list.get(1).get("name").asString()).isEqualTo("Zeeman");
            assertThat(get(sessionA, CUSTOMERS).getBody()).doesNotContain("Van B");
        }

        @Test
        @DisplayName("GET by id returns the customer")
        void getByIdReturnsCustomer() {
            long id = createAndGetId(sessionA, customerJson("Klant Een", "een@klant.nl"));

            ResponseEntity<String> response = get(sessionA, CUSTOMERS + "/" + id);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(objectMapper.readTree(response.getBody()).get("name").asString())
                    .isEqualTo("Klant Een");
        }

        @Test
        @DisplayName("PUT updates the customer")
        void putUpdatesCustomer() {
            long id = createAndGetId(sessionA, customerJson("Oude Naam", "oud@klant.nl"));

            ResponseEntity<String> response = send(sessionA, HttpMethod.PUT, CUSTOMERS + "/" + id, """
                    {
                      "name": "Nieuwe Naam",
                      "email": "nieuw@klant.nl",
                      "vatNumber": "NL009876543B01",
                      "addressLine": "Andere Weg 9",
                      "postalCode": "3011AA",
                      "city": "Rotterdam",
                      "country": "BE"
                    }
                    """);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            JsonNode updated = objectMapper.readTree(get(sessionA, CUSTOMERS + "/" + id).getBody());
            assertThat(updated.get("name").asString()).isEqualTo("Nieuwe Naam");
            assertThat(updated.get("email").asString()).isEqualTo("nieuw@klant.nl");
            assertThat(updated.get("vatNumber").asString()).isEqualTo("NL009876543B01");
            assertThat(updated.get("city").asString()).isEqualTo("Rotterdam");
            assertThat(updated.get("country").asString()).isEqualTo("BE");
        }

        @Test
        @DisplayName("DELETE removes the customer and returns 204")
        void deleteRemovesCustomer() {
            long id = createAndGetId(sessionA, customerJson("Weg Ermee", "weg@klant.nl"));

            ResponseEntity<String> response = send(sessionA, HttpMethod.DELETE, CUSTOMERS + "/" + id, null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(get(sessionA, CUSTOMERS + "/" + id).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(customerRepository.findAll()).isEmpty();
        }
    }

    @Nested
    @DisplayName("ownership")
    class Ownership {

        @Test
        @DisplayName("A cannot read a customer of B")
        void cannotReadForeignCustomer() {
            long idOfB = createAndGetId(sessionB, customerJson("Van B", "vanb@klant.nl"));

            assertThat(get(sessionA, CUSTOMERS + "/" + idOfB).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND);
            // B still can, so it is the owner filter that stopped A
            assertThat(get(sessionB, CUSTOMERS + "/" + idOfB).getStatusCode())
                    .isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("A cannot update a customer of B, and B's data is untouched")
        void cannotUpdateForeignCustomer() {
            long idOfB = createAndGetId(sessionB, customerJson("Van B", "vanb@klant.nl"));

            ResponseEntity<String> response = send(sessionA, HttpMethod.PUT, CUSTOMERS + "/" + idOfB, """
                    {
                      "name": "Gekaapt",
                      "email": "kaper@example.nl",
                      "addressLine": "Straat 1",
                      "postalCode": "1011AB",
                      "city": "Amsterdam"
                    }
                    """);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(objectMapper.readTree(get(sessionB, CUSTOMERS + "/" + idOfB).getBody())
                    .get("name").asString()).isEqualTo("Van B");
        }

        @Test
        @DisplayName("A cannot delete a customer of B, and it survives")
        void cannotDeleteForeignCustomer() {
            long idOfB = createAndGetId(sessionB, customerJson("Van B", "vanb@klant.nl"));

            ResponseEntity<String> response =
                    send(sessionA, HttpMethod.DELETE, CUSTOMERS + "/" + idOfB, null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(get(sessionB, CUSTOMERS + "/" + idOfB).getStatusCode())
                    .isEqualTo(HttpStatus.OK);
            assertThat(customerRepository.findAll()).hasSize(1);
        }

        @Test
        @DisplayName("a foreign id is indistinguishable from an unknown one")
        void foreignIdLooksLikeUnknownId() {
            long idOfB = createAndGetId(sessionB, customerJson("Van B", "vanb@klant.nl"));

            ResponseEntity<String> foreign = get(sessionA, CUSTOMERS + "/" + idOfB);
            ResponseEntity<String> unknown = get(sessionA, CUSTOMERS + "/999999");

            assertThat(foreign.getStatusCode()).isEqualTo(unknown.getStatusCode());
            // same shape of body, only the id in the message differs
            assertThat(objectMapper.readTree(foreign.getBody()).get("title").asString())
                    .isEqualTo(objectMapper.readTree(unknown.getBody()).get("title").asString());
        }
    }

    @Nested
    @DisplayName("validation and authentication")
    class Guards {

        @Test
        @DisplayName("an invalid payload gives 400 with per-field errors")
        void invalidPayloadGives400() {
            ResponseEntity<String> response = create(sessionA, """
                    {"name": "", "email": "geen-email", "city": "Amsterdam"}
                    """);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

            JsonNode errors = objectMapper.readTree(response.getBody()).get("errors");
            assertThat(errors.has("name")).isTrue();
            assertThat(errors.has("email")).isTrue();
            assertThat(errors.has("addressLine")).isTrue();
            assertThat(errors.has("postalCode")).isTrue();
            assertThat(customerRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("every route returns 401 without authentication")
        void allRoutesRequireAuthentication() {
            long id = createAndGetId(sessionA, customerJson("Klant Een", "een@klant.nl"));

            assertThat(restTemplate.getForEntity(CUSTOMERS, String.class).getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(restTemplate.getForEntity(CUSTOMERS + "/" + id, String.class).getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(create(null, customerJson("Indringer", "indringer@klant.nl")).getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(send(null, HttpMethod.DELETE, CUSTOMERS + "/" + id, null).getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);

            // nothing was created or removed along the way
            assertThat(customerRepository.findAll()).hasSize(1);
        }
    }

    // --- helpers ---------------------------------------------------------

    private ResponseEntity<String> create(String sessionCookie, String json) {
        return send(sessionCookie, HttpMethod.POST, CUSTOMERS, json);
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

    private static String customerJson(String name, String email) {
        return """
                {
                  "name": "%s",
                  "email": "%s",
                  "addressLine": "Straat 1",
                  "postalCode": "1011AB",
                  "city": "Amsterdam"
                }
                """.formatted(name, email);
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
