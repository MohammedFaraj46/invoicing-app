import {
  Box,
  Button,
  Field,
  Flex,
  HStack,
  Heading,
  Input,
  Spinner,
  Stack,
  Table,
  Text,
  Textarea,
} from '@chakra-ui/react'
import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { ApiError } from '../api/client'
import type { CreditNoteLineInput, InvoiceCreditStatus } from '../api/creditNotes'
import { createCreditNote, getInvoiceCreditStatus } from '../api/creditNotes'
import { AppLayout } from '../components/AppLayout'
import { FormMessage } from '../components/FormMessage'
import { calculateInvoice, formatAmount, formatCents, formatRate, toNumber } from '../lib/money'

type Errors = Record<string, string>

export function CreditNoteFormPage() {
  const navigate = useNavigate()
  const { invoiceId: invoiceIdParam } = useParams<{ invoiceId: string }>()
  const invoiceId = Number(invoiceIdParam)

  const [status, setStatus] = useState<InvoiceCreditStatus | null>(null)
  const [amounts, setAmounts] = useState<Record<number, string>>({})
  const [reason, setReason] = useState('')
  const [errors, setErrors] = useState<Errors>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    let cancelled = false

    getInvoiceCreditStatus(invoiceId)
      .then((loaded) => {
        if (!cancelled) setStatus(loaded)
      })
      .catch((caught: unknown) => {
        if (cancelled) return
        setFormError(
          caught instanceof ApiError && caught.status === 404
            ? 'Deze factuur bestaat niet (meer).'
            : 'De factuurgegevens konden niet worden geladen.',
        )
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [invoiceId])

  /**
   * The live total, using the same per-line rounding as the backend: a credited
   * amount behaves as one unit at that price, at the line's own VAT rate.
   */
  const totals = useMemo(() => {
    if (!status) return null
    const credited = status.lines
      .filter((line) => toNumber(amounts[line.invoiceLineId] ?? '') > 0)
      .map((line) => ({
        quantity: '1',
        unitPrice: (amounts[line.invoiceLineId] ?? '').replace(',', '.'),
        vatRate: line.vatRate,
      }))
    return calculateInvoice(credited)
  }, [status, amounts])

  function updateAmount(invoiceLineId: number, value: string) {
    setAmounts((previous) => ({ ...previous, [invoiceLineId]: value }))
    setErrors((previous) => {
      const key = fieldKeyFor(invoiceLineId)
      if (!(key in previous)) return previous
      const { [key]: _removed, ...rest } = previous
      return rest
    })
  }

  /** The server reports errors as lines[n]; n is the index among credited lines. */
  function fieldKeyFor(invoiceLineId: number): string {
    return `line-${invoiceLineId}`
  }

  function creditedLines(): CreditNoteLineInput[] {
    if (!status) return []
    return status.lines
      .filter((line) => toNumber(amounts[line.invoiceLineId] ?? '') > 0)
      .map((line) => ({
        invoiceLineId: line.invoiceLineId,
        netAmount: (amounts[line.invoiceLineId] ?? '').replace(',', '.'),
      }))
  }

  function validate(): Errors {
    const found: Errors = {}
    if (!status) return found

    for (const line of status.lines) {
      const raw = amounts[line.invoiceLineId] ?? ''
      if (!raw.trim()) continue

      const value = toNumber(raw)
      const remaining = toNumber(line.remainingAmount)

      if (value < 0) {
        found[fieldKeyFor(line.invoiceLineId)] = 'Bedrag kan niet negatief zijn'
      } else if (value > remaining + 1e-9) {
        // the backend refuses this too; catching it here saves a round trip
        found[fieldKeyFor(line.invoiceLineId)] =
          `Er resteert nog maar ${formatAmount(line.remainingAmount)}`
      }
    }

    if (creditedLines().length === 0) {
      found.lines = 'Vul bij minstens één regel een bedrag in'
    }
    return found
  }

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()

    const found = validate()
    setErrors(found)
    if (Object.keys(found).length > 0) return

    setSaving(true)
    setFormError(null)

    const lines = creditedLines()

    try {
      const created = await createCreditNote({
        originalInvoiceId: invoiceId,
        reason: reason.trim() ? reason.trim() : null,
        lines,
      })
      navigate(`/credit-notes/${created.id}`, {
        replace: true,
        state: { notice: 'De creditnota is opgesteld.' },
      })
    } catch (caught) {
      if (caught instanceof ApiError) {
        // the server keys errors by position among the credited lines; map those
        // back onto the invoice line the field belongs to
        const mapped: Errors = {}
        for (const [key, message] of Object.entries(caught.fieldErrors)) {
          const match = key.match(/^lines\[(\d+)]/)
          if (match && lines[Number(match[1])]) {
            mapped[fieldKeyFor(lines[Number(match[1])].invoiceLineId)] = message
          } else {
            mapped[key] = message
          }
        }
        setErrors(mapped)

        if (caught.status === 400) {
          setFormError('Niet alle bedragen konden worden gecrediteerd.')
        } else if (caught.status === 409) {
          setFormError('Deze factuur kan niet (meer) gecrediteerd worden.')
        } else if (caught.status === 404) {
          setFormError('Deze factuur bestaat niet (meer).')
        } else {
          setFormError(caught.message)
        }

        // someone else may have credited in the meantime; show the new remainders
        void getInvoiceCreditStatus(invoiceId).then(setStatus).catch(() => undefined)
      } else {
        setFormError('De server is niet bereikbaar. Probeer het later opnieuw.')
      }
    } finally {
      setSaving(false)
    }
  }

  if (loading) {
    return (
      <AppLayout>
        <Flex justify="center" py="20">
          <Spinner color="brand.solid" />
        </Flex>
      </AppLayout>
    )
  }

  if (!status) {
    return (
      <AppLayout>
        {formError && <FormMessage status="error">{formError}</FormMessage>}
        <Button variant="outline" onClick={() => navigate('/invoices')}>
          Terug naar facturen
        </Button>
      </AppLayout>
    )
  }

  const nothingLeft = status.lines.every((line) => toNumber(line.remainingAmount) <= 0)

  return (
    <AppLayout>
      <Heading size="lg" mb="1">
        Creditnota opstellen
      </Heading>
      <Text color="fg.muted" fontSize="sm" mb="6">
        Voor factuur {status.invoiceNumber}. Vul per regel in hoeveel je wilt crediteren; leeg laten
        betekent niet crediteren.
      </Text>

      {formError && <FormMessage status="error">{formError}</FormMessage>}
      {errors.lines && <FormMessage status="error">{errors.lines}</FormMessage>}
      {nothingLeft && (
        <FormMessage status="error">
          Deze factuur is al volledig gecrediteerd.
        </FormMessage>
      )}

      <form onSubmit={handleSubmit} noValidate>
        <Stack gap="6">
          <Box bg="bg.panel" borderWidth="1px" borderColor="border.muted" rounded="xl" overflow="hidden">
            <Table.ScrollArea>
              <Table.Root size="md">
                <Table.Header>
                  <Table.Row bg="bg.subtle">
                    <Table.ColumnHeader>Omschrijving</Table.ColumnHeader>
                    <Table.ColumnHeader textAlign="end">Factuurbedrag</Table.ColumnHeader>
                    <Table.ColumnHeader textAlign="end">Al gecrediteerd</Table.ColumnHeader>
                    <Table.ColumnHeader textAlign="end">Resterend</Table.ColumnHeader>
                    <Table.ColumnHeader textAlign="end" minW="40">
                      Te crediteren
                    </Table.ColumnHeader>
                  </Table.Row>
                </Table.Header>
                <Table.Body>
                  {status.lines.map((line) => {
                    const remaining = toNumber(line.remainingAmount)
                    const error = errors[fieldKeyFor(line.invoiceLineId)]
                    return (
                      <Table.Row key={line.invoiceLineId}>
                        <Table.Cell>
                          {line.description}
                          <Text fontSize="xs" color="fg.subtle">
                            BTW {formatRate(line.vatRate)}
                            {line.vatType === 'REVERSE_CHARGE' && ' · verlegd'}
                          </Text>
                        </Table.Cell>
                        <Table.Cell textAlign="end" color="fg.muted">
                          {formatAmount(line.netAmount)}
                        </Table.Cell>
                        <Table.Cell textAlign="end" color="fg.muted">
                          {formatAmount(line.creditedAmount)}
                        </Table.Cell>
                        <Table.Cell textAlign="end" fontWeight="medium">
                          {formatAmount(line.remainingAmount)}
                        </Table.Cell>
                        <Table.Cell textAlign="end">
                          <Field.Root invalid={Boolean(error)}>
                            <Input
                              size="sm"
                              textAlign="end"
                              maxW="32"
                              ml="auto"
                              placeholder="0,00"
                              value={amounts[line.invoiceLineId] ?? ''}
                              disabled={remaining <= 0}
                              bg="bg.subtle"
                              borderColor="border.muted"
                              onChange={(event) =>
                                updateAmount(line.invoiceLineId, event.target.value)
                              }
                            />
                            {error && (
                              <Field.ErrorText justifyContent="flex-end">{error}</Field.ErrorText>
                            )}
                          </Field.Root>
                        </Table.Cell>
                      </Table.Row>
                    )
                  })}
                </Table.Body>
              </Table.Root>
            </Table.ScrollArea>
          </Box>

          <Box bg="bg.panel" borderWidth="1px" borderColor="border.muted" rounded="xl" p="6">
            <Field.Root>
              <Field.Label>Reden</Field.Label>
              <Textarea
                value={reason}
                rows={3}
                placeholder="Optioneel, verschijnt op de creditnota"
                bg="bg.subtle"
                borderColor="border.muted"
                onChange={(event) => setReason(event.target.value)}
              />
            </Field.Root>
          </Box>

          <Box
            bg="bg.panel"
            borderWidth="1px"
            borderColor="border.muted"
            rounded="xl"
            p="6"
            maxW="md"
            ml="auto"
            w="full"
          >
            <Text fontSize="sm" fontWeight="semibold" color="brand.fg" mb="4">
              Te crediteren
            </Text>
            <Stack gap="2">
              <CreditRow label="Subtotaal" cents={-(totals?.subtotalCents ?? 0)} />
              {(totals?.vatGroups ?? []).map((group) => (
                <CreditRow
                  key={group.rate}
                  label={`BTW ${formatRate(group.rate)}`}
                  cents={-group.vatCents}
                  muted
                />
              ))}
              <CreditRow label="Totaal" cents={-(totals?.totalCents ?? 0)} strong />
            </Stack>
            <Text fontSize="xs" color="fg.subtle" mt="3">
              Ter indicatie; de server berekent de definitieve bedragen bij opslaan.
            </Text>
          </Box>

          <HStack justify="flex-end" gap="3">
            <Button
              variant="ghost"
              type="button"
              onClick={() => navigate(`/invoices/${invoiceId}`)}
              disabled={saving}
            >
              Annuleren
            </Button>
            <Button type="submit" colorPalette="brand" loading={saving} disabled={nothingLeft}>
              Creditnota opstellen
            </Button>
          </HStack>
        </Stack>
      </form>
    </AppLayout>
  )
}

/** Credit amounts are negative; showing them in the error colour marks them as money going back. */
function CreditRow({
  label,
  cents,
  strong = false,
  muted = false,
}: {
  label: string
  cents: number
  strong?: boolean
  muted?: boolean
}) {
  return (
    <HStack
      justify="space-between"
      fontSize="sm"
      borderTopWidth={strong ? '1px' : '0'}
      borderColor="border.muted"
      pt={strong ? '2' : '0'}
      fontWeight={strong ? 'semibold' : 'normal'}
    >
      <Text color={muted ? 'fg.subtle' : 'fg.muted'}>{label}</Text>
      <Text color={cents < 0 ? 'fg.error' : undefined}>{formatCents(cents)}</Text>
    </HStack>
  )
}
