export interface ApiProblem {
  type: string
  title: string
  status: number
  detail: string
  instance?: string
  code?: string
}

export class ApiError extends Error {
  constructor(readonly problem: ApiProblem) {
    super(problem.detail)
    this.name = 'ApiError'
  }
}

const configuredBaseUrl = import.meta.env.VITE_API_BASE_URL?.replace(/\/$/, '') ?? ''

export function apiUrl(path: string) {
  return `${configuredBaseUrl}${path}`
}

export async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(apiUrl(path), {
    ...init,
    headers: {
      Accept: 'application/json',
      ...init?.headers,
    },
  })

  if (!response.ok) {
    const fallback: ApiProblem = {
      type: 'about:blank',
      title: 'Request failed',
      status: response.status,
      detail: `The parking service returned HTTP ${response.status}.`,
    }
    const problem = await response.json().catch(() => fallback) as ApiProblem
    throw new ApiError(problem)
  }

  return response.json() as Promise<T>
}
