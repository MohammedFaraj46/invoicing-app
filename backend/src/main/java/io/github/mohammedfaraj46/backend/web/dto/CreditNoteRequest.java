package io.github.mohammedfaraj46.backend.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Payload for creating a draft credit note.
 *
 * @param originalInvoiceId the invoice being credited; must be sent or paid
 * @param lines             per invoice line the amount to credit; lines with 0 are skipped
 */
public record CreditNoteRequest(

        @NotNull(message = "originalInvoiceId is required")
        Long originalInvoiceId,

        @Size(max = 1000, message = "reason must be at most 1000 characters")
        String reason,

        @NotEmpty(message = "at least one line is required")
        // the annotation goes on the type argument, not the list: Hibernate
        // Validator deprecated the container form and warns about it on every
        // request that carries lines
        List<@Valid CreditNoteLineRequest> lines
) {
}
