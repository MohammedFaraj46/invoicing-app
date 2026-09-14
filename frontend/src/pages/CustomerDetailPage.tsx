import {
  Box,
  Button,
  Flex,
  Grid,
  HStack,
  Heading,
  Spinner,
  Stack,
  Table,
  Text,
} from '@chakra-ui/react'
import { useEffect, useState } from 'react'
import { useLocation, useNavigate, useParams } from 'react-router-dom'
import { ApiError } from '../api/client'
import type { Customer, CustomerInvoices } from '../api/customers'
import { deleteCustomer, getCustomer, getCustomerInvoices } from '../api/customers'
import { AppLayout } from '../components/AppLayout'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { FormMessage } from '../components/FormMessage'
import { StatusBadge } from '../components/StatusBadge'
import { formatAmount, formatDate } from '../lib/money'

type LocationState = { notice?: string } | null

/**
 * One customer: who they are, and what has been invoiced to them.
 *
 * <p>The two calls go out together — the profile and the invoices are
 * independent, and the invoice call is also what answers the ownership
 * question, so a customer of another company lands on the same 404 as one that
 * does not exist.
 */
export function CustomerDetailPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const { id } = useParams<{ id: string }>()
  const customerId = Number(id)

  const [customer, setCustomer] = useState<Customer | null>(null)
  const [invoices, setInvoices] = useState<CustomerInvoices | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(
    (location.state as LocationState)?.notice ?? null,
  )
  const [confirmingDelete, setConfirmingDelete] = useState(false)
  const [deleting, setDeleting] = useState(false)

  // drop the message from history, so a refresh does not show it again
  useEffect(() => {
    if ((location.state as LocationState)?.notice) {
      navigate(location.pathname, { replace: true, state: null })
    }
  }, [location.pathname, location.state, navigate])

  useEffect(() => {
    let cancelled = false
    setLoading(true)

    Promise.all([getCustomer(customerId), getCustomerInvoices(customerId)])
      .then(([loadedCustomer, loadedInvoices]) => {
        if (cancelled) return
        setCustomer(loadedCustomer)
        setInvoices(loadedInvoices)
      })
      .catch((caught: unknown) => {
        if (cancelled) return
        setError(
          caught instanceof ApiError && caught.status === 404
            ? 'Deze klant bestaat niet (meer).'
            : 'De klantgegevens konden niet worden geladen.',
        )
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [customerId])

  async function confirmDelete() {
    setDeleting(true)
    setError(null)
    try {
      await deleteCustomer(customerId)
      navigate('/customers', {
        replace: true,
        state: { notice: `${customer?.name ?? 'De klant'} is verwijderd.` },
      })
    } catch (caught) {
      setConfirmingDelete(false)
      setNotice(null)
      setError(
        caught instanceof ApiError && caught.status === 409
          ? 'Deze klant heeft facturen en kan daarom niet worden verwijderd.'
          : 'De klant kon niet worden verwijderd.',
      )
    } finally {
      setDeleting(false)
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

  if (!customer) {
    return (
      <AppLayout>
        {error && <FormMessage status="error">{error}</FormMessage>}
        <Button variant="outline" onClick={() => navigate('/customers')}>
          Terug naar klanten
        </Button>
      </AppLayout>
    )
  }

  const hasInvoices = (invoices?.invoices.length ?? 0) > 0

  return (
    <AppLayout>
      <Flex align="flex-start" justify="space-between" mb="6" gap="4" wrap="wrap">
        <Box minW="0">
          <Heading size="lg" mb="1">
            {customer.name}
          </Heading>
          <Text color="fg.muted" fontSize="sm">
            {customer.city}
          </Text>
        </Box>

        <HStack gap="3">
          <Button variant="outline" onClick={() => navigate(`/customers/${customerId}/edit`)}>
            Bewerken
          </Button>
          {/* an invoice names who it was sent to, so a customer with invoices stays */}
          <Button
            variant="ghost"
            color="fg.error"
            disabled={hasInvoices}
            title={hasInvoices ? 'Deze klant heeft facturen en kan niet worden verwijderd' : undefined}
            onClick={() => setConfirmingDelete(true)}
          >
            Verwijderen
          </Button>
        </HStack>
      </Flex>

      {notice && <FormMessage status="success">{notice}</FormMessage>}
      {error && <FormMessage status="error">{error}</FormMessage>}

      <Stack gap="6">
        <Box bg="bg.panel" borderWidth="1px" borderColor="border.muted" rounded="xl" p="6">
          <Text fontSize="sm" fontWeight="semibold" color="brand.fg" mb="4">
            Gegevens
          </Text>
          <Grid templateColumns={{ base: '1fr', sm: '1fr 1fr' }} gap="5">
            <Detail label="E-mailadres" value={customer.email} />
            <Detail
              label="BTW-nummer"
              value={customer.vatNumber}
              // optional: only needed once you invoice with VAT reverse-charged
              fallback="Niet opgegeven"
            />
            <Detail label="Adres" value={customer.addressLine} />
            <Detail
              label="Plaats"
              value={`${customer.postalCode} ${customer.city}${
                customer.country !== 'NL' ? ` (${customer.country})` : ''
              }`}
            />
          </Grid>
        </Box>

        {invoices && <CustomerInvoiceList invoices={invoices} customerName={customer.name} />}
      </Stack>

      <ConfirmDialog
        open={confirmingDelete}
        title="Klant verwijderen?"
        confirmLabel="Verwijderen"
        confirmPalette="red"
        busy={deleting}
        onConfirm={confirmDelete}
        onCancel={() => setConfirmingDelete(false)}
      >
        Weet je zeker dat je <b>{customer.name}</b> wilt verwijderen? Dit kan niet ongedaan worden
        gemaakt.
      </ConfirmDialog>
    </AppLayout>
  )
}

function Detail({
  label,
  value,
  fallback = '—',
}: {
  label: string
  value: string | null
  fallback?: string
}) {
  return (
    <Box>
      <Text fontSize="xs" color="fg.subtle" mb="0.5">
        {label}
      </Text>
      <Text fontSize="sm" color={value ? undefined : 'fg.subtle'}>
        {value || fallback}
      </Text>
    </Box>
  )
}

function CustomerInvoiceList({
  invoices,
  customerName,
}: {
  invoices: CustomerInvoices
  customerName: string
}) {
  const navigate = useNavigate()

  return (
    <Box bg="bg.panel" borderWidth="1px" borderColor="border.muted" rounded="xl" overflow="hidden">
      <Flex
        justify="space-between"
        align="center"
        gap="4"
        wrap="wrap"
        px="6"
        py="4"
        borderBottomWidth="1px"
        borderColor="border.muted"
      >
        <Text fontSize="sm" fontWeight="semibold" color="brand.fg">
          Facturen
        </Text>
        {invoices.invoices.length > 0 && (
          <HStack gap="6" fontSize="sm">
            <Figure label="Gefactureerd" value={formatAmount(invoices.totalInvoiced)} />
            <Figure label="Openstaand" value={formatAmount(invoices.outstandingAmount)} />
          </HStack>
        )}
      </Flex>

      {invoices.invoices.length === 0 ? (
        <Box py="12" px="6" textAlign="center">
          <Text fontWeight="medium" mb="1">
            Nog geen facturen voor deze klant
          </Text>
          <Text color="fg.muted" fontSize="sm" mb="5">
            Stel een factuur op voor {customerName}; het nummer volgt bij verzenden.
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
                <Table.ColumnHeader>Datum</Table.ColumnHeader>
                <Table.ColumnHeader>Vervaldatum</Table.ColumnHeader>
                <Table.ColumnHeader textAlign="end">Totaal</Table.ColumnHeader>
                <Table.ColumnHeader>Status</Table.ColumnHeader>
              </Table.Row>
            </Table.Header>
            <Table.Body>
              {invoices.invoices.map((invoice) => (
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
                  <Table.Cell color="fg.muted">{formatDate(invoice.invoiceDate)}</Table.Cell>
                  <Table.Cell color="fg.muted">{formatDate(invoice.dueDate)}</Table.Cell>
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
  )
}

function Figure({ label, value }: { label: string; value: string }) {
  return (
    <HStack gap="2">
      <Text color="fg.subtle" fontSize="xs">
        {label}
      </Text>
      <Text fontWeight="medium">{value}</Text>
    </HStack>
  )
}
