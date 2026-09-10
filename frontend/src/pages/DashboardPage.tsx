import { Box, Button, HStack, Heading, Stack, Text } from '@chakra-ui/react'
import { useNavigate } from 'react-router-dom'
import { AppLayout } from '../components/AppLayout'
import { useAuth } from '../auth/useAuth'

export function DashboardPage() {
  const { user } = useAuth()
  const navigate = useNavigate()

  return (
    <AppLayout>
      <Heading size="lg" mb="2">
        Welkom terug
      </Heading>
      <Text color="fg.muted" mb="8">
        Beheer je klanten en stel straks je facturen op.
      </Text>

      <Stack direction={{ base: 'column', md: 'row' }} gap="6" align="flex-start">
        <Box
          bg="bg.panel"
          borderWidth="1px"
          borderColor="border.muted"
          rounded="xl"
          p="6"
          flex="1"
          minW="0"
        >
          <Text fontSize="sm" fontWeight="semibold" color="brand.fg" mb="4">
            Ingelogde gebruiker
          </Text>
          <Stack gap="2">
            <DetailRow label="Gebruikers-id" value={String(user?.id ?? '')} />
            <DetailRow label="E-mailadres" value={user?.email ?? ''} />
            <DetailRow label="Bedrijfs-id" value={String(user?.companyId ?? '')} />
          </Stack>
        </Box>

        <Box
          bg="bg.panel"
          borderWidth="1px"
          borderColor="border.muted"
          rounded="xl"
          p="6"
          flex="1"
          minW="0"
        >
          <Text fontSize="sm" fontWeight="semibold" color="brand.fg" mb="2">
            Klanten
          </Text>
          <Text color="fg.muted" fontSize="sm" mb="5">
            Voeg de bedrijven toe waaraan je factureert.
          </Text>
          <Button size="sm" colorPalette="brand" onClick={() => navigate('/customers')}>
            Naar klanten
          </Button>
        </Box>
      </Stack>
    </AppLayout>
  )
}

function DetailRow({ label, value }: { label: string; value: string }) {
  return (
    <HStack justify="space-between" fontSize="sm" borderBottomWidth="1px" borderColor="border.muted" py="1">
      <Text color="fg.muted">{label}</Text>
      <Text>{value}</Text>
    </HStack>
  )
}
