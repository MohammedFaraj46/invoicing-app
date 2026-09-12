import { Box, Button, Flex, Grid, HStack, Heading, Spinner, Stack, Table, Text } from '@chakra-ui/react'
import { useCallback, useEffect, useState } from 'react'
import { useLocation, useNavigate, useParams } from 'react-router-dom'
import { ApiError } from '../api/client'
import type { Invoice } from '../api/invoices'
import { deleteInvoice, downloadInvoicePdf, getInvoice, payInvoice, sendInvoice } from '../api/invoices'
import { AppLayout } from '../components/AppLayout'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { FormMessage } from '../components/FormMessage'
import { StatusBadge } from '../components/StatusBadge'
import { formatAmount, formatDate, formatRate } from '../lib/money'

type LocationState = { notice?: string } | null
type PendingAction = 'send' | 'delete' | null

export function InvoiceDetailPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const { id } = useParams<{ id: string }>()
  const invoiceId = Number(id)

  const [invoice, setInvoice] = useState<Invoice | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(
    (location.state as LocationState)?.notice ?? null,
  )
  const [pending, setPending] = useState<PendingAction>(null)
  const [busy, setBusy] = useState(false)
  const [downloading, setDownloading] = useState(false)

  useEffect(() => {
    if ((location.state as LocationState)?.notice) {
      navigate(location.pathname, { replace: true, state: null })
    }
  }, [location.pathname, location.state, navigate])

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setInvoice(await getInvoice(invoiceId))
    } catch (caught) {
      setError(
        caught instanceof ApiError && caught.status === 404
          ? 'Deze factuur bestaat niet (meer).'
          : 'De factuur kon niet worden geladen.',
      )
    } finally {
      setLoading(false)
    }
  }, [invoiceId])

  useEffect(() => {
    void load()
  }, [load])

  /** Turns a failed action into a message, and refreshes so the UI matches reality. */
  async function handleFailure(caught: unknown, fallback: string) {
    setNotice(null)
    if (caught instanceof ApiError && caught.status === 409) {
      // the status moved on somewhere else; show what the server says and reload
      setError(caught.message)
      await load()
    } else if (caught instanceof ApiError && caught.status === 404) {
      setError('Deze factuur bestaat niet (meer).')
    } else {
      setError(caught instanceof ApiError ? caught.message : fallback)
    }
  }

  async function confirmSend() {
    setBusy(true)
    setError(null)
    try {
      const sent = await sendInvoice(invoiceId)
      setInvoice(sent)
      setNotice(`De factuur is verzonden met nummer ${sent.invoiceNumber}.`)
      setPending(null)
    } catch (caught) {
      setPending(null)
      await handleFailure(caught, 'De factuur kon niet worden verzonden.')
    } finally {
      setBusy(false)
    }
  }

  async function handlePay() {
    setBusy(true)
    setError(null)
    try {
      const paid = await payInvoice(invoiceId)
      setInvoice(paid)
      setNotice('De factuur staat nu op betaald.')
    } catch (caught) {
      await handleFailure(caught, 'De factuur kon niet op betaald worden gezet.')
    } finally {
      setBusy(false)
    }
  }

  /**
   * Fetches the PDF with the session cookie and hands it to the browser as a
   * download. A plain link would work too, but going through the api client
   * means a 409 or 404 surfaces as a message instead of a JSON page.
   */
  async function handleDownloadPdf() {
    if (!invoice) return
    setDownloading(true)
    setError(null)
    try {
      const blob = await downloadInvoicePdf(invoice.id)
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = `factuur-${invoice.invoiceNumber ?? invoice.id}.pdf`
      document.body.appendChild(link)
      link.click()
      link.remove()
      // release the blob once the browser has taken it
      URL.revokeObjectURL(url)
    } catch (caught) {
      await handleFailure(caught, 'De PDF kon niet worden gedownload.')
    } finally {
      setDownloading(false)
    }
  }

  async function confirmDelete() {
    setBusy(true)
    setError(null)
    try {
      await deleteInvoice(invoiceId)
      navigate('/invoices', { replace: true, state: { notice: 'Het concept is verwijderd.' } })
    } catch (caught) {
      setPending(null)
      await handleFailure(caught, 'Het concept kon niet worden verwijderd.')
    } finally {
      setBusy(false)
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

  if (!invoice) {
    return (
      <AppLayout>
        {error && <FormMessage status="error">{error}</FormMessage>}
        <Button variant="outline" onClick={() => navigate('/invoices')}>
          Terug naar facturen
        </Button>
      </AppLayout>
    )
  }

  const isDraft = invoice.status === 'DRAFT'
  const isSent = invoice.status === 'SENT'

  return (
    <AppLayout>
      <Flex align="flex-start" justify="space-between" mb="6" gap="4" wrap="wrap">
        <Box>
          <HStack gap="3" mb="1">
            <Heading size="lg">
              {invoice.invoiceNumber ?? 'Concept'}
            </Heading>
            <StatusBadge status={invoice.status} />
          </HStack>
          <Text color="fg.muted" fontSize="sm">
            {invoice.customerName}
          </Text>
        </Box>

        <HStack gap="3">
          {/* a PDF exists only once the invoice has a number */}
          {!isDraft && (
            <Button variant="outline" onClick={handleDownloadPdf} loading={downloading}>
              Download PDF
            </Button>
          )}
          {/* editing and deleting exist only while the invoice is a draft */}
          {isDraft && (
            <>
              <Button
                variant="ghost"
                color="fg.error"
                onClick={() => setPending('delete')}
                disabled={busy}
              >
                Verwijderen
              </Button>
              <Button
                variant="outline"
                onClick={() => navigate(`/invoices/${invoice.id}/edit`)}
                disabled={busy}
              >
                Bewerken
              </Button>
              <Button colorPalette="brand" onClick={() => setPending('send')} disabled={busy}>
                Verzenden
              </Button>
            </>
          )}
          {isSent && (
            <Button colorPalette="brand" onClick={handlePay} loading={busy}>
              Markeer als betaald
            </Button>
          )}
        </HStack>
      </Flex>

      {notice && <FormMessage status="success">{notice}</FormMessage>}
      {error && <FormMessage status="error">{error}</FormMessage>}

      <Stack gap="6">
        <Box bg="bg.panel" borderWidth="1px" borderColor="border.muted" rounded="xl" p="6">
          <Grid templateColumns={{ base: '1fr 1fr', md: 'repeat(4, 1fr)' }} gap="5">
            <Detail label="Factuurnummer" value={invoice.invoiceNumber ?? 'Nog niet toegekend'} />
            <Detail label="Factuurdatum" value={formatDate(invoice.invoiceDate)} />
            <Detail label="Vervaldatum" value={formatDate(invoice.dueDate)} />
            <Detail label="Betaaltermijn" value={`${invoice.paymentTermDays} dagen`} />
          </Grid>
        </Box>

        <Box bg="bg.panel" borderWidth="1px" borderColor="border.muted" rounded="xl" overflow="hidden">
          <Table.ScrollArea>
            <Table.Root size="md">
              <Table.Header>
                <Table.Row bg="bg.subtle">
                  <Table.ColumnHeader>Omschrijving</Table.ColumnHeader>
                  <Table.ColumnHeader textAlign="end">Aantal</Table.ColumnHeader>
                  <Table.ColumnHeader textAlign="end">Stukprijs</Table.ColumnHeader>
                  <Table.ColumnHeader textAlign="end">BTW</Table.ColumnHeader>
                  <Table.ColumnHeader textAlign="end">Bedrag</Table.ColumnHeader>
                </Table.Row>
              </Table.Header>
              <Table.Body>
                {invoice.lines.map((line) => (
                  <Table.Row key={line.id}>
                    <Table.Cell>
                      {line.description}
                      {line.vatType === 'REVERSE_CHARGE' && (
                        <Text fontSize="xs" color="brand.fg">
                          BTW verlegd
                        </Text>
                      )}
                    </Table.Cell>
                    <Table.Cell textAlign="end" color="fg.muted">
                      {Number.parseFloat(line.quantity).toLocaleString('nl-NL')}
                    </Table.Cell>
                    <Table.Cell textAlign="end" color="fg.muted">
                      {formatAmount(line.unitPrice)}
                    </Table.Cell>
                    <Table.Cell textAlign="end" color="fg.muted">
                      {formatRate(line.vatRate)}
                    </Table.Cell>
                    <Table.Cell textAlign="end">{formatAmount(line.lineNetAmount)}</Table.Cell>
                  </Table.Row>
                ))}
              </Table.Body>
            </Table.Root>
          </Table.ScrollArea>
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
          <Stack gap="2">
            <AmountRow label="Subtotaal" value={formatAmount(invoice.subtotalAmount)} />
            {invoice.vatBreakdown.map((group) => (
              <AmountRow
                key={group.vatRate}
                label={`BTW ${formatRate(group.vatRate)}`}
                value={formatAmount(group.vatAmount)}
                muted
              />
            ))}
            <AmountRow label="Totaal" value={formatAmount(invoice.totalAmount)} strong />
          </Stack>
        </Box>

        {invoice.notes && (
          <Box bg="bg.panel" borderWidth="1px" borderColor="border.muted" rounded="xl" p="6">
            <Text fontSize="sm" fontWeight="semibold" color="brand.fg" mb="2">
              Notities
            </Text>
            <Text fontSize="sm" color="fg.muted" whiteSpace="pre-wrap">
              {invoice.notes}
            </Text>
          </Box>
        )}
      </Stack>

      <ConfirmDialog
        open={pending === 'send'}
        title="Factuur verzenden?"
        confirmLabel="Verzenden"
        busy={busy}
        onConfirm={confirmSend}
        onCancel={() => setPending(null)}
      >
        De factuur krijgt een definitief nummer en een factuurdatum. Daarna kun je de regels en
        bedragen niet meer wijzigen.
      </ConfirmDialog>

      <ConfirmDialog
        open={pending === 'delete'}
        title="Concept verwijderen?"
        confirmLabel="Verwijderen"
        confirmPalette="red"
        busy={busy}
        onConfirm={confirmDelete}
        onCancel={() => setPending(null)}
      >
        Weet je zeker dat je dit concept wilt verwijderen? Dit kan niet ongedaan worden gemaakt.
      </ConfirmDialog>
    </AppLayout>
  )
}

function Detail({ label, value }: { label: string; value: string }) {
  return (
    <Box>
      <Text fontSize="xs" color="fg.subtle" mb="1">
        {label}
      </Text>
      <Text fontSize="sm">{value}</Text>
    </Box>
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
      <Text>{value}</Text>
    </HStack>
  )
}
