package io.github.mohammedfaraj46.backend.service;

import io.github.mohammedfaraj46.backend.domain.Company;
import io.github.mohammedfaraj46.backend.domain.Customer;
import io.github.mohammedfaraj46.backend.repository.CompanyRepository;
import io.github.mohammedfaraj46.backend.repository.CustomerRepository;
import io.github.mohammedfaraj46.backend.security.CurrentUser;
import io.github.mohammedfaraj46.backend.web.dto.CustomerRequest;
import io.github.mohammedfaraj46.backend.web.dto.CustomerResponse;
import java.util.List;
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

    private static final Sort BY_NAME = Sort.by(Sort.Direction.ASC, "name");

    private final CustomerRepository customerRepository;
    private final CompanyRepository companyRepository;
    private final CurrentUser currentUser;

    public CustomerService(CustomerRepository customerRepository,
                           CompanyRepository companyRepository,
                           CurrentUser currentUser) {
        this.customerRepository = customerRepository;
        this.companyRepository = companyRepository;
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

    @Transactional
    public void delete(Long id) {
        long removed = customerRepository.deleteByIdAndCompanyId(id, currentUser.companyId());
        if (removed == 0) {
            throw new CustomerNotFoundException(id);
        }
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
