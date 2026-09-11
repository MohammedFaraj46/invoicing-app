import { Box, Button, Container, Flex, HStack, Heading, Link as ChakraLink, Text } from '@chakra-ui/react'
import type { ReactNode } from 'react'
import { useState } from 'react'
import { NavLink, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'

const NAV_ITEMS = [
  { to: '/', label: 'Dashboard', end: true },
  { to: '/customers', label: 'Klanten', end: false },
  { to: '/invoices', label: 'Facturen', end: false },
]

/**
 * Shell for the signed-in pages: brand, navigation, who you are, and the way out.
 */
export function AppLayout({ children }: { children: ReactNode }) {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [loggingOut, setLoggingOut] = useState(false)

  async function handleLogout() {
    setLoggingOut(true)
    try {
      await logout()
      navigate('/login', { replace: true })
    } finally {
      setLoggingOut(false)
    }
  }

  return (
    <Box minH="100dvh">
      <Box as="header" borderBottomWidth="1px" borderColor="border.muted" bg="bg.panel">
        <Container maxW="5xl" py="4">
          <Flex align="center" justify="space-between" gap="6">
            <HStack gap="8">
              <Heading size="md" letterSpacing="tight">
                Facturatie
                <Box as="span" color="brand.fg">
                  .
                </Box>
              </Heading>
              <HStack as="nav" gap="5">
                {NAV_ITEMS.map((item) => (
                  <NavItem key={item.to} to={item.to} end={item.end}>
                    {item.label}
                  </NavItem>
                ))}
              </HStack>
            </HStack>

            <HStack gap="4">
              <Text fontSize="sm" color="fg.muted" display={{ base: 'none', sm: 'block' }}>
                {user?.email}
              </Text>
              <Button size="sm" variant="outline" onClick={handleLogout} loading={loggingOut}>
                Uitloggen
              </Button>
            </HStack>
          </Flex>
        </Container>
      </Box>

      <Container maxW="5xl" py="10">
        {children}
      </Container>
    </Box>
  )
}

function NavItem({ to, end, children }: { to: string; end: boolean; children: ReactNode }) {
  return (
    <ChakraLink asChild fontSize="sm" fontWeight="medium">
      <NavLink
        to={to}
        end={end}
        style={({ isActive }) => ({
          // the theme has no "active nav" role, so the two states are spelled out
          color: isActive ? 'var(--chakra-colors-brand-fg)' : 'var(--chakra-colors-fg-muted)',
        })}
      >
        {children}
      </NavLink>
    </ChakraLink>
  )
}
