import { createContext, useCallback, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import * as authApi from '../api/auth'
import type { CurrentUser, RegisterPayload } from '../api/auth'
import { ApiError } from '../api/client'

type AuthContextValue = {
  user: CurrentUser | null
  /** True until the initial /api/auth/me check has finished. */
  initialising: boolean
  login: (email: string, password: string) => Promise<void>
  register: (payload: RegisterPayload) => Promise<void>
  logout: () => Promise<void>
}

export const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<CurrentUser | null>(null)
  const [initialising, setInitialising] = useState(true)

  // The login cookie is httpOnly, so the only way to know whether there is a
  // session is to ask the backend.
  useEffect(() => {
    let cancelled = false

    authApi
      .getCurrentUser()
      .then((current) => {
        if (!cancelled) setUser(current)
      })
      .catch((error: unknown) => {
        // 401 simply means "not signed in"; anything else is a real problem but
        // still leaves the visitor at the login screen
        if (!(error instanceof ApiError) || !error.isUnauthorized) {
          console.error('Kon de sessie niet controleren', error)
        }
        if (!cancelled) setUser(null)
      })
      .finally(() => {
        if (!cancelled) setInitialising(false)
      })

    return () => {
      cancelled = true
    }
  }, [])

  const login = useCallback(async (email: string, password: string) => {
    setUser(await authApi.login(email, password))
  }, [])

  const register = useCallback(async (payload: RegisterPayload) => {
    await authApi.register(payload)
    // sign the new account straight in, so registering lands on the app
    setUser(await authApi.login(payload.email, payload.password))
  }, [])

  const logout = useCallback(async () => {
    try {
      await authApi.logout()
    } finally {
      setUser(null)
    }
  }, [])

  const value = useMemo(
    () => ({ user, initialising, login, register, logout }),
    [user, initialising, login, register, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
