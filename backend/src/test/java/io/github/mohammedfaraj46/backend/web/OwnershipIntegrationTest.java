package io.github.mohammedfaraj46.backend.web;

import static io.github.mohammedfaraj46.backend.support.HttpTestSupport.JWT_COOKIE_NAME;
import static io.github.mohammedfaraj46.backend.support.HttpTestSupport.asRequestCookie;
import static io.github.mohammedfaraj46.backend.support.HttpTestSupport.setCookie;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.mohammedfaraj46.backend.domain.AppUser;
import io.github.mohammedfaraj46.backend.domain.Company;
import io.github.mohammedfaraj46.backend.repository.AppUserRepository;
import io.github.mohammedfaraj46.backend.repository.CompanyRepository;
import io.github.mohammedfaraj46.backend.support.AbstractPostgresIntegrationTest;
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
 * The ownership rule, proven with two real accounts: whatever A asks for, A gets
 * A's data, and the query layer refuses to hand over B's row even when asked for
 * it by id.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@DisplayName("Company data ownership")
class OwnershipIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String EMAIL_A = "gebruiker.a@example.nl";
    private static final String EMAIL_B = "gebruiker.b@example.nl";
    private static final String PASSWORD = "geheim123";
    private static final String COMPANY_ME = "/api/companies/me";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private CompanyRepository companyRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void resetAndRegisterTwoUsers() {
        companyRepository.deleteAll();
        appUserRepository.deleteAll();

        assertThat(register(EMAIL_A, "Bedrijf A", "Amsterdam").getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        assertThat(register(EMAIL_B, "Bedrijf B", "Rotterdam").getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("each user gets their own company from /api/companies/me")
    void eachUserSeesOnlyTheirOwnCompany() {
        JsonNode forA = objectMapper.readTree(getCompanyMeAs(EMAIL_A).getBody());
        JsonNode forB = objectMapper.readTree(getCompanyMeAs(EMAIL_B).getBody());

        assertThat(forA.get("name").asString()).isEqualTo("Bedrijf A");
        assertThat(forA.get("city").asString()).isEqualTo("Amsterdam");

        assertThat(forB.get("name").asString()).isEqualTo("Bedrijf B");
        assertThat(forB.get("city").asString()).isEqualTo("Rotterdam");

        // different rows, and neither response mentions the other company at all
        assertThat(forA.get("id").asLong()).isNotEqualTo(forB.get("id").asLong());
        assertThat(getCompanyMeAs(EMAIL_A).getBody()).doesNotContain("Bedrijf B");
        assertThat(getCompanyMeAs(EMAIL_B).getBody()).doesNotContain("Bedrijf A");
    }

    @Test
    @DisplayName("the company returned to A is the one owned by A in the database")
    void responseMatchesTheStoredOwner() {
        JsonNode forA = objectMapper.readTree(getCompanyMeAs(EMAIL_A).getBody());

        AppUser userA = appUserRepository.findByEmail(EMAIL_A).orElseThrow();
        Company companyA = companyRepository.findByUserId(userA.getId()).orElseThrow();

        assertThat(forA.get("id").asLong()).isEqualTo(companyA.getId());
        // and that row really is A's, according to the ownership-filtered query
        assertThat(companyRepository.findByIdAndUserId(companyA.getId(), userA.getId()))
                .isPresent();
    }

    @Test
    @DisplayName("an ownership-filtered lookup with a foreign id returns nothing")
    void ownershipFilteredQueryRefusesForeignId() {
        AppUser userA = appUserRepository.findByEmail(EMAIL_A).orElseThrow();
        AppUser userB = appUserRepository.findByEmail(EMAIL_B).orElseThrow();
        Company companyB = companyRepository.findByUserId(userB.getId()).orElseThrow();

        // A knows B's company id and asks for it anyway
        assertThat(companyRepository.findByIdAndUserId(companyB.getId(), userA.getId()))
                .isEmpty();

        // and the same query does work for the owner, so the filter is what stopped it
        assertThat(companyRepository.findByIdAndUserId(companyB.getId(), userB.getId()))
                .isPresent();
    }

    @Test
    @DisplayName("a foreign id is indistinguishable from one that does not exist")
    void foreignIdLooksLikeAMissingRow() {
        AppUser userA = appUserRepository.findByEmail(EMAIL_A).orElseThrow();
        AppUser userB = appUserRepository.findByEmail(EMAIL_B).orElseThrow();
        Company companyB = companyRepository.findByUserId(userB.getId()).orElseThrow();

        assertThat(companyRepository.findByIdAndUserId(companyB.getId(), userA.getId()))
                .isEqualTo(companyRepository.findByIdAndUserId(999_999L, userA.getId()));
    }

    @Test
    @DisplayName("/api/companies/me returns 401 without authentication")
    void companyMeIsUnauthorizedWithoutCookie() {
        ResponseEntity<String> response = restTemplate.getForEntity(COMPANY_ME, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("/api/companies/me returns 401 with a garbage token")
    void companyMeIsUnauthorizedWithTamperedCookie() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, JWT_COOKIE_NAME + "=not.a.valid.token");

        ResponseEntity<String> response = restTemplate.exchange(
                COMPANY_ME, HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private ResponseEntity<String> getCompanyMeAs(String email) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, loginCookie(email));

        ResponseEntity<String> response = restTemplate.exchange(
                COMPANY_ME, HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response;
    }

    private String loginCookie(String email) {
        ResponseEntity<String> login = postJson("/api/auth/login", """
                {"email": "%s", "password": "%s"}
                """.formatted(email, PASSWORD));
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        return asRequestCookie(setCookie(login, JWT_COOKIE_NAME).orElseThrow());
    }

    private ResponseEntity<String> register(String email, String companyName, String city) {
        return postJson("/api/auth/register", """
                {
                  "email": "%s",
                  "password": "%s",
                  "company": {
                    "name": "%s",
                    "kvkNumber": "12345678",
                    "vatNumber": "NL001234567B01",
                    "addressLine": "Straat 1",
                    "postalCode": "1011AB",
                    "city": "%s"
                  }
                }
                """.formatted(email, PASSWORD, companyName, city));
    }

    private ResponseEntity<String> postJson(String path, String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.postForEntity(path, new HttpEntity<>(json, headers), String.class);
    }
}
