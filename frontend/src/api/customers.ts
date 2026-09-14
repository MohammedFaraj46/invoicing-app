import type { Numeric } from '../lib/money'
import { request } from './client'
import type { InvoiceSummary } from './invoices'

export type Customer = {
  id: number
  name: string
  email: string
  vatNumber: string | null
  addressLine: string
  postalCode: string
  city: string
  country: string
}

/** What the create and update endpoints accept. */
export type CustomerInput = {
  name: string
  email: string
  vatNumber?: string | null
  addressLine: string
  postalCode: string
  city: string
  country: string
}

export function listCustomers(): Promise<Customer[]> {
  return request<Customer[]>('/customers')
}

export function getCustomer(id: number): Promise<Customer> {
  return request<Customer>(`/customers/${id}`)
}

export function createCustomer(input: CustomerInput): Promise<Customer> {
  return request<Customer>('/customers', { method: 'POST', body: input })
}

export function updateCustomer(id: number, input: CustomerInput): Promise<Customer> {
  return request<Customer>(`/customers/${id}`, { method: 'PUT', body: input })
}

export function deleteCustomer(id: number): Promise<void> {
  return request<void>(`/customers/${id}`, { method: 'DELETE' })
}

/**
 * A customer's invoices, plus the two figures over exactly that list. Nested
 * under the customer, so a customer of another company answers 404 rather than
 * an empty list.
 */
export type CustomerInvoices = {
  /** Everything with an invoice number; drafts are not invoiced yet. */
  totalInvoiced: Numeric
  outstandingAmount: Numeric
  invoices: InvoiceSummary[]
}

export function getCustomerInvoices(id: number): Promise<CustomerInvoices> {
  return request<CustomerInvoices>(`/customers/${id}/invoices`)
}
