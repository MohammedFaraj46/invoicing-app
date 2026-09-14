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
import type { Errors } from '../lib/validation'
import {
  LIMITS,
  PASSWORD_MIN_LENGTH,
  checkCountry,
  checkEmail,
  checkPassword,
  checkRequiredText,
  collect,
} from '../lib/validation'

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
    return collect([
      ['email', checkEmail(email)],
      ['password', checkPassword(password)],
    ])
  }

  /**
   * The keys match the backend's nested field paths ("company.name"), so a 400
   * from the server lands on the same inputs these messages do.
   */
  function validateCompany(): Errors {
    return collect([
      ['company.name', checkRequiredText(company.name, 'Bedrijfsnaam', LIMITS.name)],
      // KvK and VAT: required and bounded, no format — see lib/validation.ts
      ['company.kvkNumber', checkRequiredText(company.kvkNumber, 'KvK-nummer', LIMITS.kvkNumber)],
      ['company.vatNumber', checkRequiredText(company.vatNumber, 'BTW-nummer', LIMITS.vatNumber)],
      ['company.addressLine', checkRequiredText(company.addressLine, 'Adres', LIMITS.addressLine)],
      ['company.postalCode', checkRequiredText(company.postalCode, 'Postcode', LIMITS.postalCode)],
      ['company.city', checkRequiredText(company.city, 'Plaats', LIMITS.city)],
      ['company.country', checkCountry(company.country)],
    ])
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
    // both steps again: the visitor can walk back and edit after passing them
    const found = { ...validateAccount(), ...validateCompany() }
    if (Object.keys(found).length > 0) {
      setErrors(found)
      setFormError('Niet alle gegevens zijn correct ingevuld.')
      setStep(Object.keys(found).some((key) => !key.startsWith('company.')) ? 0 : 1)
      return
    }

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
            maxLength={LIMITS.email}
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
            maxLength={LIMITS.password}
          />
          <Text fontSize="xs" color="fg.subtle">
            Minimaal {PASSWORD_MIN_LENGTH} tekens.
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
            maxLength={LIMITS.name}
          />
          <Grid templateColumns={{ base: '1fr', sm: '1fr 1fr' }} gap="4">
            <TextField
              label="KvK-nummer"
              name="kvkNumber"
              value={company.kvkNumber}
              onChange={(value) => updateCompany('kvkNumber', value)}
              error={errors['company.kvkNumber']}
              maxLength={LIMITS.kvkNumber}
            />
            <TextField
              label="BTW-nummer"
              name="vatNumber"
              value={company.vatNumber}
              onChange={(value) => updateCompany('vatNumber', value)}
              error={errors['company.vatNumber']}
              maxLength={LIMITS.vatNumber}
            />
          </Grid>
          <TextField
            label="Adres"
            name="addressLine"
            value={company.addressLine}
            onChange={(value) => updateCompany('addressLine', value)}
            error={errors['company.addressLine']}
            maxLength={LIMITS.addressLine}
          />
          <Grid templateColumns={{ base: '1fr', sm: '1fr 1fr 1fr' }} gap="4">
            <TextField
              label="Postcode"
              name="postalCode"
              value={company.postalCode}
              onChange={(value) => updateCompany('postalCode', value)}
              error={errors['company.postalCode']}
              maxLength={LIMITS.postalCode}
            />
            <TextField
              label="Plaats"
              name="city"
              value={company.city}
              onChange={(value) => updateCompany('city', value)}
              error={errors['company.city']}
              maxLength={LIMITS.city}
            />
            <TextField
              label="Land"
              name="country"
              value={company.country}
              onChange={(value) => updateCompany('country', value.toUpperCase())}
              error={errors['company.country']}
              maxLength={LIMITS.country}
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
