import {
  Box,
  Button,
  Field,
  Flex,
  Grid,
  HStack,
  Heading,
  Input,
  NativeSelect,
  Spinner,
  Stack,
  Text,
  Textarea,
} from '@chakra-ui/react'
import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { ApiError } from '../api/client'
import type { Customer } from '../api/customers'
import { listCustomers } from '../api/customers'
import type { InvoiceInput, InvoiceLineInput, VatType } from '../api/invoices'
import { createInvoice, getInvoice, updateInvoice } from '../api/invoices'
import { AppLayout } from '../components/AppLayout'
import { FormMessage } from '../components/FormMessage'
import { calculateInvoice, formatCents, formatRate } from '../lib/money'

/**
 * The rate that belongs to each VAT type. The backend refuses a mismatch, so
 * the form keeps the two in step rather than letting the user create one.
 */
const VAT_TYPES: Array<{ value: VatType; label: string; rate: string; fixed: boolean }> = [
  { value: 'STANDARD', label: 'Standaard 21%', rate: '21', fixed: false },
  { value: 'REDUCED', label: 'Verlaagd 9%', rate: '9', fixed: false },
  { value: 'ZERO', label: 'Nultarief 0%', rate: '0', fixed: true },
  { value: 'REVERSE_CHARGE', label: 'BTW verlegd', rate: '0', fixed: true },
]

function vatTypeInfo(type: VatType) {
  return VAT_TYPES.find((option) => option.value === type) ?? VAT_TYPES[0]
}

const EMPTY_LINE: InvoiceLineInput = {
  description: '',
  quantity: '1',
  unitPrice: '',
  vatRate: '21',
  vatType: 'STANDARD',
}

type Errors = Record<string, string>

