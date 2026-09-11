import { Button, Dialog, Portal, Text } from '@chakra-ui/react'
import type { ReactNode } from 'react'

/**
 * Confirmation before something that cannot be undone. Shared by deleting a
 * draft and sending an invoice.
 */
export function ConfirmDialog({
  open,
  title,
  confirmLabel,
  confirmPalette = 'brand',
  busy = false,
  onConfirm,
  onCancel,
  children,
}: {
  open: boolean
  title: string
  confirmLabel: string
  confirmPalette?: string
  busy?: boolean
  onConfirm: () => void
  onCancel: () => void
  children: ReactNode
}) {
  return (
    <Dialog.Root
      open={open}
      onOpenChange={(details) => {
        if (!details.open) onCancel()
      }}
      role="alertdialog"
    >
      <Portal>
        <Dialog.Backdrop bg="blackAlpha.700" backdropFilter="blur(2px)" />
        <Dialog.Positioner>
          <Dialog.Content bg="bg.panel" borderWidth="1px" borderColor="border.muted">
            <Dialog.Header>
              <Dialog.Title>{title}</Dialog.Title>
            </Dialog.Header>
            <Dialog.Body>
              <Text color="fg.muted" fontSize="sm">
                {children}
              </Text>
            </Dialog.Body>
            <Dialog.Footer>
              <Button variant="ghost" onClick={onCancel} disabled={busy}>
                Annuleren
              </Button>
              <Button colorPalette={confirmPalette} onClick={onConfirm} loading={busy}>
                {confirmLabel}
              </Button>
            </Dialog.Footer>
          </Dialog.Content>
        </Dialog.Positioner>
      </Portal>
    </Dialog.Root>
  )
}
