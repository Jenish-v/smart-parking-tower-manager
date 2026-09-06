import { useEffect, useMemo, useState, type ReactNode } from 'react'
import type { User } from 'oidc-client-ts'

import { setAccessTokenProvider } from '../api/http'
import { hasPartialOidcConfiguration } from '../config'
import { AuthContext, getUserManager, type AuthContextValue } from './AuthContext'

export function AuthProvider({ children }: { children: ReactNode }) {
  const manager = getUserManager()
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(Boolean(manager))
  const [error, setError] = useState<string | null>(
    hasPartialOidcConfiguration ? 'OIDC authority and client ID must be configured together.' : null,
  )

  useEffect(() => {
    if (!manager) {
      setAccessTokenProvider(() => null)
      return
    }
    const updateUser = (nextUser: User | null) => {
      setUser(nextUser)
      setAccessTokenProvider(() => nextUser?.access_token ?? null)
    }
    const removeUser = () => updateUser(null)
    manager.events.addUserLoaded(updateUser)
    manager.events.addUserUnloaded(removeUser)
    manager.events.addAccessTokenExpired(removeUser)
    void manager.getUser()
      .then(updateUser)
      .catch(() => setError('The saved sign-in session could not be restored.'))
      .finally(() => setLoading(false))
    return () => {
      manager.events.removeUserLoaded(updateUser)
      manager.events.removeUserUnloaded(removeUser)
      manager.events.removeAccessTokenExpired(removeUser)
      setAccessTokenProvider(() => null)
    }
  }, [manager])

  const value = useMemo<AuthContextValue>(() => ({
    enabled: Boolean(manager),
    loading,
    user,
    error,
    signIn: () => manager
      ? manager.signinRedirect({ state: { returnUrl: window.location.pathname } })
      : Promise.resolve(),
    signOut: () => manager ? manager.signoutRedirect() : Promise.resolve(),
  }), [error, loading, manager, user])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
