/**
 * Repositories, and the ownership rule they all follow.
 *
 * <h2>Every query that reads owned data takes the owner as a parameter</h2>
 *
 * Data belongs to a company. A repository method that can return owned rows
 * therefore takes {@code companyId} and filters on it, so a row from another
 * company can never come back — not even when the caller passes an id it found
 * somewhere else. The filtering lives in the query, not in a check afterwards.
 *
 * <pre>{@code
 * public interface CustomerRepository extends JpaRepository<Customer, Long> {
 *
 *     // single row: id AND owner, so a foreign id yields an empty Optional
 *     Optional<Customer> findByIdAndCompanyId(Long id, Long companyId);
 *
 *     // list: scoped to one company
 *     List<Customer> findAllByCompanyId(Long companyId);
 *
 *     // existence and deletion are scoped too, never by id alone
 *     boolean existsByIdAndCompanyId(Long id, Long companyId);
 * }
 * }</pre>
 *
 * The service supplies that owner id from
 * {@link io.github.mohammedfaraj46.backend.security.CurrentUser}, never from the
 * request:
 *
 * <pre>{@code
 * @Transactional(readOnly = true)
 * public CustomerResponse get(Long customerId) {
 *     return customerRepository.findByIdAndCompanyId(customerId, currentUser.companyId())
 *             .map(CustomerResponse::from)
 *             .orElseThrow(() -> new CustomerNotFoundException(customerId));
 * }
 * }</pre>
 *
 * <h2>Why this shape</h2>
 *
 * <ul>
 *   <li><b>An empty result, not an exception.</b> Someone else's row and a row
 *       that does not exist look identical, so a 404 cannot be used to find out
 *       which ids are taken.</li>
 *   <li><b>No plain {@code findById} on owned entities.</b> It returns rows
 *       regardless of owner, and any check bolted on afterwards is one that a
 *       later caller can forget. If you need it, add the owner to the query.</li>
 *   <li><b>Enforced on the backend only.</b> The frontend never decides what a
 *       user may see; it just renders what these queries return.</li>
 * </ul>
 *
 * {@link io.github.mohammedfaraj46.backend.repository.CompanyRepository} is the
 * one step up the chain: a company is owned by a user, so its ownership-filtered
 * lookup takes {@code userId} instead.
 */
package io.github.mohammedfaraj46.backend.repository;
