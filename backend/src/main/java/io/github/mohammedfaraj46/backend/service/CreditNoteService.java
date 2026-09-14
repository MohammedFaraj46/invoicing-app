package io.github.mohammedfaraj46.backend.service;

import io.github.mohammedfaraj46.backend.domain.CreditNote;
import io.github.mohammedfaraj46.backend.domain.CreditNoteLine;
import io.github.mohammedfaraj46.backend.domain.CreditNoteStatus;
import io.github.mohammedfaraj46.backend.domain.DocumentType;
import io.github.mohammedfaraj46.backend.domain.Invoice;
import io.github.mohammedfaraj46.backend.domain.InvoiceLine;
import io.github.mohammedfaraj46.backend.repository.CreditNoteRepository;
import io.github.mohammedfaraj46.backend.repository.InvoiceRepository;
import io.github.mohammedfaraj46.backend.security.CurrentUser;
import io.github.mohammedfaraj46.backend.service.calculation.CalculationLine;
import io.github.mohammedfaraj46.backend.service.calculation.InvoiceCalculator;
import io.github.mohammedfaraj46.backend.service.calculation.LineAmounts;
import io.github.mohammedfaraj46.backend.service.calculation.VatSubtotal;
import io.github.mohammedfaraj46.backend.service.pdf.CreditNotePdfViewMapper;
import io.github.mohammedfaraj46.backend.service.pdf.DocumentPdfRenderer;
import io.github.mohammedfaraj46.backend.web.dto.CreditNoteLineRequest;
import io.github.mohammedfaraj46.backend.web.dto.CreditNoteRequest;
import io.github.mohammedfaraj46.backend.web.dto.CreditNoteResponse;
import io.github.mohammedfaraj46.backend.web.dto.CreditNoteSummaryResponse;
import io.github.mohammedfaraj46.backend.web.dto.InvoiceCreditStatusResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Sort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Credit notes: partial or full corrections on an invoice that has gone out.
 *
 * <p>Crediting happens on amounts. For each line of the original invoice the
 * caller says how much to give back excluding VAT; the rate and type come from
 * that original line, so a correction is always at the rate that was charged.
 * The resulting amounts are stored negative, which makes an invoice of +121 and
 * a credit note of -40 simply add up to the 81 that is still outstanding.
 */
@Service
public class CreditNoteService {

    private static final Logger log = LoggerFactory.getLogger(CreditNoteService.class);

