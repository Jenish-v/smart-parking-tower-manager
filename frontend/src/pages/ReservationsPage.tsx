import { type FormEvent, useRef, useState } from 'react'

import { ApiError } from '../api/http'
import {
  cancelReservation,
  createReservation,
  listVehicleReservations,
  type Reservation,
} from '../api/reservations'
import type { SpaceSize } from '../api/parkingSessions'
import { PageHeader } from '../components/PageHeader'
import { referenceFacilityId } from '../config'

function formatTime(value: string) {
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}

function requestError(error: unknown) {
  return error instanceof ApiError ? error.problem.detail : 'The parking service could not be reached. Try again.'
}

function ReservationRow({
  reservation,
  cancelling,
  onCancel,
}: {
  reservation: Reservation
  cancelling: boolean
  onCancel: (reservationId: string) => void
}) {
  return (
    <article className="reservation-row">
      <div>
        <span className={`reservation-status ${reservation.status.toLowerCase()}`}>{reservation.status}</span>
        <span>{reservation.requiredSize.toLowerCase()} space</span>
      </div>
      <div>
        <strong>{formatTime(reservation.startsAt)}</strong>
        <span>Until {formatTime(reservation.endsAt)}</span>
      </div>
      <div className="reservation-actions">
        <span className="reservation-reference">{reservation.reservationId}</span>
        {reservation.status === 'CONFIRMED' && (
          <button
            className="secondary-button"
            type="button"
            disabled={cancelling}
            onClick={() => onCancel(reservation.reservationId)}
          >
            {cancelling ? 'Cancelling…' : 'Cancel reservation'}
          </button>
        )}
      </div>
    </article>
  )
}

