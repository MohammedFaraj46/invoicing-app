package io.github.mohammedfaraj46.backend.service.pdf;

import io.github.mohammedfaraj46.backend.domain.Company;
import io.github.mohammedfaraj46.backend.domain.CreditNote;
import io.github.mohammedfaraj46.backend.domain.CreditNoteLine;
import io.github.mohammedfaraj46.backend.domain.Customer;
import io.github.mohammedfaraj46.backend.domain.Invoice;
import io.github.mohammedfaraj46.backend.domain.VatType;
import io.github.mohammedfaraj46.backend.service.calculation.InvoiceCalculator;
import io.github.mohammedfaraj46.backend.service.calculation.VatSubtotal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Flattens a stored credit note into the strings the template prints.
 *
 * <p>As with invoices, the VAT breakdown is grouped from the saved amounts
 * rather than recalculated, so a sent credit note keeps showing its own
 * snapshot.
 */
@Component
public class CreditNotePdfViewMapper {

    private final InvoiceCalculator calculator;

    public CreditNotePdfViewMapper(InvoiceCalculator calculator) {
        this.calculator = calculator;
    }

    public CreditNotePdfView toView(CreditNote creditNote) {
        Company company = creditNote.getCompany();
        Customer customer = creditNote.getCustomer();
        Invoice invoice = creditNote.getOriginalInvoice();

        List<CreditNotePdfView.Line> lines = creditNote.getLines().stream()
                .map(CreditNotePdfViewMapper::toLine)
                .toList();

        return new CreditNotePdfView(
                new InvoicePdfView.Sender(
                        company.getName(),
                        company.getAddressLine(),
                        company.getPostalCode(),
                        company.getCity(),
                        company.getCountry(),
                        company.getKvkNumber(),
                        company.getVatNumber()),
                new InvoicePdfView.Recipient(
                        customer.getName(),
                        customer.getAddressLine(),
                        customer.getPostalCode(),
                        customer.getCity(),
                        customer.getCountry(),
                        customer.getVatNumber()),
                creditNote.getCreditNoteNumber(),
                PdfFormatter.date(creditNote.getCreditDate()),
                invoice.getInvoiceNumber(),
                PdfFormatter.date(invoice.getInvoiceDate()),
                lines,
                toVatRows(creditNote),
                PdfFormatter.amount(creditNote.getSubtotalAmount()),
                PdfFormatter.amount(creditNote.getTotalAmount()),
                creditNote.getReason());
    }

    private static CreditNotePdfView.Line toLine(CreditNoteLine line) {
        return new CreditNotePdfView.Line(
                line.getDescription(),
                PdfFormatter.quantity(line.getQuantity()),
                PdfFormatter.amount(line.getUnitPrice()),
                PdfFormatter.rate(line.getVatRate()),
                PdfFormatter.amount(line.getLineNetAmount()),
                line.getVatType() == VatType.REVERSE_CHARGE);
    }

    private List<InvoicePdfView.VatRow> toVatRows(CreditNote creditNote) {
        List<VatSubtotal> perLine = new ArrayList<>(creditNote.getLines().size());
        for (CreditNoteLine line : creditNote.getLines()) {
            perLine.add(new VatSubtotal(line.getVatRate(), line.getLineNetAmount(), line.getLineVatAmount()));
        }

        return calculator.groupByRate(perLine).stream()
                .map(row -> new InvoicePdfView.VatRow(
                        "BTW " + PdfFormatter.rate(row.vatRate()),
                        PdfFormatter.amount(row.vatAmount())))
                .toList();
    }
}
