import { Box, Button, Flex, Grid, HStack, Heading, Spinner, Stack, Text } from '@chakra-ui/react'
import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import type { CustomerInput } from '../api/customers'
import { createCustomer, getCustomer, updateCustomer } from '../api/customers'
import { ApiError } from '../api/client'
import { AppLayout } from '../components/AppLayout'
import { FormMessage } from '../components/FormMessage'
import { TextField } from '../components/TextField'
import type { Errors } from '../lib/validation'
import {
  LIMITS,
  checkCountry,
  checkEmail,
  checkOptionalText,
  checkRequiredText,
  collect,
} from '../lib/validation'

const EMPTY: CustomerInput = {
  name: '',
  email: '',
  vatNumber: '',
  addressLine: '',
  postalCode: '',
  city: '',
  country: 'NL',
}

/**
 * One form for both creating and editing: the presence of an id in the route
 * decides which endpoint it talks to, everything else is identical.
 */
export function CustomerFormPage() {
  const navigate = useNavigate()
  const { id } = useParams<{ id: string }>()
  const customerId = id ? Number(id) : null
  const isEdit = customerId !== null

  const [values, setValues] = useState<CustomerInput>(EMPTY)
  const [errors, setErrors] = useState<Errors>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [loading, setLoading] = useState(isEdit)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (customerId === null) return

    let cancelled = false
    setLoading(true)

    getCustomer(customerId)
      .then((customer) => {
        if (cancelled) return
        setValues({
          name: customer.name,
          email: customer.email,
          vatNumber: customer.vatNumber ?? '',
          addressLine: customer.addressLine,
          postalCode: customer.postalCode,
          city: customer.city,
          country: customer.country,
        })
      })
      .catch((caught: unknown) => {
        if (cancelled) return
        // a customer of another company answers 404, same as one that is gone
        setFormError(
          caught instanceof ApiError && caught.status === 404
            ? 'Deze klant bestaat niet (meer).'
            : 'De klant kon niet worden geladen.',
        )
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [customerId])

  function update(field: keyof CustomerInput, value: string) {
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
      ['name', checkRequiredText(values.name, 'Naam', LIMITS.name)],
      ['email', checkEmail(values.email)],
      ['vatNumber', checkOptionalText(values.vatNumber ?? '', 'BTW-nummer', LIMITS.vatNumber)],
      ['addressLine', checkRequiredText(values.addressLine, 'Adres', LIMITS.addressLine)],
      ['postalCode', checkRequiredText(values.postalCode, 'Postcode', LIMITS.postalCode)],
      ['city', checkRequiredText(values.city, 'Plaats', LIMITS.city)],
      ['country', checkCountry(values.country)],
    ])
  }

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()

    const found = validate()
    setErrors(found)
    if (Object.keys(found).length > 0) return

    setSaving(true)
    setFormError(null)

    const payload: CustomerInput = {
      ...values,
      // an empty optional field should be absent, not an empty string
      vatNumber: values.vatNumber?.trim() ? values.vatNumber.trim() : null,
    }

    try {
      if (isEdit) {
        await updateCustomer(customerId, payload)
      } else {
        await createCustomer(payload)
      }
      // editing starts on the customer, so it ends there; a new one has no page
      // of its own to return to yet
      navigate(isEdit ? `/customers/${customerId}` : '/customers', {
        replace: true,
        state: { notice: isEdit ? 'De klant is bijgewerkt.' : 'De klant is toegevoegd.' },
      })
    } catch (caught) {
      if (caught instanceof ApiError) {
        setErrors(caught.fieldErrors)
        if (caught.status === 400) {
          setFormError('Niet alle gegevens zijn correct ingevuld.')
        } else if (caught.status === 404) {
          setFormError('Deze klant bestaat niet (meer).')
        } else {
          setFormError(caught.message)
        }
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
          {isEdit ? 'Klant bewerken' : 'Klant toevoegen'}
        </Heading>
        <Text color="fg.muted" fontSize="sm" mb="6">
          Deze gegevens verschijnen als ontvanger op de factuur.
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
                  label="Naam"
                  name="name"
                  value={values.name}
                  onChange={(value) => update('name', value)}
                  error={errors.name}
                  maxLength={LIMITS.name}
                />
                <TextField
                  label="E-mailadres"
                  name="email"
                  type="email"
                  value={values.email}
                  onChange={(value) => update('email', value)}
                  error={errors.email}
                  placeholder="klant@bedrijf.nl"
                  maxLength={LIMITS.email}
                />
                <TextField
                  label="BTW-nummer"
                  name="vatNumber"
                  value={values.vatNumber ?? ''}
                  onChange={(value) => update('vatNumber', value)}
                  error={errors.vatNumber}
                  placeholder="Optioneel, nodig voor BTW verlegd"
                  maxLength={LIMITS.vatNumber}
                  required={false}
                />
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

                <HStack justify="flex-end" gap="3" mt="4">
                  <Button
                    variant="ghost"
                    type="button"
                    onClick={() => navigate(isEdit ? `/customers/${customerId}` : '/customers')}
                    disabled={saving}
                  >
                    Annuleren
                  </Button>
                  <Button type="submit" colorPalette="brand" loading={saving}>
                    {isEdit ? 'Wijzigingen opslaan' : 'Klant toevoegen'}
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
