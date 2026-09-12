package io.github.mohammedfaraj46.backend.service.pdf;

import io.github.mohammedfaraj46.backend.domain.Company;
import io.github.mohammedfaraj46.backend.domain.Customer;
import io.github.mohammedfaraj46.backend.domain.Invoice;
import io.github.mohammedfaraj46.backend.domain.InvoiceLine;
import io.github.mohammedfaraj46.backend.domain.VatType;
import io.github.mohammedfaraj46.backend.service.calculation.InvoiceCalculator;
import io.github.mohammedfaraj46.backend.service.calculation.VatSubtotal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Flattens a stored invoice into the strings the template prints.
 *
 * <p>The VAT breakdown is rebuilt by grouping the amounts that were saved, not
 * by recalculating them — a sent invoice must keep showing its own snapshot,
 * whatever the calculator would say today.
 */
@Component
public class InvoicePdfViewMapper {

    private final InvoiceCalculator calculator;

    public InvoicePdfViewMapper(InvoiceCalculator calculator) {
        this.calculator = calculator;
    }

    public InvoicePdfView toView(Invoice invoice) {
        Company company = invoice.getCompany();
        Customer customer = invoice.getCustomer();

        List<InvoicePdfView.Line> lines = invoice.getLines().stream()
                .map(InvoicePdfViewMapper::toLine)
                .toList();

        boolean hasReverseCharge = invoice.getLines().stream()
                .anyMatch(line -> line.getVatType() == VatType.REVERSE_CHARGE);

        return new InvoicePdfView(
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
                invoice.getInvoiceNumber(),
                PdfFormatter.date(invoice.getInvoiceDate()),
                PdfFormatter.date(invoice.getDueDate()),
                lines,
                toVatRows(invoice),
                PdfFormatter.amount(invoice.getSubtotalAmount()),
                PdfFormatter.amount(invoice.getTotalAmount()),
                invoice.getNotes(),
                hasReverseCharge);
    }

    private static InvoicePdfView.Line toLine(InvoiceLine line) {
        return new InvoicePdfView.Line(
                line.getDescription(),
                PdfFormatter.quantity(line.getQuantity()),
                PdfFormatter.amount(line.getUnitPrice()),
                PdfFormatter.rate(line.getVatRate()),
                PdfFormatter.amount(line.getLineNetAmount()),
                line.getVatType() == VatType.REVERSE_CHARGE);
    }

    private List<InvoicePdfView.VatRow> toVatRows(Invoice invoice) {
        List<VatSubtotal> perLine = new ArrayList<>(invoice.getLines().size());
        for (InvoiceLine line : invoice.getLines()) {
            perLine.add(new VatSubtotal(line.getVatRate(), line.getLineNetAmount(), line.getLineVatAmount()));
        }

        return calculator.groupByRate(perLine).stream()
                .map(row -> new InvoicePdfView.VatRow(
                        "BTW " + PdfFormatter.rate(row.vatRate()),
                        PdfFormatter.amount(row.vatAmount())))
                .toList();
    }
}
