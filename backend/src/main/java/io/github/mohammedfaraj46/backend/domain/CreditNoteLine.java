package io.github.mohammedfaraj46.backend.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * A line on a credit note. All shared fields live in {@link DocumentLine}.
 *
 * <p>Quantity and unit price stay positive so the line reads naturally
 * ("1 x EUR 100"); the computed amounts are stored negative. The
 * InvoiceCalculator applies that sign.
 */
@Entity
@Table(name = "credit_note_line")
public class CreditNoteLine extends DocumentLine {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "credit_note_id", nullable = false)
    private CreditNote creditNote;

    /**
     * The invoice line this one credits. Without it there is no way to tell how
     * much of a given line has already been credited across several credit
     * notes, which is what the over-credit check needs.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_line_id", nullable = false)
    private InvoiceLine invoiceLine;

    protected CreditNoteLine() {
        // for JPA
    }

    public CreditNoteLine(int position, String description, BigDecimal quantity,
                          BigDecimal unitPrice, BigDecimal vatRate, VatType vatType) {
        super(position, description, quantity, unitPrice, vatRate, vatType);
    }

    public InvoiceLine getInvoiceLine() {
        return invoiceLine;
    }

    public void setInvoiceLine(InvoiceLine invoiceLine) {
        this.invoiceLine = invoiceLine;
    }

    public CreditNote getCreditNote() {
        return creditNote;
    }

    public void setCreditNote(CreditNote creditNote) {
        this.creditNote = creditNote;
    }
}
