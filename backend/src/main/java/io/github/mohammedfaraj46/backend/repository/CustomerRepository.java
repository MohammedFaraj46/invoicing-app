package io.github.mohammedfaraj46.backend.repository;

import io.github.mohammedfaraj46.backend.domain.Customer;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Customers, scoped to their owning company. Every method here takes the owner,
 * so a row from another company can never come back — see the package
 * documentation for the rule.
 *
 * <p>Note there is deliberately no {@code findById} or {@code deleteById} in use:
 * those ignore the owner.
 */
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    /** Empty for a customer of another company, exactly as for one that does not exist. */
    Optional<Customer> findByIdAndCompanyId(Long id, Long companyId);

    /** All customers of one company. The caller supplies the ordering. */
    List<Customer> findAllByCompanyId(Long companyId, Sort sort);

    boolean existsByIdAndCompanyId(Long id, Long companyId);

    /**
     * Owner-filtered delete.
     *
     * @return the number of rows removed: 0 when the id belongs to another
     *         company or to nothing at all, which the service turns into a 404
     */
    long deleteByIdAndCompanyId(Long id, Long companyId);
}
