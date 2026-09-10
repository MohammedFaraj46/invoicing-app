package io.github.mohammedfaraj46.backend.domain;

/**
 * VAT category of a single line. Mirrors ck_invoice_line_vat_type and
 * ck_credit_note_line_vat_type in V1__init.sql.
 *
 * <p>Deliberately carries no percentage: the applied rate is stored per line
 * as a historical snapshot ({@code vat_rate}), so an existing document never
 * changes when the current rates do.
 */
public enum VatType {
    STANDARD,
    REDUCED,
    ZERO,
    REVERSE_CHARGE
}
