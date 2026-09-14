import { Box, Button, Flex, HStack, Heading, Spinner, Table, Text } from '@chakra-ui/react'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { ApiError } from '../api/client'
import type { InvoiceSummary } from '../api/invoices'
import { listInvoices } from '../api/invoices'
import { AppLayout } from '../components/AppLayout'
import { FormMessage } from '../components/FormMessage'
import { StatusBadge } from '../components/StatusBadge'
import { formatAmount, formatDate } from '../lib/money'

type LocationState = { notice?: string } | null

/**
 * The dashboard links here with a filter in the query string, so a tile can say
 * "3 verlopen" and the click lands on exactly those three. The list is small and
 * already fully loaded, so the filtering happens here rather than in an extra
 * endpoint; the query string keeps it bookmarkable and survives a refresh.
 */
const FILTERS = {
  draft: { label: 'Concepten', matches: (invoice: InvoiceSummary) => invoice.status === 'DRAFT' },
  overdue: {
    label: 'Verlopen',
    // OVERDUE is not a stored status yet: an invoice is late when it is still
    // out and its due date is behind us
    matches: (invoice: InvoiceSummary) =>
      invoice.status === 'SENT' && invoice.dueDate !== null && invoice.dueDate < today(),
  },
} as const

type FilterKey = keyof typeof FILTERS

/** Today as the backend writes dates: local calendar day, not UTC. */
function today(): string {
  const now = new Date()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  return `${now.getFullYear()}-${month}-${day}`
}

function isFilterKey(value: string | null): value is FilterKey {
  return value !== null && value in FILTERS
}

export function InvoicesPage() {
  const navigate = useNavigate()
  const location = useLocation()

  const [searchParams, setSearchParams] = useSearchParams()
  const filterParam = searchParams.get('filter')
  const filter = isFilterKey(filterParam) ? filterParam : null

  const [invoices, setInvoices] = useState<InvoiceSummary[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  // set once from the message the previous page passed along; nothing here changes it
  const [notice] = useState<string | null>((location.state as LocationState)?.notice ?? null)

  // drop the message from history, so a refresh does not show it again
  useEffect(() => {
    if ((location.state as LocationState)?.notice) {
      navigate(location.pathname, { replace: true, state: null })
    }
  }, [location.pathname, location.state, navigate])

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setInvoices(await listInvoices())
    } catch (caught) {
      setError(
        caught instanceof ApiError ? caught.message : 'De facturen konden niet worden opgehaald.',
      )
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const shown = useMemo(
    () => (filter ? invoices.filter(FILTERS[filter].matches) : invoices),
    [invoices, filter],
  )

  return (
    <AppLayout>
      <Flex align="flex-start" justify="space-between" mb="6" gap="4" wrap="wrap">
        <Box>
          <Heading size="lg" mb="1">
            Facturen
          </Heading>
          <Text color="fg.muted" fontSize="sm">
            Concepten, verzonden facturen en wat er al betaald is.
          </Text>
        </Box>
        <Button colorPalette="brand" onClick={() => navigate('/invoices/new')}>
          Nieuwe factuur
        </Button>
      </Flex>

      {notice && <FormMessage status="success">{notice}</FormMessage>}
      {error && <FormMessage status="error">{error}</FormMessage>}

      {filter && (
        <HStack
          mb="4"
          gap="3"
          bg="bg.panel"
          borderWidth="1px"
          borderColor="border.muted"
          rounded="lg"
          px="4"
          py="2.5"
        >
          <Text fontSize="sm" color="fg.muted">
            Gefilterd op
          </Text>
          <Text fontSize="sm" fontWeight="medium" color="brand.fg">
            {FILTERS[filter].label}
          </Text>
          <Button size="xs" variant="ghost" ml="auto" onClick={() => setSearchParams({})}>
            Toon alle facturen
          </Button>
        </HStack>
      )}

      <Box bg="bg.panel" borderWidth="1px" borderColor="border.muted" rounded="xl" overflow="hidden">
        {loading ? (
          <Flex justify="center" py="16">
            <Spinner color="brand.solid" />
          </Flex>
        ) : shown.length === 0 ? (
          <Box py="16" textAlign="center" px="6">
            <Text fontWeight="medium" mb="1">
              {filter ? 'Geen facturen in deze selectie' : 'Nog geen facturen'}
            </Text>
            <Text color="fg.muted" fontSize="sm" mb="5">
              {filter
                ? 'Er is op dit moment niets dat aan dit filter voldoet.'
                : 'Stel je eerste factuur op als concept; het nummer volgt bij verzenden.'}
            </Text>
            {filter ? (
              <Button variant="outline" size="sm" onClick={() => setSearchParams({})}>
                Toon alle facturen
              </Button>
            ) : (
              <Button colorPalette="brand" size="sm" onClick={() => navigate('/invoices/new')}>
                Nieuwe factuur
              </Button>
            )}
          </Box>
        ) : (
          <Table.ScrollArea>
            <Table.Root size="md" interactive>
              <Table.Header>
                <Table.Row bg="bg.subtle">
                  <Table.ColumnHeader>Nummer</Table.ColumnHeader>
                  <Table.ColumnHeader>Klant</Table.ColumnHeader>
                  <Table.ColumnHeader>Datum</Table.ColumnHeader>
                  <Table.ColumnHeader textAlign="end">Totaal</Table.ColumnHeader>
                  <Table.ColumnHeader>Status</Table.ColumnHeader>
                </Table.Row>
              </Table.Header>
              <Table.Body>
                {shown.map((invoice) => (
                  <Table.Row
                    key={invoice.id}
                    onClick={() => navigate(`/invoices/${invoice.id}`)}
                    cursor="pointer"
                  >
                    <Table.Cell fontWeight="medium">
                      {invoice.invoiceNumber ?? (
                        <Text as="span" color="fg.subtle" fontStyle="italic" fontWeight="normal">
                          Concept
                        </Text>
                      )}
                    </Table.Cell>
                    <Table.Cell color="fg.muted">{invoice.customerName}</Table.Cell>
                    <Table.Cell color="fg.muted">{formatDate(invoice.invoiceDate)}</Table.Cell>
                    <Table.Cell textAlign="end">{formatAmount(invoice.totalAmount)}</Table.Cell>
                    <Table.Cell>
                      <StatusBadge status={invoice.status} />
                    </Table.Cell>
                  </Table.Row>
                ))}
              </Table.Body>
            </Table.Root>
          </Table.ScrollArea>
        )}
      </Box>
    </AppLayout>
  )
}
