import { Center, Spinner } from '@chakra-ui/react'
import type { ReactNode } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { useAuth } from './auth/useAuth'
import { CustomerFormPage } from './pages/CustomerFormPage'
import { CustomersPage } from './pages/CustomersPage'
import { DashboardPage } from './pages/DashboardPage'
import { LoginPage } from './pages/LoginPage'
import { RegisterPage } from './pages/RegisterPage'

/** Keeps signed-out visitors out of the app pages. */
function RequireAuth({ children }: { children: ReactNode }) {
  const { user, initialising } = useAuth()

  if (initialising) return <FullPageSpinner />
  if (!user) return <Navigate to="/login" replace />
  return <>{children}</>
}

/** Sends already signed-in visitors away from login and register. */
function RedirectIfSignedIn({ children }: { children: ReactNode }) {
  const { user, initialising } = useAuth()

  if (initialising) return <FullPageSpinner />
  if (user) return <Navigate to="/" replace />
  return <>{children}</>
}

function FullPageSpinner() {
  return (
    <Center minH="100dvh">
      <Spinner size="lg" color="brand.solid" />
    </Center>
  )
}

export default function App() {
  return (
    <Routes>
      <Route
        path="/login"
        element={
          <RedirectIfSignedIn>
            <LoginPage />
          </RedirectIfSignedIn>
        }
      />
      <Route
        path="/register"
        element={
          <RedirectIfSignedIn>
            <RegisterPage />
          </RedirectIfSignedIn>
        }
      />
      <Route
        path="/"
        element={
          <RequireAuth>
            <DashboardPage />
          </RequireAuth>
        }
      />
      <Route
        path="/customers"
        element={
          <RequireAuth>
            <CustomersPage />
          </RequireAuth>
        }
      />
      <Route
        path="/customers/new"
        element={
          <RequireAuth>
            <CustomerFormPage />
          </RequireAuth>
        }
      />
      <Route
        path="/customers/:id/edit"
        element={
          <RequireAuth>
            <CustomerFormPage />
          </RequireAuth>
        }
      />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
