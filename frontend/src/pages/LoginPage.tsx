import { Button, Stack, Text } from '@chakra-ui/react'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ApiError } from '../api/client'
import { AppLink } from '../components/AppLink'
import { AuthCard } from '../components/AuthCard'
import { FormMessage } from '../components/FormMessage'
import { TextField } from '../components/TextField'
import { useAuth } from '../auth/useAuth'

export function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  /** Editing a field clears its message, so a corrected value stops looking wrong. */
  function clearError(key: string) {
    setFieldErrors((previous) => {
      if (!(key in previous)) return previous
      const { [key]: _removed, ...rest } = previous
      return rest
    })
  }

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    setFieldErrors({})
    setFormError(null)
    setSubmitting(true)

    try {
      await login(email, password)
      navigate('/', { replace: true })
    } catch (error) {
      if (error instanceof ApiError) {
        setFieldErrors(error.fieldErrors)
        // the backend answers 401 with the same message either way, on purpose
        setFormError(
          error.isUnauthorized ? 'E-mailadres of wachtwoord klopt niet.' : error.message,
        )
      } else {
        setFormError('De server is niet bereikbaar. Probeer het later opnieuw.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthCard title="Inloggen" subtitle="Log in om je facturen te beheren.">
      {formError && <FormMessage status="error">{formError}</FormMessage>}

      <form onSubmit={handleSubmit} noValidate>
        <Stack gap="4">
          <TextField
            label="E-mailadres"
            name="email"
            type="email"
            value={email}
            onChange={(value) => {
              setEmail(value)
              clearError('email')
            }}
            error={fieldErrors.email}
            placeholder="jij@bedrijf.nl"
            autoComplete="email"
          />
          <TextField
            label="Wachtwoord"
            name="password"
            type="password"
            value={password}
            onChange={(value) => {
              setPassword(value)
              clearError('password')
            }}
            error={fieldErrors.password}
            autoComplete="current-password"
          />

          <Button type="submit" colorPalette="brand" loading={submitting} mt="2">
            Inloggen
          </Button>
        </Stack>
      </form>

      <Text fontSize="sm" color="fg.muted" mt="6" textAlign="center">
        Nog geen account?{' '}
        <AppLink to="/register">Registreren</AppLink>
      </Text>
    </AuthCard>
  )
}
