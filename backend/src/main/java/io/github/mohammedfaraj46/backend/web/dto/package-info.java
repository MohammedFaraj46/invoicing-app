/**
 * Request and response payloads.
 *
 * <h2>Text is normalised before it is validated</h2>
 *
 * Request records strip their text fields in the compact constructor, so the
 * value that bean validation sees, and the value that gets stored, are the same
 * one. {@code "  Faraj  "} is stored as {@code "Faraj"}, {@code "   "} is
 * rejected as blank, and a {@code @Size} limit measures the text rather than the
 * padding around it. Services therefore do not trim; by the time a request
 * reaches one, it already has.
 *
 * <h2>Every text field has a maximum that matches its column</h2>
 *
 * The limits in {@code @Size} mirror the {@code varchar} lengths in the
 * migration. Over-long input is meant to come back as a 400 naming the field,
 * not as a 500 from the database rejecting the insert.
 *
 * <h2>Identifiers and postcodes are checked for length, never for format</h2>
 *
 * A KvK number, a VAT number and a postal code are required where the domain
 * requires them, are stripped, and must fit their column — and that is all.
 * There is deliberately no Dutch pattern on any of them.
 *
 * <p>The app is NL-first but the data model is meant to grow into the EU, and a
 * format rule is the kind of thing that quietly blocks that: a Belgian postcode
 * is four digits, an Irish Eircode mixes letters and digits, and a company
 * registration number has a different shape in every member state. A regex that
 * fits the Netherlands today turns into a bug report from the first foreign
 * customer. Rejecting input we cannot actually judge buys nothing here — the
 * value is printed on an invoice, not computed with.
 *
 * <p>{@code country} is the one exception, and it is not country-specific
 * either: two letters, because it is an ISO 3166-1 alpha-2 code.
 */
package io.github.mohammedfaraj46.backend.web.dto;
