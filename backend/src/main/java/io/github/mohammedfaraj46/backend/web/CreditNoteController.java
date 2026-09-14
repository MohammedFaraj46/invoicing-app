package io.github.mohammedfaraj46.backend.web;

import io.github.mohammedfaraj46.backend.service.CreditNoteService;
import io.github.mohammedfaraj46.backend.service.CreditNoteService.CreditNotePdf;
import io.github.mohammedfaraj46.backend.web.dto.CreditNoteRequest;
import io.github.mohammedfaraj46.backend.web.dto.CreditNoteResponse;
import io.github.mohammedfaraj46.backend.web.dto.CreditNoteSummaryResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Credit notes. Same shape as the invoice routes: ids resolve within the
 * signed-in company, drafts are editable and sent documents are not.
 */
@RestController
@RequestMapping("/api/credit-notes")
public class CreditNoteController {

    private final CreditNoteService creditNoteService;

    public CreditNoteController(CreditNoteService creditNoteService) {
        this.creditNoteService = creditNoteService;
    }

    /** Creates a DRAFT against a sent invoice: no number and no date yet. */
    @PostMapping
    public ResponseEntity<CreditNoteResponse> create(@Valid @RequestBody CreditNoteRequest request) {
        CreditNoteResponse created = creditNoteService.create(request);
        return ResponseEntity.created(URI.create("/api/credit-notes/" + created.id())).body(created);
    }

    @GetMapping
    public List<CreditNoteSummaryResponse> findAll() {
        return creditNoteService.findAll();
    }

    @GetMapping("/{id}")
    public CreditNoteResponse findById(@PathVariable Long id) {
        return creditNoteService.findById(id);
    }

    /** DRAFT to SENT: assigns a number from the credit note series. */
    @PostMapping("/{id}/send")
    public CreditNoteResponse send(@PathVariable Long id) {
        return creditNoteService.send(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        creditNoteService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** The credit note as a PDF. 409 for a draft, which has no number yet. */
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        CreditNotePdf pdf = creditNoteService.generatePdf(id);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(pdf.fileName()).build().toString())
                .body(pdf.content());
    }
}
