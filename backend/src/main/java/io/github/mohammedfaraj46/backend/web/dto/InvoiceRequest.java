package io.github.mohammedfaraj46.backend.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Payload for creating and updating a draft invoice.
 *
 * <p>Carries no invoice number, invoice date or amounts: the number and date are
 * assigned when the invoice is sent, and the amounts are calculated server-side.
 *
 * @param customerId       must be one of the signed-in company's own customers
 * @param paymentTermDays  optional; falls back to the company's default
 * @param lines            at least one — an invoice without lines is not an invoice
 */
public record InvoiceRequest(

        @NotNull(message = "customerId is required")
        Long customerId,

        @Positive(message = "paymentTermDays must be greater than zero")
        Integer paymentTermDays,

        @Size(max = 1000, message = "notes must be at most 1000 characters")
        String notes,

        @NotEmpty(message = "at least one line is required")
        @Valid
        List<InvoiceLineRequest> lines
) {
}
