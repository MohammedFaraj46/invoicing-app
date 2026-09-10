import { Box, Flex, Heading, Text } from '@chakra-ui/react'
import type { ReactNode } from 'react'

/**
 * Centred panel used by the login and registration screens.
 */
export function AuthCard({
  title,
  subtitle,
  children,
  maxW = 'md',
}: {
  title: string
  subtitle?: string
  children: ReactNode
  maxW?: string
}) {
  return (
    <Flex minH="100dvh" align="center" justify="center" px="4" py="10">
      <Box w="full" maxW={maxW}>
        <Box mb="6" textAlign="center">
          <Heading size="lg" letterSpacing="tight">
            Facturatie
            <Box as="span" color="brand.fg">
              .
            </Box>
          </Heading>
        </Box>

        <Box bg="bg.panel" borderWidth="1px" borderColor="border.muted" rounded="xl" p={{ base: '6', md: '8' }}>
          <Heading size="md" mb={subtitle ? '1' : '6'}>
            {title}
          </Heading>
          {subtitle && (
            <Text color="fg.muted" fontSize="sm" mb="6">
              {subtitle}
            </Text>
          )}
          {children}
        </Box>
      </Box>
    </Flex>
  )
}
