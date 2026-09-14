package io.github.mohammedfaraj46.backend.web;

import io.github.mohammedfaraj46.backend.service.CompanyService;
import io.github.mohammedfaraj46.backend.web.dto.CompanyResponse;
import io.github.mohammedfaraj46.backend.web.dto.CompanyUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The company profile of the signed-in user.
 */
@RestController
@RequestMapping("/api/companies")
public class CompanyController {

    private final CompanyService companyService;

    public CompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }

    /**
     * Deliberately has no id in the path: "me" is resolved from the
     * authenticated user, so there is no way to ask for someone else's company.
     * 401 without a valid login cookie.
     */
    @GetMapping("/me")
    public CompanyResponse me() {
        return companyService.currentCompany();
    }

    /** Edits that same company; 400 with field errors when something is missing. */
    @PutMapping("/me")
    public CompanyResponse update(@Valid @RequestBody CompanyUpdateRequest request) {
        return companyService.update(request);
    }
}
