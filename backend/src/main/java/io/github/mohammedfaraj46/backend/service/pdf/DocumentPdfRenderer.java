package io.github.mohammedfaraj46.backend.service.pdf;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Renders a Thymeleaf template to PDF bytes.
 *
 * <p>Knows nothing about invoices or credit notes: it takes a template name and
 * a view model. Restyling a document means editing its template under
 * {@code templates/pdf/} and nothing here.
 *
 * <h2>The font travels with the application</h2>
 *
 * DejaVu Sans ships in {@code src/main/resources/fonts} and is registered here,
 * so the renderer never asks the operating system for a typeface. That is the
 * difference between a document that happens to look right on the machine that
 * made it and one that looks the same everywhere.
 *
 * <p>The alternative — installing a font package in the image — was rejected.
 * It makes the output depend on which fonts the base image happens to carry,
 * which changes when the base image is bumped, and it leaves a developer running
 * the app outside Docker on a different font again. Shipping the file means the
 * jar is self-contained: the same bytes render the same invoice on a laptop, in
 * the container and in CI.
 *
 * <p>The glyphs are subsetted into each PDF, so a document carries only the
 * characters it actually uses and stays a few kilobytes rather than a megabyte.
 * DejaVu covers the euro sign and the Latin-1 range an invoice needs; its
 * licence (Bitstream Vera, see {@code fonts/LICENSE.txt}) allows redistribution.
 */
@Component
public class DocumentPdfRenderer {

    /**
     * The family name the templates ask for in their {@code font-family}. It
     * has to match, or the renderer quietly falls back to a built-in font again.
     */
    static final String FONT_FAMILY = "DejaVu Sans";

    private static final String REGULAR = "/fonts/DejaVuSans.ttf";
    private static final String BOLD = "/fonts/DejaVuSans-Bold.ttf";

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
            registerFonts(builder);
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

    /**
     * Registers both weights under one family, so {@code font-weight: bold} in a
     * template picks the bold file instead of having the renderer fake it by
     * smearing the regular one.
     *
     * <p>Each supplier opens a fresh stream: the builder may read a font more
     * than once, and a stream that has already been consumed yields a font with
     * no glyphs at all.
     */
    private void registerFonts(PdfRendererBuilder builder) {
        builder.useFont(() -> open(REGULAR), FONT_FAMILY, 400, FontStyle.NORMAL, true);
        builder.useFont(() -> open(BOLD), FONT_FAMILY, 700, FontStyle.NORMAL, true);
    }

    private InputStream open(String resource) {
        InputStream stream = DocumentPdfRenderer.class.getResourceAsStream(resource);
        if (stream == null) {
            // a packaging mistake, not a runtime condition: fail loudly rather
            // than silently producing documents in a substituted font
            throw new IllegalStateException("Font " + resource + " is missing from the classpath");
        }
        return stream;
    }
}
