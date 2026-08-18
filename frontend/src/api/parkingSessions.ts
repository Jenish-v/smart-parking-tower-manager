export type SpaceSize = 'SMALL' | 'MEDIUM' | 'LARGE'
export type SessionStatus = 'ACTIVE' | 'COMPLETED'

export interface ParkingSession {
  sessionId: string
  facilityId: string
  vehicleIdentifier: string
  requiredSize: SpaceSize
  floorNumber: number
  zoneCode: string
  spaceNumber: number
  status: SessionStatus
  enteredAt: string
  exitedAt: string | null
}

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

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${configuredBaseUrl}${path}`, {
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

export function findActiveSession(facilityId: string, vehicleIdentifier: string) {
  const query = new URLSearchParams({ vehicleIdentifier })
  return request<ParkingSession>(
    `/api/v1/facilities/${encodeURIComponent(facilityId)}/parking-sessions/active?${query.toString()}`,
  )
}

export function listVehicleHistory(facilityId: string, vehicleIdentifier: string) {
  const query = new URLSearchParams({ vehicleIdentifier })
  return request<ParkingSession[]>(
    `/api/v1/facilities/${encodeURIComponent(facilityId)}/parking-sessions?${query.toString()}`,
  )
}
