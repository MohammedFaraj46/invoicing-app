import type { Numeric } from '../lib/money'
import { request } from './client'

/**
 * The signed-in user's own company. Neither endpoint takes an id: which company
 * is meant follows from the login cookie.
 */
export type Company = {
  id: number
  name: string
  kvkNumber: string
  vatNumber: string
  addressLine: string
  postalCode: string
  city: string
  country: string
  defaultPaymentTermDays: Numeric
  logoUrl: string | null
}

/** What PUT /companies/me accepts; logoUrl is not editable here. */
export type CompanyInput = {
  name: string
  kvkNumber: string
  vatNumber: string
  addressLine: string
  postalCode: string
  city: string
  country: string
  defaultPaymentTermDays: number
}

export function getCompany(): Promise<Company> {
  return request<Company>('/companies/me')
}

export function updateCompany(input: CompanyInput): Promise<Company> {
  return request<Company>('/companies/me', { method: 'PUT', body: input })
}
