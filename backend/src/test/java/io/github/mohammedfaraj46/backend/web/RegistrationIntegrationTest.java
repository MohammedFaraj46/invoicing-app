package io.github.mohammedfaraj46.backend.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mohammedfaraj46.backend.domain.AppUser;
import io.github.mohammedfaraj46.backend.domain.Company;
import io.github.mohammedfaraj46.backend.repository.AppUserRepository;
import io.github.mohammedfaraj46.backend.repository.CompanyRepository;
import io.github.mohammedfaraj46.backend.support.AbstractPostgresIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Integration tests for POST /api/auth/register, driven over real HTTP so that
 * bean validation and {@link ApiExceptionHandler} take part.
 *
 * <p>The database comes from {@link AbstractPostgresIntegrationTest}: a real
 * PostgreSQL 16 container that Flyway migrates with V1__init.sql, so the mapping
 * is exercised against the actual schema.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@DisplayName("POST /api/auth/register")
class RegistrationIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String REGISTER_URL = "/api/auth/register";
    private static final String PLAIN_PASSWORD = "geheim123";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Company holds the foreign key to app_user, so it has to go first. Wiping
     * before each test keeps the shared container from leaking state between them.
     */
    @BeforeEach
    void resetDatabase() {
        companyRepository.deleteAll();
        appUserRepository.deleteAll();
    }

    @Test
    @DisplayName("valid payload creates the user and company, and stores a BCrypt hash")
    void validRegistrationCreatesUserAndCompany() {
        ResponseEntity<String> response = register(payloadFor("nieuw@example.nl"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        List<AppUser> users = appUserRepository.findAll();
        List<Company> companies = companyRepository.findAll();
        assertThat(users).hasSize(1);
        assertThat(companies).hasSize(1);

        AppUser user = users.getFirst();
        Company company = companies.getFirst();
        assertThat(user.getEmail()).isEqualTo("nieuw@example.nl");
        assertThat(company.getUser().getId()).isEqualTo(user.getId());
        assertThat(company.getName()).isEqualTo("Test ZZP");
        assertThat(company.getCountry()).isEqualTo("NL");
        assertThat(company.getDefaultPaymentTermDays()).isEqualTo(30);

        // the password must be hashed, not stored or echoed back in the clear
        assertThat(user.getPasswordHash()).isNotEqualTo(PLAIN_PASSWORD);
        assertThat(user.getPasswordHash()).startsWith("$2");
        assertThat(passwordEncoder.matches(PLAIN_PASSWORD, user.getPasswordHash())).isTrue();
        assertThat(response.getBody()).doesNotContain(PLAIN_PASSWORD);
    }

    @Test
    @DisplayName("duplicate email is rejected with 409")
    void duplicateEmailIsRejected() {
        assertThat(register(payloadFor("dubbel@example.nl")).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> second = register(payloadFor("dubbel@example.nl"));

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(appUserRepository.findAll()).hasSize(1);
        assertThat(companyRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("email differing only in case is rejected with 409")
    void emailDifferingOnlyInCaseIsRejected() {
        assertThat(register(payloadFor("casing@example.nl")).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> second = register(payloadFor("Casing@Example.NL"));

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(appUserRepository.findAll()).hasSize(1);
        // stored in normalised form, which is what makes the second attempt collide
        assertThat(appUserRepository.findAll().getFirst().getEmail())
                .isEqualTo("casing@example.nl");
        assertThat(appUserRepository.findByEmail("casing@example.nl")).isPresent();
    }

    @Test
    @DisplayName("invalid payload is rejected with 400 and per-field errors")
    void invalidPayloadIsRejected() throws Exception {
        String invalid = """
                {
                  "email": "geen-email",
                  "password": "kort",
                  "company": {
                    "name": "",
                    "kvkNumber": "12345678"
                  }
                }
                """;

        ResponseEntity<String> response = register(invalid);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        JsonNode errors = objectMapper.readTree(response.getBody()).get("errors");
        assertThat(errors).isNotNull();
        assertThat(errors.has("email")).isTrue();
        assertThat(errors.has("password")).isTrue();
        assertThat(errors.has("company.name")).isTrue();
        assertThat(errors.has("company.vatNumber")).isTrue();
        assertThat(errors.has("company.addressLine")).isTrue();
        assertThat(errors.has("company.postalCode")).isTrue();
        assertThat(errors.has("company.city")).isTrue();

        assertThat(appUserRepository.findAll()).isEmpty();
        assertThat(companyRepository.findAll()).isEmpty();
    }

    private ResponseEntity<String> register(String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.postForEntity(REGISTER_URL, new HttpEntity<>(json, headers), String.class);
    }

    /** Valid payload; country is left out on purpose so the NL default applies. */
    private static String payloadFor(String email) {
        return """
                {
                  "email": "%s",
                  "password": "%s",
                  "company": {
                    "name": "Test ZZP",
                    "kvkNumber": "12345678",
                    "vatNumber": "NL001234567B01",
                    "addressLine": "Straat 1",
                    "postalCode": "1011AB",
                    "city": "Amsterdam"
                  }
                }
                """.formatted(email, PLAIN_PASSWORD);
    }
}