export function InvoiceFormPage() {
  const navigate = useNavigate()
  const { id } = useParams<{ id: string }>()
  const invoiceId = id ? Number(id) : null
  const isEdit = invoiceId !== null

  const [customers, setCustomers] = useState<Customer[]>([])
  const [customerId, setCustomerId] = useState('')
  const [paymentTermDays, setPaymentTermDays] = useState('')
  const [notes, setNotes] = useState('')
  const [lines, setLines] = useState<InvoiceLineInput[]>([{ ...EMPTY_LINE }])
  const [errors, setErrors] = useState<Errors>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)

  // live preview while typing; the server recalculates and wins on save
  const totals = useMemo(() => calculateInvoice(lines), [lines])

  useEffect(() => {
    let cancelled = false

    async function load() {
      try {
        const own = await listCustomers()
        if (cancelled) return
        setCustomers(own)

        if (invoiceId === null) {
          // pre-select when there is only one customer to choose from
          if (own.length === 1) setCustomerId(String(own[0].id))
          return
        }

        const invoice = await getInvoice(invoiceId)
        if (cancelled) return

        if (invoice.status !== 'DRAFT') {
          setFormError('Alleen een concept kan bewerkt worden.')
          return
        }
        setCustomerId(String(invoice.customerId))
        setPaymentTermDays(String(invoice.paymentTermDays))
        setNotes(invoice.notes ?? '')
        setLines(
          invoice.lines.map((line) => ({
            description: line.description,
            quantity: line.quantity,
            unitPrice: line.unitPrice,
            vatRate: line.vatRate,
            vatType: line.vatType,
          })),
        )
      } catch (caught) {
        if (cancelled) return
        setFormError(
          caught instanceof ApiError && caught.status === 404
            ? 'Deze factuur bestaat niet (meer).'
            : 'De gegevens konden niet worden geladen.',
        )
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    void load()
    return () => {
      cancelled = true
    }
  }, [invoiceId])

  function clearError(key: string) {
    setErrors((previous) => {
      if (!(key in previous)) return previous
      const { [key]: _removed, ...rest } = previous
      return rest
    })
  }

  function updateLine(index: number, patch: Partial<InvoiceLineInput>) {
    setLines((previous) =>
      previous.map((line, position) => (position === index ? { ...line, ...patch } : line)),
    )
    Object.keys(patch).forEach((field) => clearError(`lines[${index}].${field}`))
  }

  /** Changing the VAT type pulls the rate along, which is what keeps the backend happy. */
  function changeVatType(index: number, type: VatType) {
    updateLine(index, { vatType: type, vatRate: vatTypeInfo(type).rate })
  }

  function addLine() {
    setLines((previous) => [...previous, { ...EMPTY_LINE }])
  }

  function removeLine(index: number) {
    setLines((previous) => previous.filter((_, position) => position !== index))
    setErrors({})
  }

  function validate(): Errors {
    const found: Errors = {}
    if (!customerId) found.customerId = 'Kies een klant'
    if (lines.length === 0) found.lines = 'Voeg minstens één regel toe'

    lines.forEach((line, index) => {
      if (!line.description.trim()) found[`lines[${index}].description`] = 'Omschrijving is verplicht'
      const quantity = Number.parseFloat(line.quantity.replace(',', '.'))
      if (!Number.isFinite(quantity) || quantity <= 0) {
        found[`lines[${index}].quantity`] = 'Aantal moet groter dan 0 zijn'
      }
      const price = Number.parseFloat(line.unitPrice.replace(',', '.'))
      if (!Number.isFinite(price) || price < 0) {
        found[`lines[${index}].unitPrice`] = 'Vul een stukprijs in'
      }
    })

    return found
  }

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()

    const found = validate()
    setErrors(found)
    if (Object.keys(found).length > 0) return

    setSaving(true)
    setFormError(null)

    const payload: InvoiceInput = {
      customerId: Number(customerId),
      paymentTermDays: paymentTermDays ? Number(paymentTermDays) : null,
      notes: notes.trim() ? notes.trim() : null,
      lines: lines.map((line) => ({
        ...line,
        quantity: line.quantity.replace(',', '.'),
        unitPrice: line.unitPrice.replace(',', '.'),
      })),
    }

    try {
      const saved = isEdit ? await updateInvoice(invoiceId, payload) : await createInvoice(payload)
      navigate(`/invoices/${saved.id}`, {
        replace: true,
        state: { notice: isEdit ? 'Het concept is bijgewerkt.' : 'Het concept is aangemaakt.' },
      })
    } catch (caught) {
      if (caught instanceof ApiError) {
        setErrors(caught.fieldErrors)
        if (caught.status === 400) {
          setFormError('Niet alle gegevens zijn correct ingevuld.')
        } else if (caught.status === 409) {
          setFormError('Deze factuur is al verzonden en kan niet meer gewijzigd worden.')
        } else if (caught.status === 404) {
          setFormError('Deze factuur of klant bestaat niet (meer).')
        } else {
          setFormError(caught.message)
        }
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

  return (
    <AppLayout>
      <Heading size="lg" mb="1">
        {isEdit ? 'Concept bewerken' : 'Nieuwe factuur'}
      </Heading>
      <Text color="fg.muted" fontSize="sm" mb="6">
        Het factuurnummer en de datum worden pas toegekend bij verzenden.
      </Text>

      {formError && <FormMessage status="error">{formError}</FormMessage>}

      {customers.length === 0 ? (
        <Box bg="bg.panel" borderWidth="1px" borderColor="border.muted" rounded="xl" p="8" textAlign="center">
          <Text fontWeight="medium" mb="1">
            Nog geen klanten
          </Text>
          <Text color="fg.muted" fontSize="sm" mb="5">
            Je hebt een klant nodig voordat je kunt factureren.
          </Text>
          <Button size="sm" colorPalette="brand" onClick={() => navigate('/customers/new')}>
            Klant toevoegen
          </Button>
        </Box>
      ) : (
        <form onSubmit={handleSubmit} noValidate>
          <Stack gap="6">
            <Box bg="bg.panel" borderWidth="1px" borderColor="border.muted" rounded="xl" p="6">
              <Grid templateColumns={{ base: '1fr', md: '2fr 1fr' }} gap="4">
                <Field.Root invalid={Boolean(errors.customerId)} required>
                  <Field.Label>
                    Klant
                    <Field.RequiredIndicator />
                  </Field.Label>
                  <NativeSelect.Root>
                    <NativeSelect.Field
                      value={customerId}
                      bg="bg.subtle"
                      borderColor="border.muted"
                      onChange={(event) => {
                        setCustomerId(event.currentTarget.value)
                        clearError('customerId')
                      }}
                    >
                      <option value="">Kies een klant…</option>
                      {customers.map((customer) => (
                        <option key={customer.id} value={customer.id}>
                          {customer.name}
                        </option>
                      ))}
                    </NativeSelect.Field>
                    <NativeSelect.Indicator />
                  </NativeSelect.Root>
                  {errors.customerId && <Field.ErrorText>{errors.customerId}</Field.ErrorText>}
                </Field.Root>

                <Field.Root invalid={Boolean(errors.paymentTermDays)}>
                  <Field.Label>Betaaltermijn (dagen)</Field.Label>
                  <Input
                    type="number"
                    min={1}
                    value={paymentTermDays}
                    placeholder="Standaard van je bedrijf"
                    bg="bg.subtle"
                    borderColor="border.muted"
                    onChange={(event) => {
                      setPaymentTermDays(event.target.value)
                      clearError('paymentTermDays')
                    }}
                  />
                  {errors.paymentTermDays && (
                    <Field.ErrorText>{errors.paymentTermDays}</Field.ErrorText>
                  )}
                </Field.Root>
              </Grid>
            </Box>

            <Box bg="bg.panel" borderWidth="1px" borderColor="border.muted" rounded="xl" p="6">
              <Flex justify="space-between" align="center" mb="4">
                <Text fontSize="sm" fontWeight="semibold" color="brand.fg">
                  Factuurregels
                </Text>
                <Button size="xs" variant="outline" onClick={addLine} type="button">
                  Regel toevoegen
                </Button>
              </Flex>

              {errors.lines && <FormMessage status="error">{errors.lines}</FormMessage>}

              <Stack gap="5">
                {lines.map((line, index) => {
                  const info = vatTypeInfo(line.vatType)
                  const lineTotals = totals.lines[index]
                  return (
                    <Box
                      key={index}
                      borderWidth="1px"
                      borderColor="border.muted"
                      rounded="lg"
                      p="4"
                      bg="bg.subtle"
                    >
                      <Flex justify="space-between" align="center" mb="3">
                        <Text fontSize="xs" color="fg.subtle">
                          Regel {index + 1}
                        </Text>
                        {lines.length > 1 && (
                          <Button
                            size="xs"
                            variant="ghost"
                            color="fg.error"
                            type="button"
                            onClick={() => removeLine(index)}
                          >
                            Verwijderen
                          </Button>
                        )}
                      </Flex>

                      <Stack gap="3">
                        <Field.Root invalid={Boolean(errors[`lines[${index}].description`])}>
                          <Field.Label>Omschrijving</Field.Label>
                          <Input
                            value={line.description}
                            bg="bg.panel"
                            borderColor="border.muted"
                            onChange={(event) =>
                              updateLine(index, { description: event.target.value })
                            }
                          />
                          {errors[`lines[${index}].description`] && (
                            <Field.ErrorText>{errors[`lines[${index}].description`]}</Field.ErrorText>
                          )}
                        </Field.Root>

                        <Grid templateColumns={{ base: '1fr 1fr', md: '1fr 1fr 2fr 1fr' }} gap="3">
                          <Field.Root invalid={Boolean(errors[`lines[${index}].quantity`])}>
                            <Field.Label>Aantal</Field.Label>
                            <Input
                              value={line.quantity}
                              bg="bg.panel"
                              borderColor="border.muted"
                              onChange={(event) => updateLine(index, { quantity: event.target.value })}
                            />
                            {errors[`lines[${index}].quantity`] && (
                              <Field.ErrorText>{errors[`lines[${index}].quantity`]}</Field.ErrorText>
                            )}
                          </Field.Root>

                          <Field.Root invalid={Boolean(errors[`lines[${index}].unitPrice`])}>
                            <Field.Label>Stukprijs</Field.Label>
                            <Input
                              value={line.unitPrice}
                              placeholder="0,00"
                              bg="bg.panel"
                              borderColor="border.muted"
                              onChange={(event) =>
                                updateLine(index, { unitPrice: event.target.value })
                              }
                            />
                            {errors[`lines[${index}].unitPrice`] && (
                              <Field.ErrorText>{errors[`lines[${index}].unitPrice`]}</Field.ErrorText>
                            )}
                          </Field.Root>

                          <Field.Root invalid={Boolean(errors[`lines[${index}].vatRate`])}>
                            <Field.Label>BTW</Field.Label>
                            <NativeSelect.Root>
                              <NativeSelect.Field
                                value={line.vatType}
                                bg="bg.panel"
                                borderColor="border.muted"
                                onChange={(event) =>
                                  changeVatType(index, event.currentTarget.value as VatType)
                                }
                              >
                                {VAT_TYPES.map((option) => (
                                  <option key={option.value} value={option.value}>
                                    {option.label}
                                  </option>
                                ))}
                              </NativeSelect.Field>
                              <NativeSelect.Indicator />
                            </NativeSelect.Root>
                            {errors[`lines[${index}].vatRate`] && (
                              <Field.ErrorText>{errors[`lines[${index}].vatRate`]}</Field.ErrorText>
                            )}
                          </Field.Root>

                          <Box>
                            <Text fontSize="sm" fontWeight="medium" mb="1.5">
                              Regeltotaal
                            </Text>
                            <Text fontSize="sm" py="2" color="fg.muted">
                              {formatCents(lineTotals?.totalCents ?? 0)}
                            </Text>
                          </Box>
                        </Grid>

                        {info.fixed && (
                          <Text fontSize="xs" color="brand.fg">
                            {line.vatType === 'REVERSE_CHARGE'
                              ? 'BTW verlegd: er wordt geen BTW berekend over deze regel.'
                              : 'Nultarief: er wordt geen BTW berekend over deze regel.'}
                          </Text>
                        )}
                      </Stack>
                    </Box>
                  )
                })}
              </Stack>
            </Box>

            <Box bg="bg.panel" borderWidth="1px" borderColor="border.muted" rounded="xl" p="6">
              <Field.Root>
                <Field.Label>Notities</Field.Label>
                <Textarea
                  value={notes}
                  rows={3}
                  placeholder="Optioneel, verschijnt op de factuur"
                  bg="bg.subtle"
                  borderColor="border.muted"
                  onChange={(event) => setNotes(event.target.value)}
                />
              </Field.Root>
            </Box>

            <Box bg="bg.panel" borderWidth="1px" borderColor="border.muted" rounded="xl" p="6" maxW="md" ml="auto">
              <Text fontSize="sm" fontWeight="semibold" color="brand.fg" mb="4">
                Totalen
              </Text>
              <Stack gap="2">
                <AmountRow label="Subtotaal" value={formatCents(totals.subtotalCents)} />
                {totals.vatGroups.map((group) => (
                  <AmountRow
                    key={group.rate}
                    label={`BTW ${formatRate(group.rate)}`}
                    value={formatCents(group.vatCents)}
                    muted
                  />
                ))}
                <AmountRow label="Totaal" value={formatCents(totals.totalCents)} strong />
              </Stack>
              <Text fontSize="xs" color="fg.subtle" mt="3">
                Ter indicatie; de server berekent de definitieve bedragen bij opslaan.
              </Text>
            </Box>

            <HStack justify="flex-end" gap="3">
              <Button
                variant="ghost"
                type="button"
                onClick={() => navigate(isEdit ? `/invoices/${invoiceId}` : '/invoices')}
                disabled={saving}
              >
                Annuleren
              </Button>
              <Button type="submit" colorPalette="brand" loading={saving}>
                {isEdit ? 'Wijzigingen opslaan' : 'Concept opslaan'}
              </Button>
            </HStack>
          </Stack>
        </form>
      )}
    </AppLayout>
  )
}

function AmountRow({
  label,
  value,
  strong = false,
  muted = false,
}: {
  label: string
  value: string
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
      <Text color={strong ? 'fg' : undefined}>{value}</Text>
    </HStack>
  )
}
