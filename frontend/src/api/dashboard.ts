import type { Numeric } from '../lib/money'
import { request } from './client'
import type { InvoiceStatus } from './invoices'

/** One row in the "most recent invoices" list. */
export type DashboardInvoice = {
  id: number
  /** Null while the invoice is still a draft. */
  invoiceNumber: string | null
  status: InvoiceStatus
  customerName: string
  totalAmount: Numeric
  currency: string
  /** The invoice date once sent, otherwise the day the draft was last touched. */
  date: string
}

export type Dashboard = {
  outstandingAmount: Numeric
  paidThisMonth: Numeric
  overdueCount: number
  draftCount: number
  recentInvoices: DashboardInvoice[]
}

/** Every figure in one call, scoped to the signed-in user's own company. */
export function getDashboard(): Promise<Dashboard> {
  return request<Dashboard>('/dashboard')
}
