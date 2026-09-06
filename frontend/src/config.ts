export const referenceFacilityId =
  import.meta.env.VITE_FACILITY_ID || 'd936bb7d-3027-47aa-a47b-d04a37e07310'

const oidcAuthority = import.meta.env.VITE_OIDC_AUTHORITY
const oidcClientId = import.meta.env.VITE_OIDC_CLIENT_ID

export const oidcConfiguration = oidcAuthority && oidcClientId
  ? {
      authority: oidcAuthority.replace(/\/$/, ''),
      clientId: oidcClientId,
      scope: import.meta.env.VITE_OIDC_SCOPE || 'openid profile',
    }
  : null

export const hasPartialOidcConfiguration = Boolean(oidcAuthority) !== Boolean(oidcClientId)
