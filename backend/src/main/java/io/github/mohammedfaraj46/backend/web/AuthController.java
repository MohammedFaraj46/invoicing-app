package io.github.mohammedfaraj46.backend.web;

import io.github.mohammedfaraj46.backend.security.AppUserPrincipal;
import io.github.mohammedfaraj46.backend.security.JwtCookieFactory;
import io.github.mohammedfaraj46.backend.security.JwtService;
import io.github.mohammedfaraj46.backend.service.AuthenticationService;
import io.github.mohammedfaraj46.backend.service.RegistrationService;
import io.github.mohammedfaraj46.backend.web.dto.CsrfTokenResponse;
import io.github.mohammedfaraj46.backend.web.dto.CurrentUserResponse;
import io.github.mohammedfaraj46.backend.web.dto.LoginRequest;
import io.github.mohammedfaraj46.backend.web.dto.RegisterRequest;
import io.github.mohammedfaraj46.backend.web.dto.RegisterResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Registration and session endpoints. Thin by design: the work lives in the
 * services, error mapping in {@link ApiExceptionHandler}.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final RegistrationService registrationService;
    private final AuthenticationService authenticationService;
    private final JwtService jwtService;
    private final JwtCookieFactory cookieFactory;

    public AuthController(RegistrationService registrationService,
                          AuthenticationService authenticationService,
                          JwtService jwtService,
                          JwtCookieFactory cookieFactory) {
        this.registrationService = registrationService;
        this.authenticationService = authenticationService;
        this.jwtService = jwtService;
        this.cookieFactory = cookieFactory;
    }

    /**
     * Bootstrap for the SPA: call this once on load to receive the XSRF-TOKEN
     * cookie (and the token in the body), then send it back in the X-XSRF-TOKEN
     * header on every POST, PUT and DELETE.
     */
    @GetMapping("/csrf")
    public CsrfTokenResponse csrf(CsrfToken csrfToken) {
        return CsrfTokenResponse.from(csrfToken);
    }

    /**
     * 201 on success, 409 when the email is taken, 400 with field errors when the
     * payload is invalid.
     */
    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        RegisterResponse response = registrationService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 200 with the signed-in user and a Set-Cookie carrying the token, or 401
     * with a generic message when either half of the credentials is wrong.
     */
    @PostMapping("/login")
    public ResponseEntity<CurrentUserResponse> login(@Valid @RequestBody LoginRequest request) {
        AppUserPrincipal principal = authenticationService.authenticate(request.email(), request.password());
        ResponseCookie cookie = cookieFactory.issue(jwtService.generateToken(principal.getId()));

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(CurrentUserResponse.from(principal));
    }

    /** Clears the cookie. Safe to call without being signed in. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieFactory.clear().toString())
                .build();
    }

    /** The signed-in user, or 401 without a valid cookie. */
    @GetMapping("/me")
    public CurrentUserResponse me(@AuthenticationPrincipal AppUserPrincipal principal) {
        return CurrentUserResponse.from(principal);
    }
}
