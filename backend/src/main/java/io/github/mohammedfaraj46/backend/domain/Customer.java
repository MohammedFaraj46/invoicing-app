package io.github.mohammedfaraj46.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A customer of the freelancer. Always belongs to exactly one {@link Company},
 * which is what ownership checks filter on.
 */
@Entity
@Table(name = "customer")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    /** Needed for reverse-charge VAT and VIES lookups; optional otherwise. */
    @Column(name = "vat_number", length = 20)
    private String vatNumber;

    @Column(name = "address_line", nullable = false, length = 255)
    private String addressLine;

    @Column(name = "postal_code", nullable = false, length = 20)
    private String postalCode;

    @Column(name = "city", nullable = false, length = 100)
    private String city;

    /** ISO 3166-1 alpha-2 country code. */
    @Column(name = "country", nullable = false, length = 2)
    private String country;

    protected Customer() {
        // for JPA
    }

    public Customer(Company company, String name, String email, String addressLine,
                    String postalCode, String city, String country) {
        this.company = company;
        this.name = name;
        this.email = email;
        this.addressLine = addressLine;
        this.postalCode = postalCode;
        this.city = city;
        this.country = country;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
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
}
