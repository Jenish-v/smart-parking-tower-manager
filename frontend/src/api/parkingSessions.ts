import { apiUrl, authorizedHeaders, request } from './http'

export { ApiError } from './http'

export type SpaceSize = 'SMALL' | 'MEDIUM' | 'LARGE'
export type SessionStatus = 'ACTIVE' | 'COMPLETED'

export interface ParkingReceipt {
  receiptId: string
  ratePlanId: string
  ratePlanVersion: number
  billableDuration: string
  billingIncrements: number
  grossChargeMinor: number
  capDiscountMinor: number
  totalMinor: number
  currency: string
  issuedAt: string
}

export type AdjustmentReason = 'CUSTOMER_SERVICE' | 'RATE_CORRECTION' | 'OPERATIONAL_EXCEPTION' | 'OTHER'

export interface FeeAdjustment {
  adjustmentId: string
  amountMinor: number
  reason: AdjustmentReason
  reasonDetail: string
  actorSubject: string
  createdAt: string
}

export interface ReceiptStatement {
  receiptId: string
  sessionId: string
  ratePlanId: string
  ratePlanVersion: number
  sizeClass: SpaceSize
  enteredAt: string
  exitedAt: string
  billableDuration: string
  billingIncrements: number
  grossChargeMinor: number
  capDiscountMinor: number
  baseTotalMinor: number
  adjustedTotalMinor: number
  currency: string
  issuedAt: string
  adjustments: FeeAdjustment[]
}

export interface FeeAdjustmentCommand {
  amountMinor: number
  reason: AdjustmentReason
  reasonDetail: string
}

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
  reservationId: string | null
  receipt: ParkingReceipt | null
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

function receiptPath(facilityId: string, sessionId: string) {
  return `/api/v1/facilities/${encodeURIComponent(facilityId)}`
    + `/parking-sessions/${encodeURIComponent(sessionId)}/receipt`
}

export function getReceiptStatement(facilityId: string, sessionId: string) {
  return request<ReceiptStatement>(receiptPath(facilityId, sessionId))
}

export function adjustFee(
  facilityId: string,
  sessionId: string,
  adjustmentId: string,
  command: FeeAdjustmentCommand,
) {
  return request<ReceiptStatement>(
    `${receiptPath(facilityId, sessionId)}/adjustments/${encodeURIComponent(adjustmentId)}`,
    {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(command),
    },
  )
}

export function getOccupancy(facilityId: string, signal?: AbortSignal) {
  return request<OccupancySnapshot>(
    `/api/v1/facilities/${encodeURIComponent(facilityId)}/occupancy`,
    { signal },
  )
}

export function openOccupancyStream(
  facilityId: string,
  onSnapshot: (snapshot: OccupancySnapshot) => void,
  onConnectionChange: (connected: boolean) => void,
) {
  const path = `/api/v1/facilities/${encodeURIComponent(facilityId)}/occupancy/stream`
  let controller = new AbortController()
  let reconnectTimer: number | null = null
  let closed = false

  const connect = async () => {
    controller = new AbortController()
    try {
      const response = await fetch(apiUrl(path), {
        headers: authorizedHeaders({ Accept: 'text/event-stream' }),
        signal: controller.signal,
      })
      if (!response.ok || !response.body) {
        throw new Error(`Occupancy stream returned HTTP ${response.status}.`)
      }
      onConnectionChange(true)
      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      while (!closed) {
        const { done, value } = await reader.read()
        if (done) {
          throw new Error('Occupancy stream closed.')
        }
        buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, '\n')
        let boundary = buffer.indexOf('\n\n')
        while (boundary >= 0) {
          const frame = buffer.slice(0, boundary)
          buffer = buffer.slice(boundary + 2)
          const lines = frame.split('\n')
          const eventName = lines.find((line) => line.startsWith('event:'))?.slice(6).trim()
          const data = lines
            .filter((line) => line.startsWith('data:'))
            .map((line) => line.slice(5).trimStart())
            .join('\n')
          if (eventName === 'occupancy' && data) {
            onSnapshot(JSON.parse(data) as OccupancySnapshot)
          }
          boundary = buffer.indexOf('\n\n')
        }
      }
    } catch {
      if (closed || controller.signal.aborted) {
        return
      }
      onConnectionChange(false)
      reconnectTimer = window.setTimeout(() => void connect(), 3_000)
    }
  }

  void connect()
  return () => {
    closed = true
    controller.abort()
    if (reconnectTimer !== null) {
      window.clearTimeout(reconnectTimer)
    }
  }
}
