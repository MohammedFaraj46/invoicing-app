import { request, requestBlob } from './client'
import type { VatType } from './invoices'

export type CreditNoteStatus = 'DRAFT' | 'SENT'

export type CreditNoteLine = {
  id: number
  position: number
  invoiceLineId: number
  description: string
  quantity: string
  unitPrice: string
  vatRate: string
  vatType: VatType
  lineNetAmount: string
  lineVatAmount: string
  lineTotal: string
}

export type VatSubtotal = {
  vatRate: string
  netAmount: string
  vatAmount: string
}

export type CreditNote = {
  id: number
  creditNoteNumber: string | null
  status: CreditNoteStatus
  creditDate: string | null
  originalInvoiceId: number
  originalInvoiceNumber: string | null
  customerId: number
  customerName: string
  subtotalAmount: string
  vatAmount: string
  totalAmount: string
  reason: string | null
  lines: CreditNoteLine[]
  vatBreakdown: VatSubtotal[]
}

export type CreditNoteSummary = {
  id: number
  creditNoteNumber: string | null
  status: CreditNoteStatus
  creditDate: string | null
  originalInvoiceId: number
  originalInvoiceNumber: string | null
  customerId: number
  customerName: string
  totalAmount: string
}

/** Per invoice line: what was billed, what is credited, what is left. */
export type CreditStatusLine = {
  invoiceLineId: number
  position: number
  description: string
  vatRate: string
  vatType: VatType
  netAmount: string
  creditedAmount: string
  remainingAmount: string
}

export type InvoiceCreditStatus = {
  invoiceId: number
  invoiceNumber: string | null
  lines: CreditStatusLine[]
  creditNotes: CreditNoteSummary[]
}

export type CreditNoteLineInput = {
  invoiceLineId: number
  netAmount: string
}

export type CreditNoteInput = {
  originalInvoiceId: number
  reason?: string | null
  lines: CreditNoteLineInput[]
}

/** What is left to credit on an invoice, plus its existing credit notes. */
export function getInvoiceCreditStatus(invoiceId: number): Promise<InvoiceCreditStatus> {
  return request<InvoiceCreditStatus>(`/invoices/${invoiceId}/credit-status`)
}

export function getCreditNote(id: number): Promise<CreditNote> {
  return request<CreditNote>(`/credit-notes/${id}`)
}

export function createCreditNote(input: CreditNoteInput): Promise<CreditNote> {
  return request<CreditNote>('/credit-notes', { method: 'POST', body: input })
}

/** DRAFT to SENT: the backend assigns a number from the credit note series. */
export function sendCreditNote(id: number): Promise<CreditNote> {
  return request<CreditNote>(`/credit-notes/${id}/send`, { method: 'POST' })
}

export function deleteCreditNote(id: number): Promise<void> {
  return request<void>(`/credit-notes/${id}`, { method: 'DELETE' })
}

export function downloadCreditNotePdf(id: number): Promise<Blob> {
  return requestBlob(`/credit-notes/${id}/pdf`)
}
