package io.github.mohammedfaraj46.backend.service.pdf;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Renders the invoice template to PDF bytes.
 *
 * <p>Knows nothing about invoices: it takes a view model, fills the template and
 * hands back a document. Restyling the invoice means editing
 * {@code templates/pdf/invoice.html} and nothing here.
 */
@Component
public class InvoicePdfRenderer {

    private static final String TEMPLATE = "pdf/invoice";

    private final TemplateEngine templateEngine;

    public InvoicePdfRenderer(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    public byte[] render(InvoicePdfView view) {
        Context context = new Context();
        context.setVariable("invoice", view);

        String html = templateEngine.process(TEMPLATE, context);

        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            // the template has no external references, so the base URI is unused;
            // passing null keeps the renderer from resolving anything from disk
            builder.withHtmlContent(html, null);
            builder.toStream(output);
            builder.run();
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not render the invoice PDF", exception);
        }
    }
}
