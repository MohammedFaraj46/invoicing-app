package io.github.mohammedfaraj46.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.math.BigDecimal;

/**
 * Fields shared by {@link InvoiceLine} and {@link CreditNoteLine}.
 *
 * <p>These stay two separate tables on purpose, so each line table can carry one
 * clean, enforceable foreign key to its own header; this superclass only removes
 * the Java-side duplication.
 *
 * <p>{@code vatRate} and {@code vatType} are a historical snapshot of what applied
 * when the document was created, and the three amount fields are a snapshot of
 * what the InvoiceCalculator computed. Neither is ever derived live.
 */
@MappedSuperclass
public abstract class DocumentLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Ordering of the lines within one document, 1-based. */
    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "description", nullable = false, length = 500)
    private String description;

    /** Supports fractional amounts, e.g. 0.5 hour. Always positive. */
    @Column(name = "quantity", nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    /** Price per unit. Always positive. */
    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    /** Applied percentage, e.g. 21.00. */
    @Column(name = "vat_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal vatRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "vat_type", nullable = false, length = 20)
    private VatType vatType;

    /** quantity x unitPrice, negative on a credit note. */
    @Column(name = "line_net_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal lineNetAmount;

    /** lineNetAmount x vatRate, negative on a credit note. */
    @Column(name = "line_vat_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal lineVatAmount;

    /** lineNetAmount + lineVatAmount, negative on a credit note. */
    @Column(name = "line_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal lineTotal;

    protected DocumentLine() {
        // for JPA
    }

    protected DocumentLine(int position, String description, BigDecimal quantity,
                           BigDecimal unitPrice, BigDecimal vatRate, VatType vatType) {
        this.position = position;
        this.description = description;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.vatRate = vatRate;
        this.vatType = vatType;
    }

    public Long getId() {
        return id;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public BigDecimal getVatRate() {
        return vatRate;
    }

    public void setVatRate(BigDecimal vatRate) {
        this.vatRate = vatRate;
    }

    public VatType getVatType() {
        return vatType;
    }

    public void setVatType(VatType vatType) {
        this.vatType = vatType;
    }

    public BigDecimal getLineNetAmount() {
        return lineNetAmount;
    }

    public void setLineNetAmount(BigDecimal lineNetAmount) {
        this.lineNetAmount = lineNetAmount;
    }

    public BigDecimal getLineVatAmount() {
        return lineVatAmount;
    }

    public void setLineVatAmount(BigDecimal lineVatAmount) {
        this.lineVatAmount = lineVatAmount;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }

    public void setLineTotal(BigDecimal lineTotal) {
        this.lineTotal = lineTotal;
    }
}
