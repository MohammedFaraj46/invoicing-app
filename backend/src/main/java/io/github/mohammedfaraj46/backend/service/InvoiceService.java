package io.github.mohammedfaraj46.backend.service;

import io.github.mohammedfaraj46.backend.domain.Company;
import io.github.mohammedfaraj46.backend.domain.Customer;
import io.github.mohammedfaraj46.backend.domain.Invoice;
import io.github.mohammedfaraj46.backend.domain.InvoiceLine;
import io.github.mohammedfaraj46.backend.domain.DocumentType;
import io.github.mohammedfaraj46.backend.domain.InvoiceStatus;
import io.github.mohammedfaraj46.backend.repository.CompanyRepository;
import io.github.mohammedfaraj46.backend.repository.CustomerRepository;
import io.github.mohammedfaraj46.backend.repository.InvoiceRepository;
import io.github.mohammedfaraj46.backend.security.CurrentUser;
import io.github.mohammedfaraj46.backend.service.calculation.CalculationLine;
import io.github.mohammedfaraj46.backend.service.calculation.InvoiceAmounts;
import io.github.mohammedfaraj46.backend.service.calculation.InvoiceCalculator;
import io.github.mohammedfaraj46.backend.service.calculation.LineAmounts;
import io.github.mohammedfaraj46.backend.service.calculation.VatSubtotal;
import io.github.mohammedfaraj46.backend.web.dto.InvoiceLineRequest;
import io.github.mohammedfaraj46.backend.web.dto.InvoiceRequest;
import io.github.mohammedfaraj46.backend.web.dto.InvoiceResponse;
import io.github.mohammedfaraj46.backend.web.dto.InvoiceSummaryResponse;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Draft invoices, scoped to the signed-in company.
 *
 * <p>Amounts are never taken from the request: the {@link InvoiceCalculator}
 * works them out and the result is stored as a snapshot on the invoice and its
 * lines. Numbering and the invoice date are not set here — those belong to
 * sending.
 */
@Service
public class InvoiceService {

