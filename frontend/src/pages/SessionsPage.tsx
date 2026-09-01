import { type FormEvent, useState } from 'react'

import { ApiError, listVehicleHistory, type ParkingSession } from '../api/parkingSessions'
import { PageHeader } from '../components/PageHeader'
import { referenceFacilityId } from '../config'

function formatTime(value: string) {
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}

export function SessionsPage() {
  const [vehicle, setVehicle] = useState('')
  const [searchedVehicle, setSearchedVehicle] = useState('')
  const [sessions, setSessions] = useState<ParkingSession[]>([])
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function submitSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const vehicleIdentifier = vehicle.trim()
    setPending(true)
    setError(null)
    setSessions([])
    setSearchedVehicle(vehicleIdentifier)
    try {
      setSessions(await listVehicleHistory(referenceFacilityId, vehicleIdentifier))
    } catch (requestError) {
      setError(
        requestError instanceof ApiError
          ? requestError.problem.detail
          : 'The parking service could not be reached. Try again.',
      )
    } finally {
      setPending(false)
    }
  }

  return (
    <>
      <PageHeader
        eyebrow="Session search"
        title="Find a vehicle"
        description="Inspect the active location and completed parking history returned by the backend."
      />
      <form className="search-form" onSubmit={(event) => void submitSearch(event)}>
        <label htmlFor="search-vehicle">Vehicle identifier</label>
        <div className="search-control">
          <input
            id="search-vehicle"
            value={vehicle}
            onChange={(event) => setVehicle(event.target.value)}
            maxLength={32}
            autoComplete="off"
            placeholder="TOR 501"
            required
          />
          <button type="submit" disabled={pending || vehicle.trim() === ''}>
            {pending ? 'Searching…' : 'Search sessions'}
          </button>
        </div>
      </form>
      {error && <p className="operation-error search-feedback" role="alert">{error}</p>}
      {!pending && searchedVehicle && !error && sessions.length === 0 && (
        <p className="search-feedback" role="status">No sessions found for {searchedVehicle}.</p>
      )}
      {sessions.length > 0 && (
        <section className="session-list" aria-label={`Parking sessions for ${searchedVehicle}`}>
          <div className="session-list-heading"><h2>{searchedVehicle}</h2><span>{sessions.length} sessions</span></div>
          {sessions.map((session) => (
            <article className="session-row" key={session.sessionId}>
              <div><span className={`session-status ${session.status.toLowerCase()}`}>{session.status}</span></div>
              <div>
                <strong>Floor {session.space.floorNumber}, Zone {session.space.zoneCode}</strong>
                <span>Space {session.space.spaceNumber}</span>
              </div>
              <div>
                <strong>{formatTime(session.enteredAt)}</strong>
                <span>{session.exitedAt ? `Exited ${formatTime(session.exitedAt)}` : 'Currently parked'}</span>
                {session.reservationId && <span>Reservation {session.reservationId}</span>}
              </div>
            </article>
          ))}
        </section>
      )}
    </>
  )
}
