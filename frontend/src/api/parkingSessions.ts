import { apiUrl, request } from './http'

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
  operatorReference: string
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
  operatorReference: string
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
  const source = new EventSource(apiUrl(path))
  source.addEventListener('occupancy', (event) => {
    try {
      onSnapshot(JSON.parse((event as MessageEvent<string>).data) as OccupancySnapshot)
    } catch {
      source.close()
      onConnectionChange(false)
    }
  })
  source.addEventListener('open', () => onConnectionChange(true))
  source.addEventListener('error', () => onConnectionChange(false))
  return () => source.close()
}
