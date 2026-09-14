/**
 * Form checks shared by the registration, customer and company forms.
 *
 * <p>These mirror the backend's bean validation rather than replacing it: the
 * server decides, this only saves a round trip and puts the message next to the
 * field while you are still typing. Where the two could drift — the maximum
 * lengths — they are written down once here, from the same varchar sizes the
 * backend's @Size annotations use.
 *
 * <p>Deliberately absent: any pattern for a postal code, a KvK number or a VAT
 * number. The app is NL-first but the data model is meant to grow into the EU,
 * and those formats differ per member state, so requiring a Dutch shape would
 * turn into a bug the first time a foreign address is entered. Required and a
 * generous maximum, nothing more.
 */

/** The varchar lengths from V1__init.sql, in one place. */
export const LIMITS = {
  name: 255,
  email: 255,
  addressLine: 255,
  city: 100,
  postalCode: 20,
  kvkNumber: 20,
  vatNumber: 20,
  country: 2,
  password: 100,
} as const

export const PASSWORD_MIN_LENGTH = 8

export type Errors = Record<string, string>

/**
 * Good enough to catch a typo, not an attempt to decide what a valid address
 * is — the backend's @Email and the actual mail server have the last word.
 */
const EMAIL_SHAPE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

/**
 * Checks one required text field: present after trimming, and within its limit.
 * Returns the message, or null when the value is fine.
 *
 * @param label how the field is named in the message, e.g. "Bedrijfsnaam"
 */
export function checkRequiredText(value: string, label: string, max: number): string | null {
  const trimmed = value.trim()
  if (!trimmed) return `${label} is verplicht`
  if (trimmed.length > max) return `${label} mag maximaal ${max} tekens zijn`
  return null
}

/** The same length check for a field that may be left empty. */
export function checkOptionalText(value: string, label: string, max: number): string | null {
  const trimmed = value.trim()
  if (trimmed.length > max) return `${label} mag maximaal ${max} tekens zijn`
  return null
}

export function checkEmail(value: string, label = 'E-mailadres'): string | null {
  const trimmed = value.trim()
  if (!trimmed) return `${label} is verplicht`
  if (!EMAIL_SHAPE.test(trimmed)) return 'Vul een geldig e-mailadres in'
  if (trimmed.length > LIMITS.email) {
    return `${label} mag maximaal ${LIMITS.email} tekens zijn`
  }
  return null
}

/** Two letters, because it is an ISO 3166-1 alpha-2 code — not a country rule. */
export function checkCountry(value: string): string | null {
  if (value.trim().length !== LIMITS.country) {
    return 'Gebruik een landcode van 2 letters, bijv. NL'
  }
  return null
}

export function checkPassword(value: string): string | null {
  // not trimmed: spaces inside or around a password are part of it
  if (!value) return 'Wachtwoord is verplicht'
  if (value.length < PASSWORD_MIN_LENGTH) return `Minimaal ${PASSWORD_MIN_LENGTH} tekens`
  if (value.length > LIMITS.password) return `Maximaal ${LIMITS.password} tekens`
  return null
}

/** A whole number of days, at least one. */
export function checkPaymentTerm(value: string, label = 'Betaaltermijn'): string | null {
  const trimmed = value.trim()
  if (!trimmed) return `${label} is verplicht`
  if (!/^\d+$/.test(trimmed) || Number(trimmed) < 1) {
    return 'Vul een heel aantal dagen in, minimaal 1'
  }
  return null
}

/**
 * Collects the checks that produced a message, keyed by field. Lets a form read
 * as a list of rules instead of a run of ifs.
 */
export function collect(checks: Array<[string, string | null]>): Errors {
  const found: Errors = {}
  for (const [field, message] of checks) {
    if (message) found[field] = message
  }
  return found
}
