package io.github.mohammedfaraj46.backend.repository;

import io.github.mohammedfaraj46.backend.domain.Company;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * A company is owned by a user, so its ownership-filtered lookups take
 * {@code userId} where the other repositories take {@code companyId}. See the
 * package documentation for the pattern.
 */
public interface CompanyRepository extends JpaRepository<Company, Long> {

    /** One company per user, so this is at most one row. */
    Optional<Company> findByUserId(Long userId);

    /**
     * Ownership-filtered lookup: returns empty for a company the user does not
     * own, exactly as it does for one that does not exist.
     */
    Optional<Company> findByIdAndUserId(Long id, Long userId);
}
