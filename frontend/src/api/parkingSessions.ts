export type SpaceSize = 'SMALL' | 'MEDIUM' | 'LARGE'
export type SessionStatus = 'ACTIVE' | 'COMPLETED'

export interface ParkingSession {
  sessionId: string
  facilityId: string
  vehicleIdentifier: string
  requiredSize: SpaceSize
  space: {
    floorNumber: number
    zoneCode: string
    spaceNumber: number
  }
  status: SessionStatus
  enteredAt: string
  exitedAt: string | null
}

export interface OccupancySnapshot {
  facilityId: string
  capturedAt: string
  totalSpaces: number
  operationalSpaces: number
  occupiedSpaces: number
  availableSpaces: number
  floors: FloorOccupancy[]
}

export interface FloorOccupancy {
  floorNumber: number
  totalSpaces: number
  operationalSpaces: number
  occupiedSpaces: number
  availableSpaces: number
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

export interface EntryCommand {
  vehicleIdentifier: string
  requiredSize: SpaceSize
}

function mutateSession(path: string, body: EntryCommand | { vehicleIdentifier: string }, idempotencyKey: string) {
  return request<ParkingSession>(path, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Idempotency-Key': idempotencyKey,
    },
    body: JSON.stringify(body),
  })
}

export function enterVehicle(facilityId: string, command: EntryCommand, idempotencyKey: string) {
  return mutateSession(
    `/api/v1/facilities/${encodeURIComponent(facilityId)}/parking-sessions/entries`,
    command,
    idempotencyKey,
  )
}

export function exitVehicle(facilityId: string, vehicleIdentifier: string, idempotencyKey: string) {
  return mutateSession(
    `/api/v1/facilities/${encodeURIComponent(facilityId)}/parking-sessions/exits`,
    { vehicleIdentifier },
    idempotencyKey,
  )
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

export function getOccupancy(facilityId: string, signal?: AbortSignal) {
  return request<OccupancySnapshot>(
    `/api/v1/facilities/${encodeURIComponent(facilityId)}/occupancy`,
    { signal },
  )
}
