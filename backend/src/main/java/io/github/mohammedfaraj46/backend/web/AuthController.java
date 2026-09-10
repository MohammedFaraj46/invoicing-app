package io.github.mohammedfaraj46.backend.web;

import io.github.mohammedfaraj46.backend.service.RegistrationService;
import io.github.mohammedfaraj46.backend.web.dto.RegisterRequest;
import io.github.mohammedfaraj46.backend.web.dto.RegisterResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Registration endpoint. Thin by design: all the work lives in
 * {@link RegistrationService}, and error mapping in {@link ApiExceptionHandler}.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final RegistrationService registrationService;

    public AuthController(RegistrationService registrationService) {
        this.registrationService = registrationService;
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
}
