package io.github.mohammedfaraj46.backend.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * A line on an invoice. All shared fields live in {@link DocumentLine}.
 */
@Entity
@Table(name = "invoice_line")
public class InvoiceLine extends DocumentLine {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    protected InvoiceLine() {
        // for JPA
    }

    public InvoiceLine(int position, String description, BigDecimal quantity,
                       BigDecimal unitPrice, BigDecimal vatRate, VatType vatType) {
        super(position, description, quantity, unitPrice, vatRate, vatType);
    }

    public Invoice getInvoice() {
        return invoice;
    }

    public void setInvoice(Invoice invoice) {
        this.invoice = invoice;
    }
}
