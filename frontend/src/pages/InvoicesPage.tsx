import { Box, Button, Flex, Heading, Spinner, Table, Text } from '@chakra-ui/react'
import { useCallback, useEffect, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { ApiError } from '../api/client'
import type { InvoiceSummary } from '../api/invoices'
import { listInvoices } from '../api/invoices'
import { AppLayout } from '../components/AppLayout'
import { FormMessage } from '../components/FormMessage'
import { StatusBadge } from '../components/StatusBadge'
import { formatAmount, formatDate } from '../lib/money'

type LocationState = { notice?: string } | null

export function InvoicesPage() {
  const navigate = useNavigate()
  const location = useLocation()

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

      <Box bg="bg.panel" borderWidth="1px" borderColor="border.muted" rounded="xl" overflow="hidden">
        {loading ? (
          <Flex justify="center" py="16">
            <Spinner color="brand.solid" />
          </Flex>
        ) : invoices.length === 0 ? (
          <Box py="16" textAlign="center" px="6">
            <Text fontWeight="medium" mb="1">
              Nog geen facturen
            </Text>
            <Text color="fg.muted" fontSize="sm" mb="5">
              Stel je eerste factuur op als concept; het nummer volgt bij verzenden.
            </Text>
            <Button colorPalette="brand" size="sm" onClick={() => navigate('/invoices/new')}>
              Nieuwe factuur
            </Button>
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
                {invoices.map((invoice) => (
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
