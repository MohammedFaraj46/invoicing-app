import { Box, Text } from '@chakra-ui/react'

/**
 * Banner for a message that belongs to the form as a whole, rather than to one
 * field — a failed login, a duplicate email address.
 */
export function FormMessage({ status, children }: { status: 'error' | 'success'; children: string }) {
  const isError = status === 'error'
  return (
    <Box
      role={isError ? 'alert' : 'status'}
      bg={isError ? 'bg.error' : 'bg.success'}
      borderWidth="1px"
      borderColor={isError ? 'border.error' : 'brand.emphasized'}
      color={isError ? 'fg.error' : 'fg.success'}
      rounded="md"
      px="3"
      py="2"
      mb="4"
    >
      <Text fontSize="sm">{children}</Text>
    </Box>
  )
}
