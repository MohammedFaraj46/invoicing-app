import { request } from './client'

export type CurrentUser = {
  id: number
  email: string
  companyId: number
}

export type CompanyDetails = {
  name: string
  kvkNumber: string
  vatNumber: string
  addressLine: string
  postalCode: string
  city: string
  country: string
}

export type RegisterPayload = {
  email: string
  password: string
  company: CompanyDetails
}

export type RegisterResult = {
  userId: number
  email: string
  companyId: number
  companyName: string
}

export function login(email: string, password: string): Promise<CurrentUser> {
  return request<CurrentUser>('/auth/login', {
    method: 'POST',
    body: { email, password },
  })
}

export function logout(): Promise<void> {
  return request<void>('/auth/logout', { method: 'POST' })
}

export function register(payload: RegisterPayload): Promise<RegisterResult> {
  return request<RegisterResult>('/auth/register', { method: 'POST', body: payload })
}

export function getCurrentUser(): Promise<CurrentUser> {
  return request<CurrentUser>('/auth/me')
}
