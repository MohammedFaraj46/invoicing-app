package io.github.mohammedfaraj46.backend.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A credit note that (partially) credits an existing sent invoice. Always points
 * at the original invoice and draws its number from its own series
 * ({@code DocumentType.CREDIT_NOTE}).
 *
 * <p>The amounts are stored negative, so an invoice of +121 and a credit note of
 * -40 simply add up to the 81 that is still outstanding.
 */
@Entity
@Table(name = "credit_note")
public class CreditNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    /** The invoice being credited. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "original_invoice_id", nullable = false)
    private Invoice originalInvoice;

    /** Null while DRAFT; assigned on send from the credit note series. */
    @Column(name = "credit_note_number", length = 50)
    private String creditNoteNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CreditNoteStatus status = CreditNoteStatus.DRAFT;

    /** Null while DRAFT; assigned on send. */
    @Column(name = "credit_date")
    private LocalDate creditDate;

    @Column(name = "subtotal_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotalAmount = BigDecimal.ZERO;

    @Column(name = "vat_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal vatAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "reason", length = 1000)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "creditNote", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<CreditNoteLine> lines = new ArrayList<>();

    protected CreditNote() {
        // for JPA
    }

    public CreditNote(Company company, Customer customer, Invoice originalInvoice) {
        this.company = company;
        this.customer = customer;
        this.originalInvoice = originalInvoice;
    }

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public void addLine(CreditNoteLine line) {
        lines.add(line);
        line.setCreditNote(this);
    }

    public void removeLine(CreditNoteLine line) {
        lines.remove(line);
        line.setCreditNote(null);
    }

    public Long getId() {
        return id;
    }

    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public Invoice getOriginalInvoice() {
        return originalInvoice;
    }

    public void setOriginalInvoice(Invoice originalInvoice) {
        this.originalInvoice = originalInvoice;
    }

    public String getCreditNoteNumber() {
        return creditNoteNumber;
    }

    public void setCreditNoteNumber(String creditNoteNumber) {
        this.creditNoteNumber = creditNoteNumber;
    }

    public CreditNoteStatus getStatus() {
        return status;
    }

    public void setStatus(CreditNoteStatus status) {
        this.status = status;
    }

    public LocalDate getCreditDate() {
        return creditDate;
    }

    public void setCreditDate(LocalDate creditDate) {
        this.creditDate = creditDate;
    }

    public BigDecimal getSubtotalAmount() {
        return subtotalAmount;
    }

    public void setSubtotalAmount(BigDecimal subtotalAmount) {
        this.subtotalAmount = subtotalAmount;
    }

    public BigDecimal getVatAmount() {
        return vatAmount;
    }

    public void setVatAmount(BigDecimal vatAmount) {
        this.vatAmount = vatAmount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public List<CreditNoteLine> getLines() {
        return lines;
    }
}
