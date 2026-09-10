import { Box, Circle, HStack, Text } from '@chakra-ui/react'

/**
 * Progress indicator for the registration wizard: which step you are on, and
 * how many are left.
 */
export function Stepper({ steps, current }: { steps: string[]; current: number }) {
  return (
    <HStack gap="0" mb="8" align="flex-start">
      {steps.map((label, index) => {
        const isDone = index < current
        const isCurrent = index === current
        return (
          <HStack key={label} gap="0" flex={index === steps.length - 1 ? '0 0 auto' : '1'} align="center">
            <Box textAlign="center" minW="20">
              <Circle
                size="8"
                mx="auto"
                mb="1"
                fontSize="sm"
                fontWeight="semibold"
                bg={isCurrent || isDone ? 'brand.solid' : 'bg.muted'}
                color={isCurrent || isDone ? 'brand.contrast' : 'fg.subtle'}
                borderWidth="1px"
                borderColor={isCurrent ? 'brand.emphasized' : 'border.muted'}
              >
                {isDone ? '✓' : index + 1}
              </Circle>
              <Text
                fontSize="xs"
                color={isCurrent ? 'fg' : 'fg.subtle'}
                fontWeight={isCurrent ? 'medium' : 'normal'}
              >
                {label}
              </Text>
            </Box>
            {index < steps.length - 1 && (
              <Box flex="1" h="1px" bg={isDone ? 'brand.solid' : 'border.muted'} mt="-5" />
            )}
          </HStack>
        )
      })}
    </HStack>
  )
}
