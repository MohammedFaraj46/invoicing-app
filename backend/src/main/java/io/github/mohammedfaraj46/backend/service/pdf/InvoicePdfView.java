package io.github.mohammedfaraj46.backend.service.pdf;

import java.util.List;

/**
 * Everything the invoice template needs, already formatted.
 *
 * <p>Deliberately not the JPA entities: the template gets plain strings and no
 * domain objects, so it holds no logic of its own and cannot accidentally
 * trigger a lazy load or reformat an amount differently than the rest of the
 * application does. Money and dates are turned into text once, in
 * {@link PdfFormatter}.
 */
public record InvoicePdfView(
        Sender sender,
        Recipient recipient,
        String invoiceNumber,
        String invoiceDate,
        String dueDate,
        List<Line> lines,
        List<VatRow> vatRows,
        String subtotal,
        String total,
        String notes,
        boolean hasReverseCharge
) {

    /** The freelancer's own details, as they appear at the top of the invoice. */
    public record Sender(
            String name,
            String addressLine,
            String postalCode,
            String city,
            String country,
            String kvkNumber,
            String vatNumber
    ) {
    }

    public record Recipient(
            String name,
            String addressLine,
            String postalCode,
            String city,
            String country,
            String vatNumber
    ) {
    }

    public record Line(
            String description,
            String quantity,
            String unitPrice,
            String vatRate,
            String lineNetAmount,
            boolean reverseCharge
    ) {
    }

    /** One row of the VAT breakdown, e.g. "21%" over 559,97 is 117,59. */
    public record VatRow(String label, String amount) {
    }
}
