import { Link as ChakraLink } from '@chakra-ui/react'
import type { ReactNode } from 'react'
import { Link as RouterLink } from 'react-router-dom'

/**
 * A router link styled by the theme. Chakra v3 composes with `asChild` rather
 * than swapping the element through `as`, which also keeps the router's own
 * props properly typed.
 */
export function AppLink({ to, children }: { to: string; children: ReactNode }) {
  return (
    <ChakraLink asChild color="brand.fg" fontWeight="medium">
      <RouterLink to={to}>{children}</RouterLink>
    </ChakraLink>
  )
}
