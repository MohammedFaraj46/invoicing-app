package io.github.mohammedfaraj46.backend.service.pdf;

import java.util.List;

/**
 * Everything the credit note template needs, already formatted.
 *
 * <p>Same shape as {@link InvoicePdfView}, with the reference to the invoice
 * being corrected and negative amounts throughout. Reuses the invoice's sender
 * and recipient records: those parties are identical on both documents.
 */
public record CreditNotePdfView(
        InvoicePdfView.Sender sender,
        InvoicePdfView.Recipient recipient,
        String creditNoteNumber,
        String creditDate,
        String originalInvoiceNumber,
        String originalInvoiceDate,
        List<Line> lines,
        List<InvoicePdfView.VatRow> vatRows,
        String subtotal,
        String total,
        String reason
) {

    /** One credited line. The amount is negative; the unit price is not. */
    public record Line(
            String description,
            String quantity,
            String unitPrice,
            String vatRate,
            String lineNetAmount,
            boolean reverseCharge
    ) {
    }
}
