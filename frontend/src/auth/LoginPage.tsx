import { useState } from 'react'

import { useAuth } from './AuthContext'

export function LoginPage() {
  const { error, signIn } = useAuth()
  const [starting, setStarting] = useState(false)

  const beginSignIn = async () => {
    setStarting(true)
    try {
      await signIn()
    } finally {
      setStarting(false)
    }
  }

  return (
    <main className="auth-page">
      <div className="brand-mark" aria-hidden="true">SP</div>
      <p className="eyebrow accent">Smart Parking</p>
      <h1>Operator sign-in</h1>
      <p className="page-description">
        Use your organization account to access parking operations.
      </p>
      {error && <p className="form-message error" role="alert">{error}</p>}
      <button
        className="auth-button"
        type="button"
        disabled={starting || Boolean(error)}
        onClick={() => void beginSignIn()}
      >
        {starting ? 'Redirecting…' : 'Continue with identity provider'}
      </button>
    </main>
  )
}
