package io.github.mohammedfaraj46.backend.service;

import io.github.mohammedfaraj46.backend.domain.Company;
import io.github.mohammedfaraj46.backend.domain.Customer;
import io.github.mohammedfaraj46.backend.repository.CompanyRepository;
import io.github.mohammedfaraj46.backend.repository.CustomerRepository;
import io.github.mohammedfaraj46.backend.repository.InvoiceRepository;
import io.github.mohammedfaraj46.backend.security.CurrentUser;
import io.github.mohammedfaraj46.backend.web.dto.CustomerRequest;
import io.github.mohammedfaraj46.backend.web.dto.CustomerResponse;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Customer management, scoped to the signed-in company.
 *
 * <p>The company id comes from {@link CurrentUser} and goes into every query;
 * no method takes an owner from its caller. A customer id that does not resolve
 * within that company is a {@link CustomerNotFoundException}, which is what
 * makes another company's rows indistinguishable from rows that do not exist.
 */
@Service
public class CustomerService {

    private static final Logger log = LoggerFactory.getLogger(CustomerService.class);

    private static final Sort BY_NAME = Sort.by(Sort.Direction.ASC, "name");

    private final CustomerRepository customerRepository;
    private final CompanyRepository companyRepository;
    private final InvoiceRepository invoiceRepository;
    private final CurrentUser currentUser;

    public CustomerService(CustomerRepository customerRepository,
                           CompanyRepository companyRepository,
                           InvoiceRepository invoiceRepository,
                           CurrentUser currentUser) {
        this.customerRepository = customerRepository;
        this.companyRepository = companyRepository;
        this.invoiceRepository = invoiceRepository;
        this.currentUser = currentUser;
    }

    @Transactional
    public CustomerResponse create(CustomerRequest request) {
        Company company = ownCompany();

        Customer customer = new Customer(
                company,
                request.name(),
                request.email(),
                request.addressLine(),
                request.postalCode(),
                request.city(),
                request.country());
        customer.setVatNumber(request.vatNumber());

        return CustomerResponse.from(customerRepository.save(customer));
    }

    @Transactional(readOnly = true)
    public List<CustomerResponse> findAll() {
        return customerRepository.findAllByCompanyId(currentUser.companyId(), BY_NAME)
                .stream()
                .map(CustomerResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CustomerResponse findById(Long id) {
        return CustomerResponse.from(requireOwnCustomer(id));
    }

    @Transactional
    public CustomerResponse update(Long id, CustomerRequest request) {
        Customer customer = requireOwnCustomer(id);

        customer.setName(request.name());
        customer.setEmail(request.email());
        customer.setVatNumber(request.vatNumber());
        customer.setAddressLine(request.addressLine());
        customer.setPostalCode(request.postalCode());
        customer.setCity(request.city());
        customer.setCountry(request.country());

        // still inside the transaction, so dirty checking writes the update
        return CustomerResponse.from(customer);
    }

    /**
     * Removes a customer, unless invoices still name it.
     *
     * <p>The check runs before the delete so the caller gets a 409 that says
     * what is wrong instead of the foreign key's 500. The foreign key is still
     * the real guard: an invoice created between this check and the delete would
     * be refused by the database, which is the outcome we want either way.
     */
    @Transactional
    public void delete(Long id) {
        Long companyId = currentUser.companyId();

        if (!customerRepository.existsByIdAndCompanyId(id, companyId)) {
            throw new CustomerNotFoundException(id);
        }
        if (invoiceRepository.existsByCompanyIdAndCustomerId(companyId, id)) {
            throw new CustomerInUseException(id);
        }

        customerRepository.deleteByIdAndCompanyId(id, companyId);
        // deletions are the ones you want to be able to reconstruct afterwards
        log.info("Deleted customer {} of company {}", id, companyId);
    }

    /** Loads a customer, or fails if it is not this company's. */
    private Customer requireOwnCustomer(Long id) {
        return customerRepository.findByIdAndCompanyId(id, currentUser.companyId())
                .orElseThrow(() -> new CustomerNotFoundException(id));
    }

    private Company ownCompany() {
        Long companyId = currentUser.companyId();
        return companyRepository.findByIdAndUserId(companyId, currentUser.userId())
                .orElseThrow(() -> new IllegalStateException(
                        "Company " + companyId + " is no longer owned by the signed-in user"));
    }
}
