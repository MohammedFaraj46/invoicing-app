-- V2: link every credit note line back to the invoice line it credits.
--
-- Crediting is validated per original line: a line may never be credited for
-- more than its own net amount, across all credit notes together. That sum can
-- only be computed if each credit note line knows which invoice line it came
-- from, which V1 had no way to express.
--
-- NOT NULL is safe here: credit notes had no endpoints before this migration,
-- so the table is empty. Were it not, this would fail loudly rather than leave
-- half-linked rows behind — which is the outcome you want.
ALTER TABLE credit_note_line
    ADD COLUMN invoice_line_id bigint NOT NULL;

ALTER TABLE credit_note_line
    ADD CONSTRAINT fk_credit_note_line_invoice_line
        FOREIGN KEY (invoice_line_id) REFERENCES invoice_line (id) ON DELETE RESTRICT;

-- the over-credit check reads every credit note line for one invoice, grouped
-- by the line it credits
CREATE INDEX ix_credit_note_line_invoice_line ON credit_note_line (invoice_line_id);
