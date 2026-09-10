package io.github.mohammedfaraj46.backend.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;

/**
 * Business profile of the freelancer; appears as the sender on every document.
 * Owns the one-to-one to {@link AppUser} through the unique user_id column, and
 * is the ownership root that every query filters on.
 */
@Entity
@Table(name = "company")
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private AppUser user;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "kvk_number", nullable = false, length = 20)
    private String kvkNumber;

    @Column(name = "vat_number", nullable = false, length = 20)
    private String vatNumber;

    @Column(name = "address_line", nullable = false, length = 255)
    private String addressLine;

    @Column(name = "postal_code", nullable = false, length = 20)
    private String postalCode;

    @Column(name = "city", nullable = false, length = 100)
    private String city;

    /** ISO 3166-1 alpha-2 country code. */
    @Column(name = "country", nullable = false, length = 2)
    private String country = "NL";

    @Column(name = "default_payment_term_days", nullable = false)
    private int defaultPaymentTermDays = 30;

    @Column(name = "logo_url", length = 512)
    private String logoUrl;

    @OneToMany(mappedBy = "company", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Customer> customers = new ArrayList<>();

    @OneToMany(mappedBy = "company")
    private List<Invoice> invoices = new ArrayList<>();

    @OneToMany(mappedBy = "company")
    private List<CreditNote> creditNotes = new ArrayList<>();

    @OneToMany(mappedBy = "company", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<NumberSequence> numberSequences = new ArrayList<>();

    protected Company() {
        // for JPA
    }

    public Company(AppUser user, String name, String kvkNumber, String vatNumber,
                   String addressLine, String postalCode, String city, String country) {
        this.user = user;
        this.name = name;
        this.kvkNumber = kvkNumber;
        this.vatNumber = vatNumber;
        this.addressLine = addressLine;
        this.postalCode = postalCode;
        this.city = city;
        this.country = country;
    }

    public void addCustomer(Customer customer) {
        customers.add(customer);
        customer.setCompany(this);
    }

    public void removeCustomer(Customer customer) {
        customers.remove(customer);
        customer.setCompany(null);
    }

    public Long getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public void setUser(AppUser user) {
        this.user = user;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getKvkNumber() {
        return kvkNumber;
    }

    public void setKvkNumber(String kvkNumber) {
        this.kvkNumber = kvkNumber;
    }

    public String getVatNumber() {
        return vatNumber;
    }

    public void setVatNumber(String vatNumber) {
        this.vatNumber = vatNumber;
    }

    public String getAddressLine() {
        return addressLine;
    }

    public void setAddressLine(String addressLine) {
        this.addressLine = addressLine;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public int getDefaultPaymentTermDays() {
        return defaultPaymentTermDays;
    }

    public void setDefaultPaymentTermDays(int defaultPaymentTermDays) {
        this.defaultPaymentTermDays = defaultPaymentTermDays;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public List<Customer> getCustomers() {
        return customers;
    }

    public List<Invoice> getInvoices() {
        return invoices;
    }

    public List<CreditNote> getCreditNotes() {
        return creditNotes;
    }

    public List<NumberSequence> getNumberSequences() {
        return numberSequences;
    }
}