export function ReservationsPage() {
  const [vehicleIdentifier, setVehicleIdentifier] = useState('')
  const [requiredSize, setRequiredSize] = useState<SpaceSize>('SMALL')
  const [startsAt, setStartsAt] = useState('')
  const [endsAt, setEndsAt] = useState('')
  const [createPending, setCreatePending] = useState(false)
  const [createError, setCreateError] = useState<string | null>(null)
  const [created, setCreated] = useState<Reservation | null>(null)
  const replayIds = useRef(new Map<string, string>())

  const [searchVehicle, setSearchVehicle] = useState('')
  const [searchedVehicle, setSearchedVehicle] = useState('')
  const [reservations, setReservations] = useState<Reservation[]>([])
  const [searchPending, setSearchPending] = useState(false)
  const [searchError, setSearchError] = useState<string | null>(null)
  const [cancellingId, setCancellingId] = useState<string | null>(null)

  async function submitCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const vehicle = vehicleIdentifier.trim()
    const command = {
      vehicleIdentifier: vehicle,
      requiredSize,
      startsAt: new Date(startsAt).toISOString(),
      endsAt: new Date(endsAt).toISOString(),
    }
    const signature = JSON.stringify(command)
    const reservationId = replayIds.current.get(signature) ?? crypto.randomUUID()
    replayIds.current.set(signature, reservationId)
    setCreatePending(true)
    setCreateError(null)
    setCreated(null)
    try {
      const reservation = await createReservation(referenceFacilityId, reservationId, command)
      replayIds.current.delete(signature)
      setCreated(reservation)
      setVehicleIdentifier('')
      setStartsAt('')
      setEndsAt('')
    } catch (error) {
      setCreateError(requestError(error))
    } finally {
      setCreatePending(false)
    }
  }

  async function submitSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const vehicle = searchVehicle.trim()
    setSearchPending(true)
    setSearchError(null)
    setReservations([])
    setSearchedVehicle(vehicle)
    try {
      setReservations(await listVehicleReservations(referenceFacilityId, vehicle))
    } catch (error) {
      setSearchError(requestError(error))
    } finally {
      setSearchPending(false)
    }
  }

  async function cancel(reservationId: string) {
    setCancellingId(reservationId)
    setSearchError(null)
    try {
      const cancelled = await cancelReservation(referenceFacilityId, reservationId)
      setReservations((current) => current.map((item) =>
        item.reservationId === reservationId ? cancelled : item,
      ))
      setCreated((current) => current?.reservationId === reservationId ? cancelled : current)
    } catch (error) {
      setSearchError(requestError(error))
    } finally {
      setCancellingId(null)
    }
  }

  return (
    <>
      <PageHeader
        eyebrow="Reservation operations"
        title="Reserve future capacity"
        description="Create and manage time-bound capacity claims for the reference facility."
      />
      <div className="reservation-layout">
        <form className="workflow-card" onSubmit={(event) => void submitCreate(event)}>
          <div className="workflow-heading"><span>01</span><h2>Create reservation</h2></div>
          <label htmlFor="reservation-vehicle">Vehicle identifier</label>
          <input
            id="reservation-vehicle"
            value={vehicleIdentifier}
            onChange={(event) => setVehicleIdentifier(event.target.value)}
            maxLength={32}
            autoComplete="off"
            placeholder="TOR 501"
            required
          />
          <label htmlFor="reservation-size">Required space size</label>
          <select
            id="reservation-size"
            value={requiredSize}
            onChange={(event) => setRequiredSize(event.target.value as SpaceSize)}
          >
            <option value="SMALL">Small</option>
            <option value="MEDIUM">Medium</option>
            <option value="LARGE">Large</option>
          </select>
          <div className="date-grid">
            <div>
              <label htmlFor="reservation-start">Arrival window starts</label>
              <input
                id="reservation-start"
                type="datetime-local"
                value={startsAt}
                onChange={(event) => setStartsAt(event.target.value)}
                required
              />
            </div>
            <div>
              <label htmlFor="reservation-end">Arrival window ends</label>
              <input
                id="reservation-end"
                type="datetime-local"
                value={endsAt}
                onChange={(event) => setEndsAt(event.target.value)}
                required
              />
            </div>
          </div>
          <button
            type="submit"
            disabled={createPending || !vehicleIdentifier.trim() || !startsAt || !endsAt}
          >
            {createPending ? 'Reserving capacity…' : 'Create reservation'}
          </button>
          {createError && <p className="operation-error" role="alert">{createError}</p>}
          {created && (
            <div className="operation-result" role="status">
              <p className="result-message">Reservation confirmed</p>
              <p className="space-assignment">{created.vehicleIdentifier} · {created.requiredSize}</p>
              <p className="result-meta">
                {formatTime(created.startsAt)} to {formatTime(created.endsAt)}
              </p>
            </div>
          )}
        </form>

        <section className="reservation-guidance" aria-labelledby="capacity-guidance">
          <p className="eyebrow accent">Capacity rules</p>
          <h2 id="capacity-guidance">Claims protect compatible space pools</h2>
          <p>
            The backend checks the complete arrival window before confirming a reservation. Large claims use
            large-only capacity; medium claims use medium or large capacity; small claims can use any compatible space.
          </p>
          <p>
            Retrying the same form after a network failure preserves its reservation identifier, so the request cannot
            create a duplicate claim.
          </p>
        </section>
      </div>

      <section className="reservation-history" aria-labelledby="reservation-history-title">
        <div>
          <p className="eyebrow accent">Vehicle history</p>
          <h2 id="reservation-history-title">Find and manage reservations</h2>
        </div>
        <form className="search-form" onSubmit={(event) => void submitSearch(event)}>
          <label htmlFor="reservation-search">Vehicle identifier</label>
          <div className="search-control">
            <input
              id="reservation-search"
              value={searchVehicle}
              onChange={(event) => setSearchVehicle(event.target.value)}
              maxLength={32}
              autoComplete="off"
              placeholder="TOR 501"
              required
            />
            <button type="submit" disabled={searchPending || !searchVehicle.trim()}>
              {searchPending ? 'Searching…' : 'Search reservations'}
            </button>
          </div>
        </form>
        {searchError && <p className="operation-error search-feedback" role="alert">{searchError}</p>}
        {!searchPending && searchedVehicle && !searchError && reservations.length === 0 && (
          <p className="search-feedback" role="status">No reservations found for {searchedVehicle}.</p>
        )}
        {reservations.length > 0 && (
          <div className="reservation-list" aria-label={`Reservations for ${searchedVehicle}`}>
            <div className="session-list-heading">
              <h2>{searchedVehicle}</h2>
              <span>{reservations.length} reservations</span>
            </div>
            {reservations.map((reservation) => (
              <ReservationRow
                key={reservation.reservationId}
                reservation={reservation}
                cancelling={cancellingId === reservation.reservationId}
                onCancel={(id) => void cancel(id)}
              />
            ))}
          </div>
        )}
      </section>
    </>
  )
}
