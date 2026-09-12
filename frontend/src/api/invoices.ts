import { request, requestBlob } from './client'

export type InvoiceStatus = 'DRAFT' | 'SENT' | 'PAID' | 'OVERDUE'

export type VatType = 'STANDARD' | 'REDUCED' | 'ZERO' | 'REVERSE_CHARGE'

export type InvoiceLine = {
  id: number
  position: number
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

export type Invoice = {
  id: number
  invoiceNumber: string | null
  status: InvoiceStatus
  invoiceDate: string | null
  dueDate: string | null
  paymentTermDays: number
  customerId: number
  customerName: string
  subtotalAmount: string
  vatAmount: string
  totalAmount: string
  currency: string
  notes: string | null
  lines: InvoiceLine[]
  vatBreakdown: VatSubtotal[]
}

export type InvoiceSummary = {
  id: number
  invoiceNumber: string | null
  status: InvoiceStatus
  invoiceDate: string | null
  dueDate: string | null
  customerId: number
  customerName: string
  totalAmount: string
  currency: string
}

export type InvoiceLineInput = {
  description: string
  quantity: string
  unitPrice: string
  vatRate: string
  vatType: VatType
}

export type InvoiceInput = {
  customerId: number
  paymentTermDays?: number | null
  notes?: string | null
  lines: InvoiceLineInput[]
}

export function listInvoices(): Promise<InvoiceSummary[]> {
  return request<InvoiceSummary[]>('/invoices')
}

export function getInvoice(id: number): Promise<Invoice> {
  return request<Invoice>(`/invoices/${id}`)
}

export function createInvoice(input: InvoiceInput): Promise<Invoice> {
  return request<Invoice>('/invoices', { method: 'POST', body: input })
}

export function updateInvoice(id: number, input: InvoiceInput): Promise<Invoice> {
  return request<Invoice>(`/invoices/${id}`, { method: 'PUT', body: input })
}

export function deleteInvoice(id: number): Promise<void> {
  return request<void>(`/invoices/${id}`, { method: 'DELETE' })
}

/** DRAFT to SENT: the backend assigns the number and the dates. */
export function sendInvoice(id: number): Promise<Invoice> {
  return request<Invoice>(`/invoices/${id}/send`, { method: 'POST' })
}

/** The invoice as a PDF. Only available once the invoice has a number. */
export function downloadInvoicePdf(id: number): Promise<Blob> {
  return requestBlob(`/invoices/${id}/pdf`)
}

/** SENT to PAID. */
export function payInvoice(id: number): Promise<Invoice> {
  return request<Invoice>(`/invoices/${id}/pay`, { method: 'POST' })
}
