package io.github.mohammedfaraj46.backend.service;

import io.github.mohammedfaraj46.backend.repository.CompanyRepository;
import io.github.mohammedfaraj46.backend.security.CurrentUser;
import io.github.mohammedfaraj46.backend.web.dto.CompanyResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads the company profile of the signed-in user.
 *
 * <p>Worked example of the ownership rule: the owner comes from
 * {@link CurrentUser}, and the query filters on it. Nothing in the request says
 * which company to read, so there is no id for a caller to tamper with.
 */
@Service
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final CurrentUser currentUser;

    public CompanyService(CompanyRepository companyRepository, CurrentUser currentUser) {
        this.companyRepository = companyRepository;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public CompanyResponse currentCompany() {
        Long userId = currentUser.userId();
        Long companyId = currentUser.companyId();

        // both ids come from the authenticated principal, so this cannot fail on a
        // healthy account; querying on the owner anyway keeps the rule uniform and
        // means a company reassigned to another user stops being readable at once
        return companyRepository.findByIdAndUserId(companyId, userId)
                .map(CompanyResponse::from)
                .orElseThrow(() -> new IllegalStateException(
                        "Company " + companyId + " is no longer owned by user " + userId));
    }
}
