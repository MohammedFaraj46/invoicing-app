import { Box, Button, Flex, Grid, HStack, Heading, Spinner, Stack, Text } from '@chakra-ui/react'
import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ApiError } from '../api/client'
import type { CompanyInput } from '../api/company'
import { getCompany, updateCompany } from '../api/company'
import { AppLayout } from '../components/AppLayout'
import { FormMessage } from '../components/FormMessage'
import { TextField } from '../components/TextField'
import { toNumber } from '../lib/money'
import type { Errors } from '../lib/validation'
import {
  LIMITS,
  checkCountry,
  checkPaymentTerm,
  checkRequiredText,
  collect,
} from '../lib/validation'

const EMPTY: CompanyInput = {
  name: '',
  kvkNumber: '',
  vatNumber: '',
  addressLine: '',
  postalCode: '',
  city: '',
  country: 'NL',
  defaultPaymentTermDays: 30,
}

/**
 * Editing the own company profile. There is no id in the route for the same
 * reason there is none in the endpoint: the backend reads which company is meant
 * from the login cookie.
 */
export function CompanyFormPage() {
  const navigate = useNavigate()

  const [values, setValues] = useState<CompanyInput>(EMPTY)
  // kept as text while typing, so clearing the field does not snap back to a number
  const [paymentTerm, setPaymentTerm] = useState('30')
  const [errors, setErrors] = useState<Errors>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    let cancelled = false

    getCompany()
      .then((company) => {
        if (cancelled) return
        setValues({
          name: company.name,
          kvkNumber: company.kvkNumber,
          vatNumber: company.vatNumber,
          addressLine: company.addressLine,
          postalCode: company.postalCode,
          city: company.city,
          country: company.country,
          defaultPaymentTermDays: toNumber(company.defaultPaymentTermDays),
        })
        setPaymentTerm(String(toNumber(company.defaultPaymentTermDays)))
      })
      .catch((caught: unknown) => {
        if (cancelled) return
        setFormError(
          caught instanceof ApiError ? caught.message : 'De bedrijfsgegevens konden niet worden geladen.',
        )
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [])

  function update(field: keyof CompanyInput, value: string) {
    setValues((previous) => ({ ...previous, [field]: value }))
    // editing a field clears its message, so a corrected value stops looking wrong
    setErrors((previous) => {
      if (!(field in previous)) return previous
      const { [field]: _removed, ...rest } = previous
      return rest
    })
  }

  /** Local checks first, so an obviously incomplete form never leaves the browser. */
  function validate(): Errors {
    return collect([
      ['name', checkRequiredText(values.name, 'Bedrijfsnaam', LIMITS.name)],
      // KvK and VAT: required and bounded, no format — see lib/validation.ts
      ['kvkNumber', checkRequiredText(values.kvkNumber, 'KvK-nummer', LIMITS.kvkNumber)],
      ['vatNumber', checkRequiredText(values.vatNumber, 'BTW-nummer', LIMITS.vatNumber)],
      ['addressLine', checkRequiredText(values.addressLine, 'Adres', LIMITS.addressLine)],
      ['postalCode', checkRequiredText(values.postalCode, 'Postcode', LIMITS.postalCode)],
      ['city', checkRequiredText(values.city, 'Plaats', LIMITS.city)],
      ['country', checkCountry(values.country)],
      ['defaultPaymentTermDays', checkPaymentTerm(paymentTerm)],
    ])
  }

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()

    const found = validate()
    setErrors(found)
    if (Object.keys(found).length > 0) return

    setSaving(true)
    setFormError(null)

    try {
      await updateCompany({ ...values, defaultPaymentTermDays: toNumber(paymentTerm) })
      navigate('/', { replace: true, state: { notice: 'De bedrijfsgegevens zijn bijgewerkt.' } })
    } catch (caught) {
      if (caught instanceof ApiError) {
        setErrors(caught.fieldErrors)
        setFormError(
          caught.status === 400
            ? 'Niet alle gegevens zijn correct ingevuld.'
            : caught.message,
        )
      } else {
        setFormError('De server is niet bereikbaar. Probeer het later opnieuw.')
      }
    } finally {
      setSaving(false)
    }
  }

  return (
    <AppLayout>
      <Box maxW="2xl">
        <Heading size="lg" mb="1">
          Bedrijfsgegevens bewerken
        </Heading>
        <Text color="fg.muted" fontSize="sm" mb="6">
          Deze gegevens staan als afzender op je facturen en creditnota&#39;s.
        </Text>

        {formError && <FormMessage status="error">{formError}</FormMessage>}

        {loading ? (
          <Flex justify="center" py="16">
            <Spinner color="brand.solid" />
          </Flex>
        ) : (
          <Box bg="bg.panel" borderWidth="1px" borderColor="border.muted" rounded="xl" p={{ base: '6', md: '8' }}>
            <form onSubmit={handleSubmit} noValidate>
              <Stack gap="4">
                <TextField
                  label="Bedrijfsnaam"
                  name="name"
                  value={values.name}
                  onChange={(value) => update('name', value)}
                  error={errors.name}
                  maxLength={LIMITS.name}
                />
                <Grid templateColumns={{ base: '1fr', sm: '1fr 1fr' }} gap="4">
                  <TextField
                    label="KvK-nummer"
                    name="kvkNumber"
                    value={values.kvkNumber}
                    onChange={(value) => update('kvkNumber', value)}
                    error={errors.kvkNumber}
                    maxLength={LIMITS.kvkNumber}
                  />
                  <TextField
                    label="BTW-nummer"
                    name="vatNumber"
                    value={values.vatNumber}
                    onChange={(value) => update('vatNumber', value)}
                    error={errors.vatNumber}
                    maxLength={LIMITS.vatNumber}
                    placeholder="NL001234567B01"
                  />
                </Grid>
                <TextField
                  label="Adres"
                  name="addressLine"
                  value={values.addressLine}
                  onChange={(value) => update('addressLine', value)}
                  error={errors.addressLine}
                  maxLength={LIMITS.addressLine}
                />
                <Grid templateColumns={{ base: '1fr', sm: '1fr 1fr 1fr' }} gap="4">
                  <TextField
                    label="Postcode"
                    name="postalCode"
                    value={values.postalCode}
                    onChange={(value) => update('postalCode', value)}
                    error={errors.postalCode}
                    maxLength={LIMITS.postalCode}
                  />
                  <TextField
                    label="Plaats"
                    name="city"
                    value={values.city}
                    onChange={(value) => update('city', value)}
                    error={errors.city}
                    maxLength={LIMITS.city}
                  />
                  <TextField
                    label="Land"
                    name="country"
                    value={values.country}
                    onChange={(value) => update('country', value.toUpperCase())}
                    error={errors.country}
                    maxLength={LIMITS.country}
                  />
                </Grid>
                <TextField
                  label="Standaard betaaltermijn (dagen)"
                  name="defaultPaymentTermDays"
                  value={paymentTerm}
                  onChange={(value) => {
                    setPaymentTerm(value)
                    setErrors((previous) => {
                      const { defaultPaymentTermDays: _removed, ...rest } = previous
                      return rest
                    })
                  }}
                  error={errors.defaultPaymentTermDays}
                  placeholder="30"
                />
                <Text fontSize="xs" color="fg.subtle" mt="-2">
                  Wordt voorgesteld bij een nieuwe factuur; reeds verzonden facturen houden hun eigen
                  termijn.
                </Text>

                <HStack justify="flex-end" gap="3" mt="4">
                  <Button variant="ghost" type="button" onClick={() => navigate('/')} disabled={saving}>
                    Annuleren
                  </Button>
                  <Button type="submit" colorPalette="brand" loading={saving}>
                    Wijzigingen opslaan
                  </Button>
                </HStack>
              </Stack>
            </form>
          </Box>
        )}
      </Box>
    </AppLayout>
  )
}
