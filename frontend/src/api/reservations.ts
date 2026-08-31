import { request } from './http'
import type { SpaceSize } from './parkingSessions'

export type ReservationStatus = 'CONFIRMED' | 'CANCELLED' | 'FULFILLED' | 'EXPIRED'

export interface Reservation {
  reservationId: string
  facilityId: string
  vehicleIdentifier: string
  requiredSize: SpaceSize
  startsAt: string
  endsAt: string
  createdAt: string
  status: ReservationStatus
  resolvedAt: string | null
}

export interface CreateReservationCommand {
  vehicleIdentifier: string
  requiredSize: SpaceSize
  startsAt: string
  endsAt: string
}

function reservationPath(facilityId: string, reservationId?: string) {
  const base = `/api/v1/facilities/${encodeURIComponent(facilityId)}/reservations`
  return reservationId ? `${base}/${encodeURIComponent(reservationId)}` : base
}

export function createReservation(
  facilityId: string,
  reservationId: string,
  command: CreateReservationCommand,
) {
  return request<Reservation>(reservationPath(facilityId, reservationId), {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(command),
  })
}

export function cancelReservation(facilityId: string, reservationId: string) {
  return request<Reservation>(reservationPath(facilityId, reservationId), { method: 'DELETE' })
}

export function listVehicleReservations(facilityId: string, vehicleIdentifier: string) {
  const query = new URLSearchParams({ vehicleIdentifier })
  return request<Reservation[]>(`${reservationPath(facilityId)}?${query.toString()}`)
}
