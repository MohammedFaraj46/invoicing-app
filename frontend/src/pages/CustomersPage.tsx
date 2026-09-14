import { Box, Button, Flex, Heading, Spinner, Table, Text } from '@chakra-ui/react'
import { useCallback, useEffect, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import type { Customer } from '../api/customers'
import { listCustomers } from '../api/customers'
import { ApiError } from '../api/client'
import { AppLayout } from '../components/AppLayout'
import { FormMessage } from '../components/FormMessage'

/** Set by the form and detail pages after a successful save or delete. */
type LocationState = { notice?: string } | null

/**
 * The customer list. A row leads to the customer, where the profile, the
 * invoices and the edit and delete actions live — the same shape the invoice
 * list has, and it keeps a whole row clickable instead of carrying buttons that
 * have to swallow the click.
 */
export function CustomersPage() {
  const navigate = useNavigate()
  const location = useLocation()

  const [customers, setCustomers] = useState<Customer[]>([])
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
      setCustomers(await listCustomers())
    } catch (caught) {
      setError(
        caught instanceof ApiError ? caught.message : 'De klanten konden niet worden opgehaald.',
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
            Klanten
          </Heading>
          <Text color="fg.muted" fontSize="sm">
            De klanten waaraan je factureert.
          </Text>
        </Box>
        <Button colorPalette="brand" onClick={() => navigate('/customers/new')}>
          Klant toevoegen
        </Button>
      </Flex>

      {notice && <FormMessage status="success">{notice}</FormMessage>}
      {error && <FormMessage status="error">{error}</FormMessage>}

      <Box bg="bg.panel" borderWidth="1px" borderColor="border.muted" rounded="xl" overflow="hidden">
        {loading ? (
          <Flex justify="center" py="16">
            <Spinner color="brand.solid" />
          </Flex>
        ) : customers.length === 0 ? (
          <Box py="16" textAlign="center" px="6">
            <Text fontWeight="medium" mb="1">
              Nog geen klanten
            </Text>
            <Text color="fg.muted" fontSize="sm" mb="5">
              Voeg je eerste klant toe om facturen te kunnen opstellen.
            </Text>
            <Button colorPalette="brand" size="sm" onClick={() => navigate('/customers/new')}>
              Klant toevoegen
            </Button>
          </Box>
        ) : (
          <Table.ScrollArea>
            <Table.Root size="md" interactive>
              <Table.Header>
                <Table.Row bg="bg.subtle">
                  <Table.ColumnHeader>Naam</Table.ColumnHeader>
                  <Table.ColumnHeader>E-mailadres</Table.ColumnHeader>
                  <Table.ColumnHeader>Plaats</Table.ColumnHeader>
                </Table.Row>
              </Table.Header>
              <Table.Body>
                {customers.map((customer) => (
                  <Table.Row
                    key={customer.id}
                    onClick={() => navigate(`/customers/${customer.id}`)}
                    cursor="pointer"
                  >
                    <Table.Cell fontWeight="medium">{customer.name}</Table.Cell>
                    <Table.Cell color="fg.muted">{customer.email}</Table.Cell>
                    <Table.Cell color="fg.muted">{customer.city}</Table.Cell>
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
