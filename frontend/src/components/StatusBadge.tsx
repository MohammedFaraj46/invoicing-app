import { Badge } from '@chakra-ui/react'
import type { InvoiceStatus } from '../api/invoices'

/**
 * Colour and wording per status, in one place so the list and the detail page
 * cannot drift apart.
 */
const STYLES: Record<InvoiceStatus, { label: string; bg: string; color: string; border: string }> = {
  DRAFT: { label: 'Concept', bg: 'bg.muted', color: 'fg.muted', border: 'border.emphasized' },
  SENT: { label: 'Verzonden', bg: 'brand.muted', color: 'brand.fg', border: 'brand.emphasized' },
  PAID: { label: 'Betaald', bg: 'brand.solid', color: 'brand.contrast', border: 'brand.solid' },
  OVERDUE: { label: 'Verlopen', bg: 'bg.error', color: 'fg.error', border: 'border.error' },
}

export function StatusBadge({ status }: { status: InvoiceStatus }) {
  const style = STYLES[status]
  return (
    <Badge
      bg={style.bg}
      color={style.color}
      borderWidth="1px"
      borderColor={style.border}
      rounded="full"
      px="2.5"
      py="0.5"
      fontSize="xs"
      fontWeight="medium"
    >
      {style.label}
    </Badge>
  )
}
