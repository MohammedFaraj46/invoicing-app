import {
  Box,
  Button,
  Dialog,
  Flex,
  HStack,
  Heading,
  Portal,
  Spinner,
  Table,
  Text,
} from '@chakra-ui/react'
import { useCallback, useEffect, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import type { Customer } from '../api/customers'
import { deleteCustomer, listCustomers } from '../api/customers'
import { ApiError } from '../api/client'
import { AppLayout } from '../components/AppLayout'
import { FormMessage } from '../components/FormMessage'

/** Set by the form page after a successful save. */
type LocationState = { notice?: string } | null

export function CustomersPage() {
  const navigate = useNavigate()
  const location = useLocation()

  const [customers, setCustomers] = useState<Customer[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(
    (location.state as LocationState)?.notice ?? null,
  )
  const [pendingDelete, setPendingDelete] = useState<Customer | null>(null)
  const [deleting, setDeleting] = useState(false)

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

  async function confirmDelete() {
    if (!pendingDelete) return
    setDeleting(true)
    setError(null)
    try {
      await deleteCustomer(pendingDelete.id)
      setNotice(`${pendingDelete.name} is verwijderd.`)
      setPendingDelete(null)
      await load()
    } catch (caught) {
      setPendingDelete(null)
      setError(
        caught instanceof ApiError ? caught.message : 'De klant kon niet worden verwijderd.',
      )
    } finally {
      setDeleting(false)
    }
  }

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
                  <Table.ColumnHeader textAlign="end">Acties</Table.ColumnHeader>
                </Table.Row>
              </Table.Header>
              <Table.Body>
                {customers.map((customer) => (
                  <Table.Row key={customer.id}>
                    <Table.Cell fontWeight="medium">{customer.name}</Table.Cell>
                    <Table.Cell color="fg.muted">{customer.email}</Table.Cell>
                    <Table.Cell color="fg.muted">{customer.city}</Table.Cell>
                    <Table.Cell textAlign="end">
                      <HStack gap="2" justify="flex-end">
                        <Button
                          size="xs"
                          variant="outline"
                          onClick={() => navigate(`/customers/${customer.id}/edit`)}
                        >
                          Bewerken
                        </Button>
                        <Button
                          size="xs"
                          variant="ghost"
                          color="fg.error"
                          onClick={() => setPendingDelete(customer)}
                        >
                          Verwijderen
                        </Button>
                      </HStack>
                    </Table.Cell>
                  </Table.Row>
                ))}
              </Table.Body>
            </Table.Root>
          </Table.ScrollArea>
        )}
      </Box>

      <Dialog.Root
        open={pendingDelete !== null}
        onOpenChange={(details) => {
          if (!details.open) setPendingDelete(null)
        }}
        role="alertdialog"
      >
        <Portal>
          <Dialog.Backdrop bg="blackAlpha.700" backdropFilter="blur(2px)" />
          <Dialog.Positioner>
            <Dialog.Content bg="bg.panel" borderWidth="1px" borderColor="border.muted">
              <Dialog.Header>
                <Dialog.Title>Klant verwijderen?</Dialog.Title>
              </Dialog.Header>
              <Dialog.Body>
                <Text color="fg.muted" fontSize="sm">
                  Weet je zeker dat je <b>{pendingDelete?.name}</b> wilt verwijderen? Dit kan niet
                  ongedaan worden gemaakt.
                </Text>
              </Dialog.Body>
              <Dialog.Footer>
                <Button variant="ghost" onClick={() => setPendingDelete(null)} disabled={deleting}>
                  Annuleren
                </Button>
                <Button colorPalette="red" onClick={confirmDelete} loading={deleting}>
                  Verwijderen
                </Button>
              </Dialog.Footer>
            </Dialog.Content>
          </Dialog.Positioner>
        </Portal>
      </Dialog.Root>
    </AppLayout>
  )
}
