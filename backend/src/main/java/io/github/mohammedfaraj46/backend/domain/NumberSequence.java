package io.github.mohammedfaraj46.backend.domain;

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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Counter that hands out invoice and credit note numbers in a concurrency-safe
 * way. One row per (company, document type, year).
 *
 * <p>Assigning a number means locking this row and incrementing
 * {@link #lastValue} inside the same transaction, so two simultaneous sends can
 * never receive the same number. A deleted draft therefore consumes no number.
 */
@Entity
@Table(
        name = "number_sequence",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_number_sequence",
                columnNames = {"company_id", "document_type", "year"}
        )
)
public class NumberSequence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 20)
    private DocumentType documentType;

    /** Financial year the series belongs to. */
    @Column(name = "year", nullable = false)
    private int year;

    /** Last sequence number handed out; 0 means nothing issued yet. */
    @Column(name = "last_value", nullable = false)
    private int lastValue;

    protected NumberSequence() {
        // for JPA
    }

    public NumberSequence(Company company, DocumentType documentType, int year) {
        this.company = company;
        this.documentType = documentType;
        this.year = year;
        this.lastValue = 0;
    }

    /** Increments and returns the next sequence number. Caller holds the row lock. */
    public int next() {
        return ++lastValue;
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

    public DocumentType getDocumentType() {
        return documentType;
    }

    public void setDocumentType(DocumentType documentType) {
        this.documentType = documentType;
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    public int getLastValue() {
        return lastValue;
    }

    public void setLastValue(int lastValue) {
        this.lastValue = lastValue;
    }
}
