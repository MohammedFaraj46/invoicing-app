package io.github.mohammedfaraj46.backend.web;

import io.github.mohammedfaraj46.backend.service.InvoiceService;
import io.github.mohammedfaraj46.backend.web.dto.InvoiceRequest;
import io.github.mohammedfaraj46.backend.web.dto.InvoiceResponse;
import io.github.mohammedfaraj46.backend.web.dto.InvoiceSummaryResponse;
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
 * Draft invoices. Ids resolve within the signed-in company, so an invoice of
 * another company is a 404 here just like an unknown one.
 *
 * <p>Editing and deleting are limited to drafts; a sent invoice answers 409.
 */
@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    /** Creates a DRAFT: no number and no invoice date yet. */
    @PostMapping
    public ResponseEntity<InvoiceResponse> create(@Valid @RequestBody InvoiceRequest request) {
        InvoiceResponse created = invoiceService.create(request);
        return ResponseEntity.created(URI.create("/api/invoices/" + created.id())).body(created);
    }

    @GetMapping
    public List<InvoiceSummaryResponse> findAll() {
        return invoiceService.findAll();
    }

    /** Detail, including the lines and the VAT breakdown. */
    @GetMapping("/{id}")
    public InvoiceResponse findById(@PathVariable Long id) {
        return invoiceService.findById(id);
    }

    @PutMapping("/{id}")
    public InvoiceResponse update(@PathVariable Long id,
                                  @Valid @RequestBody InvoiceRequest request) {
        return invoiceService.update(id, request);
    }

    /**
     * DRAFT to SENT: assigns the invoice number and the dates. 409 if the
     * invoice was already sent.
     */
    @PostMapping("/{id}/send")
    public InvoiceResponse send(@PathVariable Long id) {
        return invoiceService.send(id);
    }

    /**
     * SENT to PAID. 409 unless the invoice is currently SENT.
     *
     * <p>A named action rather than a PATCH on the status field: the server owns
     * the lifecycle, and an endpoint that takes a status would invite callers to
     * ask for transitions that are not allowed anyway.
     */
    @PostMapping("/{id}/pay")
    public InvoiceResponse markPaid(@PathVariable Long id) {
        return invoiceService.markPaid(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        invoiceService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
