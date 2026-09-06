import { createContext, useContext } from 'react'
import { UserManager, WebStorageStateStore, type User } from 'oidc-client-ts'

import { oidcConfiguration } from '../config'

export interface AuthContextValue {
  enabled: boolean
  loading: boolean
  user: User | null
  error: string | null
  signIn: () => Promise<void>
  signOut: () => Promise<void>
}

export const AuthContext = createContext<AuthContextValue>({
  enabled: false,
  loading: false,
  user: null,
  error: null,
  signIn: () => Promise.resolve(),
  signOut: () => Promise.resolve(),
})

let userManager: UserManager | null | undefined

export function getUserManager() {
  if (userManager !== undefined) {
    return userManager
  }
  if (!oidcConfiguration) {
    userManager = null
    return userManager
  }
  userManager = new UserManager({
    authority: oidcConfiguration.authority,
    client_id: oidcConfiguration.clientId,
    redirect_uri: `${window.location.origin}/auth/callback`,
    post_logout_redirect_uri: window.location.origin,
    response_type: 'code',
    scope: oidcConfiguration.scope,
    userStore: new WebStorageStateStore({ store: window.sessionStorage }),
    stateStore: new WebStorageStateStore({ store: window.sessionStorage }),
  })
  return userManager
}

export function useAuth() {
  return useContext(AuthContext)
}
