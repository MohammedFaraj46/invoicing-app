package io.github.mohammedfaraj46.backend.web;

import io.github.mohammedfaraj46.backend.service.CustomerService;
import io.github.mohammedfaraj46.backend.service.InvoiceService;
import io.github.mohammedfaraj46.backend.web.dto.CustomerInvoicesResponse;
import io.github.mohammedfaraj46.backend.web.dto.CustomerRequest;
import io.github.mohammedfaraj46.backend.web.dto.CustomerResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Customer CRUD. Every route resolves ids within the signed-in company, so an
 * id belonging to another company is a 404 here just like an unknown one.
 */
@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;
    private final InvoiceService invoiceService;

    public CustomerController(CustomerService customerService, InvoiceService invoiceService) {
        this.customerService = customerService;
        this.invoiceService = invoiceService;
    }

    @PostMapping
    public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CustomerRequest request) {
        CustomerResponse created = customerService.create(request);
        return ResponseEntity.created(URI.create("/api/customers/" + created.id())).body(created);
    }

    /** The signed-in company's customers, by name. */
    @GetMapping
    public List<CustomerResponse> findAll() {
        return customerService.findAll();
    }

    @GetMapping("/{id}")
    public CustomerResponse findById(@PathVariable Long id) {
        return customerService.findById(id);
    }

    /**
     * This customer's invoices, for the customer detail page.
     *
     * <p>A nested route rather than a {@code customerId} filter on
     * /api/invoices: the customer is what is being addressed, so an id from
     * another company answers 404 the way it does everywhere else. A filter
     * could only answer with an empty list, which says something different.
     */
    @GetMapping("/{id}/invoices")
    public CustomerInvoicesResponse invoices(@PathVariable Long id) {
        return invoiceService.findAllForCustomer(id);
    }

    @PutMapping("/{id}")
    public CustomerResponse update(@PathVariable Long id,
                                   @Valid @RequestBody CustomerRequest request) {
        return customerService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        customerService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