    /** Newest first; drafts have no number or date yet, so creation order it is. */
    private static final Sort NEWEST_FIRST =
            Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));

    private final InvoiceRepository invoiceRepository;
    private final CustomerRepository customerRepository;
    private final CompanyRepository companyRepository;
    private final InvoiceCalculator calculator;
    private final DocumentNumberAllocator numberAllocator;
    private final CurrentUser currentUser;

    public InvoiceService(InvoiceRepository invoiceRepository,
                          CustomerRepository customerRepository,
                          CompanyRepository companyRepository,
                          InvoiceCalculator calculator,
                          DocumentNumberAllocator numberAllocator,
                          CurrentUser currentUser) {
        this.invoiceRepository = invoiceRepository;
        this.customerRepository = customerRepository;
        this.companyRepository = companyRepository;
        this.calculator = calculator;
        this.numberAllocator = numberAllocator;
        this.currentUser = currentUser;
    }

    @Transactional
    public InvoiceResponse create(InvoiceRequest request) {
        Company company = ownCompany();
        Customer customer = ownCustomer(request.customerId());
        validateRequestedVat(request.lines());

        int paymentTermDays = request.paymentTermDays() != null
                ? request.paymentTermDays()
                : company.getDefaultPaymentTermDays();

        // a draft: no number, no invoice date, no due date — those follow on send
        Invoice invoice = new Invoice(company, customer, paymentTermDays);
        invoice.setStatus(InvoiceStatus.DRAFT);
        invoice.setNotes(request.notes());

        applyLinesAndAmounts(invoice, request.lines());

        Invoice saved = invoiceRepository.save(invoice);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<InvoiceSummaryResponse> findAll() {
        return invoiceRepository.findAllByCompanyId(currentUser.companyId(), NEWEST_FIRST)
                .stream()
                .map(InvoiceSummaryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public InvoiceResponse findById(Long id) {
        return toResponse(requireOwnInvoice(id));
    }

    @Transactional
    public InvoiceResponse update(Long id, InvoiceRequest request) {
        Invoice invoice = requireOwnInvoice(id);
        requireEditable(invoice);
        validateRequestedVat(request.lines());

        invoice.setCustomer(ownCustomer(request.customerId()));
        if (request.paymentTermDays() != null) {
            invoice.setPaymentTermDays(request.paymentTermDays());
        }
        invoice.setNotes(request.notes());

        // replace the lines wholesale, then recalculate over the new set
        invoice.getLines().clear();
        // flush the deletes before the new lines go in: the unique index on
        // (invoice_id, position) would otherwise be hit by an insert that reuses a
        // position the old row still occupies
        invoiceRepository.flush();

        applyLinesAndAmounts(invoice, request.lines());

        return toResponse(invoice);
    }

    @Transactional
    public void delete(Long id) {
        Invoice invoice = requireOwnInvoice(id);
        requireEditable(invoice);
        invoiceRepository.delete(invoice);
    }

    /**
     * SENT to PAID: records that the money arrived.
     *
     * <p>Only reachable from SENT. Paying a draft makes no sense — the customer
     * never received anything — and paying twice is refused rather than silently
     * overwriting the moment it was first paid.
     */
    @Transactional
    public InvoiceResponse markPaid(Long id) {
        Invoice invoice = requireOwnInvoice(id);
        requireTransition(invoice, InvoiceStatus.PAID);

        invoice.setPaidAt(OffsetDateTime.now());
        invoice.setStatus(InvoiceStatus.PAID);

        return toResponse(invoice);
    }

    /**
     * DRAFT to SENT: assigns the definitive number, stamps the dates and locks
     * the invoice down.
     *
     * <p>Everything happens in one transaction, which is what makes the
     * numbering safe: the counter row stays locked from the moment it is read
     * until the invoice is committed with its number on it. A send that fails
     * afterwards rolls the counter back too, so no number is burnt.
     *
     * <p>The VAT rules are checked once more here. A draft can sit around for a
     * while, and this is the last moment at which the amounts can still be
     * corrected — after this they are frozen.
     */
    @Transactional
    public InvoiceResponse send(Long id) {
        Invoice invoice = requireOwnInvoice(id);
        // sending is the DRAFT -> SENT transition; re-sending is refused here
        requireTransition(invoice, InvoiceStatus.SENT);
        validateStoredVat(invoice.getLines());

        LocalDate today = LocalDate.now();
        String number = numberAllocator.allocate(
                currentUser.companyId(), DocumentType.INVOICE, today.getYear());

        invoice.setInvoiceNumber(number);
        invoice.setInvoiceDate(today);
        invoice.setDueDate(today.plusDays(invoice.getPaymentTermDays()));
        invoice.setSentAt(OffsetDateTime.now());
        invoice.setStatus(InvoiceStatus.SENT);

        return toResponse(invoice);
    }

    /**
     * Builds the lines from the request, runs the calculator, and writes the
     * result onto the lines and the invoice as a snapshot.
     */
    private void applyLinesAndAmounts(Invoice invoice, List<InvoiceLineRequest> requestedLines) {
        List<CalculationLine> toCalculate = requestedLines.stream()
                .map(line -> new CalculationLine(line.quantity(), line.unitPrice(), line.vatRate()))
                .toList();

        InvoiceAmounts amounts = calculator.calculate(toCalculate);

        for (int index = 0; index < requestedLines.size(); index++) {
            InvoiceLineRequest requested = requestedLines.get(index);
            LineAmounts calculated = amounts.lines().get(index);

            InvoiceLine line = new InvoiceLine(
                    index + 1, // positions are 1-based and follow the order sent
                    requested.description(),
                    requested.quantity(),
                    requested.unitPrice(),
                    requested.vatRate(),
                    requested.vatType());
            line.setLineNetAmount(calculated.netAmount());
            line.setLineVatAmount(calculated.vatAmount());
            line.setLineTotal(calculated.total());

            invoice.addLine(line);
        }

        invoice.setSubtotalAmount(amounts.subtotalAmount());
        invoice.setVatAmount(amounts.vatAmount());
        invoice.setTotalAmount(amounts.totalAmount());
    }

    /**
     * Rebuilds the VAT breakdown by grouping the amounts that are stored, not by
     * recalculating them — so a sent invoice keeps showing its own snapshot.
     */
    private InvoiceResponse toResponse(Invoice invoice) {
        List<VatSubtotal> perLine = new ArrayList<>(invoice.getLines().size());
        for (InvoiceLine line : invoice.getLines()) {
            perLine.add(new VatSubtotal(line.getVatRate(), line.getLineNetAmount(), line.getLineVatAmount()));
        }
        return InvoiceResponse.from(invoice, calculator.groupByRate(perLine));
    }

    /** VAT check on an incoming payload. */
    private static void validateRequestedVat(List<InvoiceLineRequest> lines) {
        VatConsistencyValidator.validate(lines.stream()
                .map(line -> new VatConsistencyValidator.Line(line.vatType(), line.vatRate()))
                .toList());
    }

    /** The same check on what is already stored, used at send time. */
    private static void validateStoredVat(List<InvoiceLine> lines) {
        VatConsistencyValidator.validate(lines.stream()
                .map(line -> new VatConsistencyValidator.Line(line.getVatType(), line.getVatRate()))
                .toList());
    }

    private Invoice requireOwnInvoice(Long id) {
        return invoiceRepository.findByIdAndCompanyId(id, currentUser.companyId())
                .orElseThrow(() -> new InvoiceNotFoundException(id));
    }

    /** Guards content changes: only a draft may be edited or deleted. */
    private static void requireEditable(Invoice invoice) {
        if (!invoice.getStatus().isEditable()) {
            throw new InvoiceNotEditableException(invoice.getId(), invoice.getStatus());
        }
    }

    /** Guards status changes against the lifecycle defined on {@link InvoiceStatus}. */
    private static void requireTransition(Invoice invoice, InvoiceStatus target) {
        if (!invoice.getStatus().canTransitionTo(target)) {
            throw new InvalidStatusTransitionException(invoice.getId(), invoice.getStatus(), target);
        }
    }

    /**
     * The customer must be one of this company's own, otherwise an invoice could
     * be hung on someone else's customer. Same ownership filter as
     * {@code existsByIdAndCompanyId}, but it hands back the entity we need.
     */
    private Customer ownCustomer(Long customerId) {
        return customerRepository.findByIdAndCompanyId(customerId, currentUser.companyId())
                .orElseThrow(() -> new CustomerNotFoundException(customerId));
    }

    private Company ownCompany() {
        Long companyId = currentUser.companyId();
        return companyRepository.findByIdAndUserId(companyId, currentUser.userId())
                .orElseThrow(() -> new IllegalStateException(
                        "Company " + companyId + " is no longer owned by the signed-in user"));
    }
}
