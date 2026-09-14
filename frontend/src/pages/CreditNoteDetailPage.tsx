import { Badge, Box, Button, Flex, Grid, HStack, Heading, Spinner, Stack, Table, Text } from '@chakra-ui/react'
import { useCallback, useEffect, useState } from 'react'
import { useLocation, useNavigate, useParams } from 'react-router-dom'
import { ApiError } from '../api/client'
import type { CreditNote } from '../api/creditNotes'
import {
  deleteCreditNote,
  downloadCreditNotePdf,
  getCreditNote,
  sendCreditNote,
} from '../api/creditNotes'
import { AppLayout } from '../components/AppLayout'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { FormMessage } from '../components/FormMessage'
import { formatAmount, formatDate, formatRate } from '../lib/money'

type LocationState = { notice?: string } | null
type PendingAction = 'send' | 'delete' | null

export function CreditNoteDetailPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const { id } = useParams<{ id: string }>()
  const creditNoteId = Number(id)

  const [creditNote, setCreditNote] = useState<CreditNote | null>(null)
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
      setCreditNote(await getCreditNote(creditNoteId))
    } catch (caught) {
      setError(
        caught instanceof ApiError && caught.status === 404
          ? 'Deze creditnota bestaat niet (meer).'
          : 'De creditnota kon niet worden geladen.',
      )
    } finally {
      setLoading(false)
    }
  }, [creditNoteId])

  useEffect(() => {
    void load()
  }, [load])

  async function handleFailure(caught: unknown, fallback: string) {
    setNotice(null)
    if (caught instanceof ApiError && caught.status === 409) {
      // the status moved on elsewhere; show what the server says and refresh
      setError(caught.message)
      await load()
    } else if (caught instanceof ApiError && caught.status === 404) {
      setError('Deze creditnota bestaat niet (meer).')
    } else {
      setError(caught instanceof ApiError ? caught.message : fallback)
    }
  }

  async function confirmSend() {
    setBusy(true)
    setError(null)
    try {
      const sent = await sendCreditNote(creditNoteId)
      setCreditNote(sent)
      setNotice(`De creditnota is verzonden met nummer ${sent.creditNoteNumber}.`)
      setPending(null)
    } catch (caught) {
      setPending(null)
      await handleFailure(caught, 'De creditnota kon niet worden verzonden.')
    } finally {
      setBusy(false)
    }
  }

  async function confirmDelete() {
    if (!creditNote) return
    setBusy(true)
    setError(null)
    try {
      await deleteCreditNote(creditNoteId)
      navigate(`/invoices/${creditNote.originalInvoiceId}`, {
        replace: true,
        state: { notice: 'De concept-creditnota is verwijderd.' },
      })
    } catch (caught) {
      setPending(null)
      await handleFailure(caught, 'De creditnota kon niet worden verwijderd.')
    } finally {
      setBusy(false)
    }
  }

  async function handleDownloadPdf() {
    if (!creditNote) return
    setDownloading(true)
    setError(null)
    try {
      const blob = await downloadCreditNotePdf(creditNote.id)
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = `creditnota-${creditNote.creditNoteNumber ?? creditNote.id}.pdf`
      document.body.appendChild(link)
      link.click()
      link.remove()
      URL.revokeObjectURL(url)
    } catch (caught) {
      await handleFailure(caught, 'De PDF kon niet worden gedownload.')
    } finally {
      setDownloading(false)
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

  if (!creditNote) {
    return (
      <AppLayout>
        {error && <FormMessage status="error">{error}</FormMessage>}
        <Button variant="outline" onClick={() => navigate('/invoices')}>
          Terug naar facturen
        </Button>
      </AppLayout>
    )
  }

  const isDraft = creditNote.status === 'DRAFT'

  return (
    <AppLayout>
      <Flex align="flex-start" justify="space-between" mb="6" gap="4" wrap="wrap">
        <Box>
          <HStack gap="3" mb="1">
            <Heading size="lg">{creditNote.creditNoteNumber ?? 'Concept-creditnota'}</Heading>
            <Badge
              bg={isDraft ? 'bg.muted' : 'brand.solid'}
              color={isDraft ? 'fg.muted' : 'brand.contrast'}
              borderWidth="1px"
              borderColor={isDraft ? 'border.emphasized' : 'brand.solid'}
              rounded="full"
              px="2.5"
              py="0.5"
              fontSize="xs"
              fontWeight="medium"
            >
              {isDraft ? 'Concept' : 'Verzonden'}
            </Badge>
          </HStack>
          <Text color="fg.muted" fontSize="sm">
            {creditNote.customerName}
          </Text>
        </Box>

        <HStack gap="3">
          {!isDraft && (
            <Button variant="outline" onClick={handleDownloadPdf} loading={downloading}>
              Download PDF
            </Button>
          )}
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
              <Button colorPalette="brand" onClick={() => setPending('send')} disabled={busy}>
                Verzenden
              </Button>
            </>
          )}
        </HStack>
      </Flex>

      {notice && <FormMessage status="success">{notice}</FormMessage>}
      {error && <FormMessage status="error">{error}</FormMessage>}

      <Stack gap="6">
        <Box bg="bg.panel" borderWidth="1px" borderColor="border.muted" rounded="xl" p="6">
          <Grid templateColumns={{ base: '1fr 1fr', md: 'repeat(3, 1fr)' }} gap="5">
            <Detail
              label="Creditnotanummer"
              value={creditNote.creditNoteNumber ?? 'Nog niet toegekend'}
            />
            <Detail label="Creditdatum" value={formatDate(creditNote.creditDate)} />
            <Box>
              <Text fontSize="xs" color="fg.subtle" mb="1">
                Betreft factuur
              </Text>
              <Button
                variant="plain"
                size="sm"
                p="0"
                h="auto"
                color="brand.fg"
                fontWeight="normal"
                onClick={() => navigate(`/invoices/${creditNote.originalInvoiceId}`)}
              >
                {creditNote.originalInvoiceNumber ?? 'Concept'}
              </Button>
            </Box>
          </Grid>
        </Box>

        <Box bg="bg.panel" borderWidth="1px" borderColor="border.muted" rounded="xl" overflow="hidden">
          <Table.ScrollArea>
            <Table.Root size="md">
              <Table.Header>
                <Table.Row bg="bg.subtle">
                  <Table.ColumnHeader>Omschrijving</Table.ColumnHeader>
                  <Table.ColumnHeader textAlign="end">Bedrag</Table.ColumnHeader>
                  <Table.ColumnHeader textAlign="end">BTW</Table.ColumnHeader>
                  <Table.ColumnHeader textAlign="end">Gecrediteerd</Table.ColumnHeader>
                </Table.Row>
              </Table.Header>
              <Table.Body>
                {creditNote.lines.map((line) => (
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
                      {formatAmount(line.unitPrice)}
                    </Table.Cell>
                    <Table.Cell textAlign="end" color="fg.muted">
                      {formatRate(line.vatRate)}
                    </Table.Cell>
                    <Table.Cell textAlign="end" color="fg.error">
                      {formatAmount(line.lineNetAmount)}
                    </Table.Cell>
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
            <AmountRow label="Subtotaal" value={formatAmount(creditNote.subtotalAmount)} />
            {creditNote.vatBreakdown.map((group) => (
              <AmountRow
                key={group.vatRate}
                label={`BTW ${formatRate(group.vatRate)}`}
                value={formatAmount(group.vatAmount)}
                muted
              />
            ))}
            <AmountRow
              label="Totaal te crediteren"
              value={formatAmount(creditNote.totalAmount)}
              strong
            />
          </Stack>
        </Box>

        {creditNote.reason && (
          <Box bg="bg.panel" borderWidth="1px" borderColor="border.muted" rounded="xl" p="6">
            <Text fontSize="sm" fontWeight="semibold" color="brand.fg" mb="2">
              Reden
            </Text>
            <Text fontSize="sm" color="fg.muted" whiteSpace="pre-wrap">
              {creditNote.reason}
            </Text>
          </Box>
        )}
      </Stack>

      <ConfirmDialog
        open={pending === 'send'}
        title="Creditnota verzenden?"
        confirmLabel="Verzenden"
        busy={busy}
        onConfirm={confirmSend}
        onCancel={() => setPending(null)}
      >
        De creditnota krijgt een definitief nummer uit de creditnotareeks. Daarna kun je hem niet
        meer wijzigen of verwijderen.
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
        Het gecrediteerde bedrag komt daarmee weer beschikbaar op de factuur. Dit kan niet ongedaan
        worden gemaakt.
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
      {/* credits are money going back, so they read in the error colour */}
      <Text color="fg.error">{value}</Text>
    </HStack>
  )
}
