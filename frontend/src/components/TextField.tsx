import { Field, Input } from '@chakra-ui/react'

type TextFieldProps = {
  label: string
  name: string
  value: string
  onChange: (value: string) => void
  error?: string
  type?: string
  placeholder?: string
  autoComplete?: string
  maxLength?: number
  required?: boolean
}

/**
 * One labelled input with its validation message. Wrapping it keeps the forms
 * short and makes every field look and behave the same.
 */
export function TextField({
  label,
  name,
  value,
  onChange,
  error,
  type = 'text',
  placeholder,
  autoComplete,
  maxLength,
  required = true,
}: TextFieldProps) {
  return (
    <Field.Root invalid={Boolean(error)} required={required}>
      <Field.Label>
        {label}
        {required && <Field.RequiredIndicator />}
      </Field.Label>
      <Input
        name={name}
        type={type}
        value={value}
        placeholder={placeholder}
        autoComplete={autoComplete}
        maxLength={maxLength}
        onChange={(event) => onChange(event.target.value)}
        bg="bg.subtle"
        borderColor="border.muted"
        _hover={{ borderColor: 'border.emphasized' }}
      />
      {error && <Field.ErrorText>{error}</Field.ErrorText>}
    </Field.Root>
  )
}
