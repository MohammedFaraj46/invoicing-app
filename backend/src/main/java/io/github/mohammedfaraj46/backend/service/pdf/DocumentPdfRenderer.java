package io.github.mohammedfaraj46.backend.service.pdf;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Renders a Thymeleaf template to PDF bytes.
 *
 * <p>Knows nothing about invoices or credit notes: it takes a template name and
 * a view model. Restyling a document means editing its template under
 * {@code templates/pdf/} and nothing here.
 */
@Component
public class DocumentPdfRenderer {

    private final TemplateEngine templateEngine;

    public DocumentPdfRenderer(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    /**
     * @param templateName  e.g. {@code pdf/invoice}
     * @param variableName  the name the template refers to the model by
     * @param model         the already-formatted view model
     */
    public byte[] render(String templateName, String variableName, Object model) {
        Context context = new Context();
        context.setVariable(variableName, model);

        String html = templateEngine.process(templateName, context);

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
            throw new IllegalStateException("Could not render the PDF for " + templateName, exception);
        }
    }
}
