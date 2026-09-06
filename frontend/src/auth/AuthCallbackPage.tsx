import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import type { User } from 'oidc-client-ts'

import { getUserManager } from './AuthContext'

let callbackPromise: Promise<User> | null = null

export function AuthCallbackPage() {
  const manager = getUserManager()
  const navigate = useNavigate()
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!manager) {
      void navigate('/', { replace: true })
      return
    }
    callbackPromise ??= manager.signinRedirectCallback()
    void callbackPromise
      .then((user) => {
        const state = user.state as { returnUrl?: string } | undefined
        void navigate(state?.returnUrl ?? '/', { replace: true })
      })
      .catch(() => setError('Sign-in could not be completed. Return to the dashboard and try again.'))
  }, [manager, navigate])

  return (
    <main className="auth-page">
      <p className="eyebrow accent">Identity</p>
      <h1>{error ? 'Sign-in failed' : 'Completing sign-in'}</h1>
      <p className="page-description" role={error ? 'alert' : undefined}>
        {error ?? 'Validating the authorization response.'}
      </p>
      {error && <a className="auth-button" href="/">Return to dashboard</a>}
    </main>
  )
}
