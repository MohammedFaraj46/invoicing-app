package io.github.mohammedfaraj46.backend.domain;

/**
 * Which numbering series a document draws from. Mirrors
 * ck_number_sequence_document_type in V1__init.sql.
 */
public enum DocumentType {
    INVOICE,
    CREDIT_NOTE
}
