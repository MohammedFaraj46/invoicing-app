import { request } from './client'

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