    private static final Sort NEWEST_FIRST =
            Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));

    private final CreditNoteRepository creditNoteRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceCalculator calculator;
    private final DocumentNumberAllocator numberAllocator;
    private final CreditNotePdfViewMapper pdfViewMapper;
    private final DocumentPdfRenderer pdfRenderer;
    private final CurrentUser currentUser;

    public CreditNoteService(CreditNoteRepository creditNoteRepository,
                             InvoiceRepository invoiceRepository,
                             InvoiceCalculator calculator,
                             DocumentNumberAllocator numberAllocator,
                             CreditNotePdfViewMapper pdfViewMapper,
                             DocumentPdfRenderer pdfRenderer,
                             CurrentUser currentUser) {
        this.creditNoteRepository = creditNoteRepository;
        this.invoiceRepository = invoiceRepository;
        this.calculator = calculator;
        this.numberAllocator = numberAllocator;
        this.pdfViewMapper = pdfViewMapper;
        this.pdfRenderer = pdfRenderer;
        this.currentUser = currentUser;
    }

    /** A rendered credit note, ready to be streamed to the browser. */
    public record CreditNotePdf(String fileName, byte[] content) {
    }

    /**
     * Creates a draft credit note against an invoice.
     *
     * <p>The invoice row is locked for the length of this transaction. The
     * over-credit check reads what has already been credited and then writes
     * more, and two of those at once would both see the same room left and both
     * take it. Locking the invoice makes crediting serial per invoice — the same
     * shape of fix as the number sequence row, and for the same reason.
     */
    @Transactional
    public CreditNoteResponse create(CreditNoteRequest request) {
        Long companyId = currentUser.companyId();

        Invoice invoice = invoiceRepository.lockByIdAndCompanyId(request.originalInvoiceId(), companyId)
                .orElseThrow(() -> new InvoiceNotFoundException(request.originalInvoiceId()));

        if (invoice.getInvoiceNumber() == null) {
            throw new InvoiceValidationException(Map.of(
                    "originalInvoiceId",
                    "only a sent invoice can be credited; this one is still a draft"));
        }

        Map<Long, InvoiceLine> invoiceLines = new LinkedHashMap<>();
        for (InvoiceLine line : invoice.getLines()) {
            invoiceLines.put(line.getId(), line);
        }

        List<CreditNoteLineRequest> requested = onlyCreditedLines(request.lines());
        validateRequestedLines(requested, invoiceLines, invoice.getId());

        CreditNote creditNote = new CreditNote(
                invoice.getCompany(), invoice.getCustomer(), invoice);
        creditNote.setStatus(CreditNoteStatus.DRAFT);
        creditNote.setReason(request.reason());

        applyLinesAndAmounts(creditNote, requested, invoiceLines);
        CreditNote saved = creditNoteRepository.save(creditNote);

        // crediting is money going back, so every one of them is worth a line
        log.info("Created credit note {} against invoice {} for {}",
                saved.getId(), invoice.getId(), saved.getTotalAmount());

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<CreditNoteSummaryResponse> findAll() {
        return creditNoteRepository.findAllByCompanyId(currentUser.companyId(), NEWEST_FIRST)
                .stream()
                .map(CreditNoteSummaryResponse::from)
                .toList();
    }

    /**
     * What is left to credit on an invoice, per line, plus the credit notes
     * already raised against it. Feeds the credit note form and the credit
     * section on the invoice detail page.
     */
    @Transactional(readOnly = true)
    public InvoiceCreditStatusResponse creditStatusFor(Long invoiceId) {
        Long companyId = currentUser.companyId();

        Invoice invoice = invoiceRepository.findByIdAndCompanyId(invoiceId, companyId)
                .orElseThrow(() -> new InvoiceNotFoundException(invoiceId));

        Map<Long, BigDecimal> credited = creditedPerLine(invoiceId);

        List<InvoiceCreditStatusResponse.Line> lines = invoice.getLines().stream()
                .map(line -> {
                    BigDecimal alreadyCredited =
                            credited.getOrDefault(line.getId(), BigDecimal.ZERO);
                    return new InvoiceCreditStatusResponse.Line(
                            line.getId(),
                            line.getPosition(),
                            line.getDescription(),
                            line.getVatRate(),
                            line.getVatType(),
                            line.getLineNetAmount(),
                            alreadyCredited,
                            line.getLineNetAmount().subtract(alreadyCredited));
                })
                .toList();

        List<CreditNoteSummaryResponse> creditNotes = creditNoteRepository
                .findAllByCompanyIdAndOriginalInvoiceId(companyId, invoiceId, NEWEST_FIRST)
                .stream()
                .map(CreditNoteSummaryResponse::from)
                .toList();

        return new InvoiceCreditStatusResponse(
                invoice.getId(), invoice.getInvoiceNumber(), lines, creditNotes);
    }

    @Transactional(readOnly = true)
    public CreditNoteResponse findById(Long id) {
        return toResponse(requireOwnCreditNote(id));
    }

    /** DRAFT to SENT: assigns a number from the credit note series and freezes it. */
    @Transactional
    public CreditNoteResponse send(Long id) {
        CreditNote creditNote = requireOwnCreditNote(id);
        if (!creditNote.getStatus().canTransitionTo(CreditNoteStatus.SENT)) {
            throw new CreditNoteStatusTransitionException(
                    id, creditNote.getStatus(), CreditNoteStatus.SENT);
        }

        LocalDate today = LocalDate.now();
        // a separate series from the invoices, per the data model
        String number = numberAllocator.allocate(
                currentUser.companyId(), DocumentType.CREDIT_NOTE, today.getYear());

        creditNote.setCreditNoteNumber(number);
        creditNote.setCreditDate(today);
        creditNote.setStatus(CreditNoteStatus.SENT);
        log.info("Sent credit note {} as {} against invoice {}, {}", creditNote.getId(), number,
                creditNote.getOriginalInvoice().getId(), creditNote.getTotalAmount());

        return toResponse(creditNote);
    }

    /**
     * Removes a draft. Worth having: an abandoned draft would otherwise keep
     * occupying credit room on the invoice forever, since drafts count towards
     * the over-credit check.
     */
    @Transactional
    public void delete(Long id) {
        CreditNote creditNote = requireOwnCreditNote(id);
        if (!creditNote.getStatus().isEditable()) {
            throw new CreditNoteNotEditableException(id, creditNote.getStatus());
        }
        creditNoteRepository.delete(creditNote);
        log.info("Deleted draft credit note {} of company {}", id, currentUser.companyId());
    }

    @Transactional(readOnly = true)
    public CreditNotePdf generatePdf(Long id) {
        CreditNote creditNote = requireOwnCreditNote(id);
        if (creditNote.getCreditNoteNumber() == null) {
            throw new CreditNotePdfUnavailableException(id);
        }

        byte[] content = pdfRenderer.render(
                "pdf/credit-note", "creditNote", pdfViewMapper.toView(creditNote));
        return new CreditNotePdf("creditnota-" + creditNote.getCreditNoteNumber() + ".pdf", content);
    }

    // --- crediting ------------------------------------------------------

    /** Lines with nothing to credit are simply left out. */
    private static List<CreditNoteLineRequest> onlyCreditedLines(List<CreditNoteLineRequest> lines) {
        return lines.stream()
                .filter(line -> line.netAmount() != null
                        && line.netAmount().compareTo(BigDecimal.ZERO) > 0)
                .toList();
    }

    /**
     * The heart of it: every requested amount must fit in what is left of its
     * own invoice line, counting everything credited so far.
     */
    private void validateRequestedLines(List<CreditNoteLineRequest> requested,
                                        Map<Long, InvoiceLine> invoiceLines,
                                        Long invoiceId) {
        if (requested.isEmpty()) {
            throw new InvoiceValidationException(Map.of(
                    "lines", "at least one line must have an amount greater than 0"));
        }

        Map<Long, BigDecimal> alreadyCredited = creditedPerLine(invoiceId);
        Map<Long, BigDecimal> requestedPerLine = new HashMap<>();
        Map<String, String> errors = new LinkedHashMap<>();

        for (int index = 0; index < requested.size(); index++) {
            CreditNoteLineRequest line = requested.get(index);
            String field = "lines[" + index + "].netAmount";

            InvoiceLine invoiceLine = invoiceLines.get(line.invoiceLineId());
            if (invoiceLine == null) {
                errors.put("lines[" + index + "].invoiceLineId",
                        "line " + line.invoiceLineId() + " does not belong to invoice " + invoiceId);
                continue;
            }

            // the same line twice in one request has to be added up as well
            BigDecimal requestedForLine = requestedPerLine
                    .merge(invoiceLine.getId(), line.netAmount(), BigDecimal::add);

            BigDecimal credited = alreadyCredited.getOrDefault(invoiceLine.getId(), BigDecimal.ZERO);
            BigDecimal remaining = invoiceLine.getLineNetAmount().subtract(credited);

            if (requestedForLine.compareTo(remaining) > 0) {
                errors.put(field, "cannot credit %s on line %d: only %s of %s is left to credit"
                        .formatted(requestedForLine.toPlainString(),
                                invoiceLine.getPosition(),
                                remaining.toPlainString(),
                                invoiceLine.getLineNetAmount().toPlainString()));
            }
        }

        if (!errors.isEmpty()) {
            throw new InvoiceValidationException(errors);
        }
    }

    /** Per invoice line, how much has already been credited — as a positive amount. */
    private Map<Long, BigDecimal> creditedPerLine(Long invoiceId) {
        Map<Long, BigDecimal> credited = new HashMap<>();
        for (Object[] row : creditNoteRepository.sumCreditedNetPerInvoiceLine(invoiceId)) {
            Long lineId = (Long) row[0];
            BigDecimal sum = (BigDecimal) row[1];
            // stored negative; the check thinks in positive amounts
            credited.put(lineId, sum == null ? BigDecimal.ZERO : sum.negate());
        }
        return credited;
    }

    /**
     * Builds the credit note lines and totals.
     *
     * <p>Runs through the same {@link InvoiceCalculator} as an invoice, by
     * treating the credited amount as one unit at that price. That keeps the
     * rounding identical to the invoice it corrects. The sign is applied
     * afterwards: quantity and unit price stay positive so the line still reads
     * as "1 x 40,00", and only the computed amounts go negative.
     */
    private void applyLinesAndAmounts(CreditNote creditNote,
                                      List<CreditNoteLineRequest> requested,
                                      Map<Long, InvoiceLine> invoiceLines) {
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal vatTotal = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;

        for (int index = 0; index < requested.size(); index++) {
            CreditNoteLineRequest requestedLine = requested.get(index);
            InvoiceLine origin = invoiceLines.get(requestedLine.invoiceLineId());

            LineAmounts amounts = calculator.calculateLine(new CalculationLine(
                    BigDecimal.ONE, requestedLine.netAmount(), origin.getVatRate()));

            CreditNoteLine line = new CreditNoteLine(
                    index + 1,
                    origin.getDescription(),
                    BigDecimal.ONE,
                    amounts.netAmount(),
                    // rate and type are inherited, never chosen by the caller
                    origin.getVatRate(),
                    origin.getVatType());
            line.setInvoiceLine(origin);
            line.setLineNetAmount(amounts.netAmount().negate());
            line.setLineVatAmount(amounts.vatAmount().negate());
            line.setLineTotal(amounts.total().negate());

            creditNote.addLine(line);

            subtotal = subtotal.add(line.getLineNetAmount());
            vatTotal = vatTotal.add(line.getLineVatAmount());
            total = total.add(line.getLineTotal());
        }

        creditNote.setSubtotalAmount(subtotal);
        creditNote.setVatAmount(vatTotal);
        creditNote.setTotalAmount(total);
    }

    private CreditNoteResponse toResponse(CreditNote creditNote) {
        List<VatSubtotal> perLine = new ArrayList<>(creditNote.getLines().size());
        for (CreditNoteLine line : creditNote.getLines()) {
            perLine.add(new VatSubtotal(line.getVatRate(), line.getLineNetAmount(), line.getLineVatAmount()));
        }
        return CreditNoteResponse.from(creditNote, calculator.groupByRate(perLine));
    }

    private CreditNote requireOwnCreditNote(Long id) {
        return creditNoteRepository.findByIdAndCompanyId(id, currentUser.companyId())
                .orElseThrow(() -> new CreditNoteNotFoundException(id));
    }
}
