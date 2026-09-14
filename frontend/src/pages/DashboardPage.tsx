import {
  Box,
  Button,
  Flex,
  Grid,
  HStack,
  Heading,
  Spinner,
  Stack,
  Text,
} from '@chakra-ui/react'
import type { ReactNode } from 'react'
import { useEffect, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { ApiError } from '../api/client'
import type { Company } from '../api/company'
import { getCompany } from '../api/company'
import type { Dashboard } from '../api/dashboard'
import { getDashboard } from '../api/dashboard'
import { AppLayout } from '../components/AppLayout'
import { FormMessage } from '../components/FormMessage'
import { StatusBadge } from '../components/StatusBadge'
import { formatAmount, formatDate } from '../lib/money'

type LocationState = { notice?: string } | null

/**
 * The landing page: who you are as a business, how you are doing, and what you
 * touched last.
 *
 * <p>The company and the figures are two calls, fired together: they are
 * independent, and waiting for one to finish before starting the other would
 * only make the page slower.
 */
export function DashboardPage() {
  const navigate = useNavigate()
  const location = useLocation()

  // set once from the message the previous page passed along; nothing here changes it
  const [notice] = useState<string | null>((location.state as LocationState)?.notice ?? null)
  const [company, setCompany] = useState<Company | null>(null)
  const [overview, setOverview] = useState<Dashboard | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false

    Promise.all([getCompany(), getDashboard()])
      .then(([loadedCompany, loadedOverview]) => {
        if (cancelled) return
        setCompany(loadedCompany)
        setOverview(loadedOverview)
      })
      .catch((caught: unknown) => {
        if (cancelled) return
        setError(
          caught instanceof ApiError ? caught.message : 'Het overzicht kon niet worden geladen.',
        )
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [])

  // drop the message from history, so a refresh does not show it again
  useEffect(() => {
    if ((location.state as LocationState)?.notice) {
      navigate(location.pathname, { replace: true, state: null })
    }
  }, [location.pathname, location.state, navigate])

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
        Overzicht
      </Heading>
      <Text color="fg.muted" fontSize="sm" mb="6">
        Hoe je bedrijf ervoor staat, in één oogopslag.
      </Text>

      {notice && <FormMessage status="success">{notice}</FormMessage>}
      {error && <FormMessage status="error">{error}</FormMessage>}

      <Stack gap="6">
        {company && <CompanyCard company={company} onEdit={() => navigate('/company/edit')} />}

        {overview && (
          <>
            <Grid templateColumns={{ base: '1fr', sm: '1fr 1fr', lg: 'repeat(4, 1fr)' }} gap="4">
              <Tile label="Openstaand" value={formatAmount(overview.outstandingAmount)} note="Verzonden, nog niet betaald" />
              <Tile label="Betaald deze maand" value={formatAmount(overview.paidThisMonth)} note="Ontvangen sinds de 1e" accent />
              <Tile
                label="Verlopen"
                value={String(overview.overdueCount)}
                note={overview.overdueCount === 1 ? 'factuur over de vervaldatum' : 'facturen over de vervaldatum'}
                danger={overview.overdueCount > 0}
                onClick={overview.overdueCount > 0 ? () => navigate('/invoices?filter=overdue') : undefined}
              />
              <Tile
                label="Concepten"
                value={String(overview.draftCount)}
                note={overview.draftCount === 1 ? 'factuur nog niet verzonden' : 'facturen nog niet verzonden'}
                onClick={overview.draftCount > 0 ? () => navigate('/invoices?filter=draft') : undefined}
              />
            </Grid>

            <RecentInvoices invoices={overview.recentInvoices} />
          </>
        )}
      </Stack>
    </AppLayout>
  )
}

/** The real profile, the way it appears on an invoice — no ids. */
function CompanyCard({ company, onEdit }: { company: Company; onEdit: () => void }) {
  return (
    <Box bg="bg.panel" borderWidth="1px" borderColor="border.muted" rounded="xl" p="6">
      <Flex justify="space-between" align="flex-start" gap="4" wrap="wrap">
        <Box minW="0">
          <Text fontSize="sm" fontWeight="semibold" color="brand.fg" mb="3">
            Mijn bedrijf
          </Text>
          <Heading size="md" mb="1">
            {company.name}
          </Heading>
          <Text color="fg.muted" fontSize="sm">
            {company.addressLine}
          </Text>
          <Text color="fg.muted" fontSize="sm" mb="4">
            {company.postalCode} {company.city}
            {company.country !== 'NL' && ` (${company.country})`}
          </Text>

          <HStack gap="8" wrap="wrap">
            <Labelled label="KvK-nummer" value={company.kvkNumber} />
            <Labelled label="BTW-nummer" value={company.vatNumber} />
          </HStack>
        </Box>

        <Button size="sm" variant="outline" onClick={onEdit}>
          Gegevens bewerken
        </Button>
      </Flex>
    </Box>
  )
}

function Labelled({ label, value }: { label: string; value: string }) {
  return (
    <Box>
      <Text fontSize="xs" color="fg.subtle" mb="0.5">
        {label}
      </Text>
      <Text fontSize="sm" fontWeight="medium">
        {value}
      </Text>
    </Box>
  )
}

/**
 * One figure. Clickable when there is something behind it to look at — a tile
 * showing zero leads to an empty list, so it stays inert rather than promising
 * a destination.
 *
 * A clickable tile is a real button via `asChild`, so the keyboard reaches it
 * and a screen reader announces it as something to press.
 */
function Tile({
  label,
  value,
  note,
  accent = false,
  danger = false,
  onClick,
}: {
  label: string
  value: string
  note: string
  accent?: boolean
  danger?: boolean
  onClick?: () => void
}) {
  const surface = {
    textAlign: 'start',
    w: 'full',
    bg: 'bg.panel',
    borderWidth: '1px',
    borderColor: 'border.muted',
    rounded: 'xl',
    p: '5',
  } as const

  const content = (
    <>
      <Text fontSize="xs" color="fg.subtle" mb="2" textTransform="uppercase" letterSpacing="wide">
        {label}
      </Text>
      <Text
        fontSize="2xl"
        fontWeight="semibold"
        lineHeight="1.2"
        color={danger ? 'fg.error' : accent ? 'brand.fg' : undefined}
      >
        {value}
      </Text>
      <Text fontSize="xs" color="fg.muted" mt="1">
        {note}
      </Text>
    </>
  )

  if (!onClick) {
    return <Box {...surface}>{content}</Box>
  }

  return (
    <Box
      asChild
      {...surface}
      cursor="pointer"
      transition="border-color 0.15s, background 0.15s"
      _hover={{ borderColor: 'brand.emphasized', bg: 'bg.subtle' }}
    >
      <button type="button" onClick={onClick}>
        {content}
      </button>
    </Box>
  )
}

function RecentInvoices({ invoices }: { invoices: Dashboard['recentInvoices'] }) {
  const navigate = useNavigate()

  return (
    <Box bg="bg.panel" borderWidth="1px" borderColor="border.muted" rounded="xl" overflow="hidden">
      <Flex justify="space-between" align="center" px="6" py="4" borderBottomWidth="1px" borderColor="border.muted">
        <Text fontSize="sm" fontWeight="semibold" color="brand.fg">
          Laatste facturen
        </Text>
        <Button size="xs" variant="ghost" onClick={() => navigate('/invoices')}>
          Alle facturen
        </Button>
      </Flex>

      {invoices.length === 0 ? (
        <Box py="12" px="6" textAlign="center">
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
        <Stack gap="0">
          {invoices.map((invoice) => (
            <Row key={invoice.id} onClick={() => navigate(`/invoices/${invoice.id}`)}>
              <Box minW="28">
                {invoice.invoiceNumber ?? (
                  <Text as="span" color="fg.subtle" fontStyle="italic">
                    Concept
                  </Text>
                )}
              </Box>
              <Text color="fg.muted" flex="1" minW="0" truncate>
                {invoice.customerName}
              </Text>
              <Text color="fg.muted" fontSize="sm" display={{ base: 'none', sm: 'block' }}>
                {formatDate(invoice.date)}
              </Text>
              <Text fontWeight="medium" minW="24" textAlign="end">
                {formatAmount(invoice.totalAmount)}
              </Text>
              <Box minW="24" textAlign="end">
                <StatusBadge status={invoice.status} />
              </Box>
            </Row>
          ))}
        </Stack>
      )}
    </Box>
  )
}

function Row({ children, onClick }: { children: ReactNode; onClick: () => void }) {
  return (
    <HStack
      asChild
      gap="4"
      w="full"
      px="6"
      py="3.5"
      textAlign="start"
      fontSize="sm"
      borderBottomWidth="1px"
      borderColor="border.muted"
      cursor="pointer"
      _last={{ borderBottomWidth: '0' }}
      _hover={{ bg: 'bg.subtle' }}
      transition="background 0.15s"
    >
      <button type="button" onClick={onClick}>
        {children}
      </button>
    </HStack>
  )
}
