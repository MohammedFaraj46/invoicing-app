import { Box, Button, Grid, HStack, Stack, Text } from '@chakra-ui/react'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import type { CompanyDetails } from '../api/auth'
import { ApiError } from '../api/client'
import { AppLink } from '../components/AppLink'
import { AuthCard } from '../components/AuthCard'
import { FormMessage } from '../components/FormMessage'
import { Stepper } from '../components/Stepper'
import { TextField } from '../components/TextField'
import { useAuth } from '../auth/useAuth'

const STEPS = ['Account', 'Bedrijf', 'Controleren']

const EMPTY_COMPANY: CompanyDetails = {
  name: '',
  kvkNumber: '',
  vatNumber: '',
  addressLine: '',
  postalCode: '',
  city: '',
  country: 'NL',
}

type Errors = Record<string, string>

export function RegisterPage() {
  const { register } = useAuth()
  const navigate = useNavigate()

  const [step, setStep] = useState(0)
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [company, setCompany] = useState<CompanyDetails>(EMPTY_COMPANY)
  const [errors, setErrors] = useState<Errors>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  /** Editing a field clears its message, so a corrected value stops looking wrong. */
  function clearError(key: string) {
    setErrors((previous) => {
      if (!(key in previous)) return previous
      const { [key]: _removed, ...rest } = previous
      return rest
    })
  }

  function updateCompany(field: keyof CompanyDetails, value: string) {
    setCompany((previous) => ({ ...previous, [field]: value }))
    clearError(`company.${field}`)
  }

  /** Local checks, so a step is only left once it is filled in sensibly. */
  function validateAccount(): Errors {
    const found: Errors = {}
    if (!email.trim()) found.email = 'E-mailadres is verplicht'
    else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) found.email = 'Vul een geldig e-mailadres in'
    if (!password) found.password = 'Wachtwoord is verplicht'
    else if (password.length < 8) found.password = 'Minimaal 8 tekens'
    return found
  }

  function validateCompany(): Errors {
    const found: Errors = {}
    const required: Array<[keyof CompanyDetails, string]> = [
      ['name', 'Bedrijfsnaam is verplicht'],
      ['kvkNumber', 'KvK-nummer is verplicht'],
      ['vatNumber', 'BTW-nummer is verplicht'],
      ['addressLine', 'Adres is verplicht'],
      ['postalCode', 'Postcode is verplicht'],
      ['city', 'Plaats is verplicht'],
    ]
    for (const [field, message] of required) {
      if (!company[field].trim()) found[`company.${field}`] = message
    }
    if (company.country.trim().length !== 2) {
      found['company.country'] = 'Gebruik een landcode van 2 letters, bijv. NL'
    }
    return found
  }

  function goNext() {
    const found = step === 0 ? validateAccount() : validateCompany()
    setErrors(found)
    if (Object.keys(found).length === 0) {
      setFormError(null)
      setStep(step + 1)
    }
  }

  function goBack() {
    setFormError(null)
    setStep(step - 1)
  }

  async function handleSubmit() {
    setSubmitting(true)
    setFormError(null)
    setErrors({})

    try {
      await register({ email, password, company })
      navigate('/', { replace: true })
    } catch (error) {
      if (error instanceof ApiError) {
        setErrors(error.fieldErrors)

        if (error.status === 409) {
          setFormError('Er bestaat al een account met dit e-mailadres.')
          setStep(0)
        } else if (error.status === 400) {
          // send the visitor back to the step that actually has the problem
          const keys = Object.keys(error.fieldErrors)
          const accountProblem = keys.some((key) => key === 'email' || key === 'password')
          setFormError('Niet alle gegevens zijn correct ingevuld.')
          setStep(accountProblem ? 0 : 1)
        } else {
          setFormError(error.message)
        }
      } else {
        setFormError('De server is niet bereikbaar. Probeer het later opnieuw.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthCard
      title="Account aanmaken"
      subtitle="In drie stappen klaar om je eerste factuur te versturen."
      maxW="xl"
    >
      <Stepper steps={STEPS} current={step} />

      {formError && <FormMessage status="error">{formError}</FormMessage>}

      {step === 0 && (
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
            error={errors.email}
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
            error={errors.password}
            autoComplete="new-password"
          />
          <Text fontSize="xs" color="fg.subtle">
            Minimaal 8 tekens.
          </Text>
        </Stack>
      )}

      {step === 1 && (
        <Stack gap="4">
          <TextField
            label="Bedrijfsnaam"
            name="companyName"
            value={company.name}
            onChange={(value) => updateCompany('name', value)}
            error={errors['company.name']}
          />
          <Grid templateColumns={{ base: '1fr', sm: '1fr 1fr' }} gap="4">
            <TextField
              label="KvK-nummer"
              name="kvkNumber"
              value={company.kvkNumber}
              onChange={(value) => updateCompany('kvkNumber', value)}
              error={errors['company.kvkNumber']}
              maxLength={20}
            />
            <TextField
              label="BTW-nummer"
              name="vatNumber"
              value={company.vatNumber}
              onChange={(value) => updateCompany('vatNumber', value)}
              error={errors['company.vatNumber']}
              maxLength={20}
            />
          </Grid>
          <TextField
            label="Adres"
            name="addressLine"
            value={company.addressLine}
            onChange={(value) => updateCompany('addressLine', value)}
            error={errors['company.addressLine']}
          />
          <Grid templateColumns={{ base: '1fr', sm: '1fr 1fr 1fr' }} gap="4">
            <TextField
              label="Postcode"
              name="postalCode"
              value={company.postalCode}
              onChange={(value) => updateCompany('postalCode', value)}
              error={errors['company.postalCode']}
              maxLength={20}
            />
            <TextField
              label="Plaats"
              name="city"
              value={company.city}
              onChange={(value) => updateCompany('city', value)}
              error={errors['company.city']}
            />
            <TextField
              label="Land"
              name="country"
              value={company.country}
              onChange={(value) => updateCompany('country', value.toUpperCase())}
              error={errors['company.country']}
              maxLength={2}
            />
          </Grid>
        </Stack>
      )}

      {step === 2 && (
        <Stack gap="5">
          <ReviewSection title="Account" rows={[['E-mailadres', email], ['Wachtwoord', '••••••••']]} />
          <ReviewSection
            title="Bedrijf"
            rows={[
              ['Naam', company.name],
              ['KvK-nummer', company.kvkNumber],
              ['BTW-nummer', company.vatNumber],
              ['Adres', company.addressLine],
              ['Postcode', company.postalCode],
              ['Plaats', company.city],
              ['Land', company.country],
            ]}
          />
          <Text fontSize="xs" color="fg.subtle">
            Deze gegevens verschijnen als afzender op je facturen. Je kunt ze later aanpassen.
          </Text>
        </Stack>
      )}

      <HStack mt="8" justify="space-between">
        <Button variant="ghost" onClick={goBack} disabled={step === 0 || submitting}>
          Terug
        </Button>
        {step < STEPS.length - 1 ? (
          <Button colorPalette="brand" onClick={goNext}>
            Volgende
          </Button>
        ) : (
          <Button colorPalette="brand" onClick={handleSubmit} loading={submitting}>
            Account aanmaken
          </Button>
        )}
      </HStack>

      <Text fontSize="sm" color="fg.muted" mt="6" textAlign="center">
        Heb je al een account?{' '}
        <AppLink to="/login">Inloggen</AppLink>
      </Text>
    </AuthCard>
  )
}

function ReviewSection({ title, rows }: { title: string; rows: Array<[string, string]> }) {
  return (
    <Box>
      <Text fontSize="sm" fontWeight="semibold" color="brand.fg" mb="2">
        {title}
      </Text>
      <Stack gap="1">
        {rows.map(([label, value]) => (
          <HStack key={label} justify="space-between" fontSize="sm" borderBottomWidth="1px" borderColor="border.muted" py="1">
            <Text color="fg.muted">{label}</Text>
            <Text>{value}</Text>
          </HStack>
        ))}
      </Stack>
    </Box>
  )
}
