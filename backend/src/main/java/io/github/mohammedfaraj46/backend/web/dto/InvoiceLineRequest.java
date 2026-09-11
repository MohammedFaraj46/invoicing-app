package io.github.mohammedfaraj46.backend.web.dto;

import io.github.mohammedfaraj46.backend.domain.VatType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * One line of an invoice as it comes in.
 *
 * <p>The caller sends what was agreed — description, quantity, unit price, VAT
 * rate and type. The amounts are never accepted from the client: the
 * InvoiceCalculator works those out.
 */
public record InvoiceLineRequest(

        @NotBlank(message = "description is required")
        @Size(max = 500, message = "description must be at most 500 characters")
        String description,

        @NotNull(message = "quantity is required")
        @DecimalMin(value = "0.001", message = "quantity must be greater than zero")
        @Digits(integer = 9, fraction = 3, message = "quantity may have at most 3 decimals")
        BigDecimal quantity,

        @NotNull(message = "unitPrice is required")
        @DecimalMin(value = "0.00", message = "unitPrice cannot be negative")
        @Digits(integer = 10, fraction = 2, message = "unitPrice may have at most 2 decimals")
        BigDecimal unitPrice,

        @NotNull(message = "vatRate is required")
        @DecimalMin(value = "0.00", message = "vatRate cannot be negative")
        @DecimalMax(value = "100.00", message = "vatRate cannot exceed 100")
        @Digits(integer = 3, fraction = 2, message = "vatRate may have at most 2 decimals")
        BigDecimal vatRate,

        @NotNull(message = "vatType is required")
        VatType vatType
) {
}
