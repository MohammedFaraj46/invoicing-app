/**
 * Client-side mirror of the backend's InvoiceCalculator, for showing totals
 * while the user types. The server stays the source of truth: what it returns
 * on save is what gets stored.
 *
 * <p>Everything is done in whole cents with integer arithmetic. JavaScript
 * numbers are binary floats, so 0.1 + 0.2 is not 0.3 and multiplying prices
 * drifts; working in integers avoids that entirely and matches the backend's
 * BigDecimal exactly.
 *
 * <p>Rounding is HALF_UP per line, before the lines are added up — the same
 * choice the backend documents, and for the same reason: the totals have to
 * equal the sum of the amounts the reader can see.
 */

/** Quantities carry at most 3 decimals, prices and rates at most 2. */
const QUANTITY_UNITS = 1000
const PRICE_UNITS = 100
const RATE_UNITS = 100

export type MoneyLine = {
  quantity: string
  unitPrice: string
  vatRate: string
}

export type LineTotals = {
  netCents: number
  vatCents: number
  totalCents: number
}

export type VatGroup = {
  /** The rate as a number, e.g. 21 or 9. */
  rate: number
  netCents: number
  vatCents: number
}

export type InvoiceTotals = {
  lines: LineTotals[]
  vatGroups: VatGroup[]
  subtotalCents: number
  vatCents: number
  totalCents: number
}

const EMPTY_LINE: LineTotals = { netCents: 0, vatCents: 0, totalCents: 0 }

/** Parses a typed value into scaled integer units; anything unparseable is 0. */
function toUnits(value: string, units: number): number {
  const parsed = Number.parseFloat((value ?? '').replace(',', '.'))
  return Number.isFinite(parsed) ? Math.round(parsed * units) : 0
}

/** Half-up division of two integers, for non-negative numerators. */
function divideHalfUp(numerator: number, divisor: number): number {
  return Math.floor(numerator / divisor + 0.5)
}

export function calculateLine(line: MoneyLine): LineTotals {
  const quantity = toUnits(line.quantity, QUANTITY_UNITS)
  const unitPrice = toUnits(line.unitPrice, PRICE_UNITS)
  const rate = toUnits(line.vatRate, RATE_UNITS)

  if (quantity <= 0 || unitPrice < 0) {
    return EMPTY_LINE
  }

  // quantity/1000 x unitPrice/100 euro = quantity*unitPrice/1000 cents
  const netCents = divideHalfUp(quantity * unitPrice, QUANTITY_UNITS)
  // VAT over the rounded net, exactly as the backend does it
  const vatCents = divideHalfUp(netCents * rate, RATE_UNITS * 100)

  return { netCents, vatCents, totalCents: netCents + vatCents }
}

export function calculateInvoice(lines: MoneyLine[]): InvoiceTotals {
  const perLine = lines.map(calculateLine)

  const groups = new Map<number, VatGroup>()
  lines.forEach((line, index) => {
    const rate = toUnits(line.vatRate, RATE_UNITS) / RATE_UNITS
    const totals = perLine[index]
    const existing = groups.get(rate)
    if (existing) {
      existing.netCents += totals.netCents
      existing.vatCents += totals.vatCents
    } else {
      groups.set(rate, { rate, netCents: totals.netCents, vatCents: totals.vatCents })
    }
  })

  return {
    lines: perLine,
    vatGroups: [...groups.values()].sort((a, b) => a.rate - b.rate),
    subtotalCents: perLine.reduce((sum, line) => sum + line.netCents, 0),
    vatCents: perLine.reduce((sum, line) => sum + line.vatCents, 0),
    totalCents: perLine.reduce((sum, line) => sum + line.totalCents, 0),
  }
}

const EURO = new Intl.NumberFormat('nl-NL', { style: 'currency', currency: 'EUR' })

export function formatCents(cents: number): string {
  return EURO.format(cents / 100)
}

/** Formats an amount the backend sent as a decimal string. */
export function formatAmount(amount: string): string {
  const parsed = Number.parseFloat(amount)
  return Number.isFinite(parsed) ? EURO.format(parsed) : amount
}

/** 21.00 -> "21%", 9.00 -> "9%" */
export function formatRate(rate: string | number): string {
  const parsed = typeof rate === 'number' ? rate : Number.parseFloat(rate)
  if (!Number.isFinite(parsed)) return String(rate)
  return `${parsed % 1 === 0 ? parsed.toFixed(0) : parsed.toFixed(2)}%`
}

/** ISO date to the Dutch short form. */
export function formatDate(date: string | null): string {
  if (!date) return '—'
  const parsed = new Date(date)
  return Number.isNaN(parsed.getTime())
    ? date
    : new Intl.DateTimeFormat('nl-NL', { day: 'numeric', month: 'short', year: 'numeric' }).format(parsed)
}
