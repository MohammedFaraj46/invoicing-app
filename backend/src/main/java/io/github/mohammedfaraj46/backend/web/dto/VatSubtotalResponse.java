package io.github.mohammedfaraj46.backend.web.dto;

import io.github.mohammedfaraj46.backend.service.calculation.VatSubtotal;
import java.math.BigDecimal;

/**
 * One row of the VAT breakdown, for the invoice detail and later the PDF.
 */
public record VatSubtotalResponse(
        BigDecimal vatRate,
        BigDecimal netAmount,
        BigDecimal vatAmount
) {

    public static VatSubtotalResponse from(VatSubtotal subtotal) {
        return new VatSubtotalResponse(subtotal.vatRate(), subtotal.netAmount(), subtotal.vatAmount());
    }
}
