import { type FormEvent, useRef, useState } from 'react'

import { ApiError, enterVehicle, exitVehicle, type ParkingSession, type SpaceSize } from '../api/parkingSessions'
import { PageHeader } from '../components/PageHeader'
import { SessionResult } from '../components/SessionResult'
import { referenceFacilityId } from '../config'

type Operation = 'entry' | 'exit'

interface Feedback {
  operation: Operation
  session?: ParkingSession
  error?: string
}

function errorMessage(error: unknown) {
  return error instanceof ApiError ? error.problem.detail : 'The parking service could not be reached. Try again.'
}

export function OperationsPage() {
  const [entryVehicle, setEntryVehicle] = useState('')
  const [requiredSize, setRequiredSize] = useState<SpaceSize>('SMALL')
  const [exitVehicleId, setExitVehicleId] = useState('')
  const [pending, setPending] = useState<Operation | null>(null)
  const [feedback, setFeedback] = useState<Feedback | null>(null)
  const retryKeys = useRef(new Map<string, string>())

  async function runOperation(
    operation: Operation,
    signature: string,
    request: (key: string) => Promise<ParkingSession>,
  ) {
    setPending(operation)
    setFeedback(null)
    const key = retryKeys.current.get(signature) ?? crypto.randomUUID()
    retryKeys.current.set(signature, key)

    try {
      const session = await request(key)
      retryKeys.current.delete(signature)
      setFeedback({ operation, session })
      if (operation === 'entry') setEntryVehicle('')
      if (operation === 'exit') setExitVehicleId('')
    } catch (error) {
      setFeedback({ operation, error: errorMessage(error) })
    } finally {
      setPending(null)
    }
  }

  function submitEntry(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const vehicleIdentifier = entryVehicle.trim()
    const signature = `entry:${vehicleIdentifier}:${requiredSize}`
    void runOperation('entry', signature, (key) =>
      enterVehicle(referenceFacilityId, { vehicleIdentifier, requiredSize }, key),
    )
  }

  function submitExit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const vehicleIdentifier = exitVehicleId.trim()
    const signature = `exit:${vehicleIdentifier}`
    void runOperation('exit', signature, (key) => exitVehicle(referenceFacilityId, vehicleIdentifier, key))
  }

  return (
    <>
      <PageHeader
        eyebrow="Parking operations"
        title="Entry and exit"
        description="Start and complete sessions in the configured 7,200-space reference facility."
      />
      <div className="workflow-grid">
        <form className="workflow-card" onSubmit={submitEntry}>
          <div className="workflow-heading"><span>01</span><h2>Admit vehicle</h2></div>
          <label htmlFor="entry-vehicle">Vehicle identifier</label>
          <input
            id="entry-vehicle"
            value={entryVehicle}
            onChange={(event) => setEntryVehicle(event.target.value)}
            maxLength={32}
            autoComplete="off"
            placeholder="TOR 501"
            required
          />
          <label htmlFor="required-size">Required space size</label>
          <select
            id="required-size"
            value={requiredSize}
            onChange={(event) => setRequiredSize(event.target.value as SpaceSize)}
          >
            <option value="SMALL">Small</option>
            <option value="MEDIUM">Medium</option>
            <option value="LARGE">Large</option>
          </select>
          <button type="submit" disabled={pending !== null || entryVehicle.trim() === ''}>
            {pending === 'entry' ? 'Assigning space…' : 'Assign space'}
          </button>
          {feedback?.operation === 'entry' && feedback.error && (
            <p className="operation-error" role="alert">{feedback.error}</p>
          )}
          {feedback?.operation === 'entry' && feedback.session && (
            <SessionResult session={feedback.session} message="Entry recorded" />
          )}
        </form>

        <form className="workflow-card" onSubmit={submitExit}>
          <div className="workflow-heading"><span>02</span><h2>Complete exit</h2></div>
          <label htmlFor="exit-vehicle">Vehicle identifier</label>
          <input
            id="exit-vehicle"
            value={exitVehicleId}
            onChange={(event) => setExitVehicleId(event.target.value)}
            maxLength={32}
            autoComplete="off"
            placeholder="TOR 501"
            required
          />
          <p className="field-note">The active space is released when the session completes.</p>
          <button type="submit" disabled={pending !== null || exitVehicleId.trim() === ''}>
            {pending === 'exit' ? 'Completing exit…' : 'Complete exit'}
          </button>
          {feedback?.operation === 'exit' && feedback.error && (
            <p className="operation-error" role="alert">{feedback.error}</p>
          )}
          {feedback?.operation === 'exit' && feedback.session && (
            <SessionResult session={feedback.session} message="Exit completed" />
          )}
        </form>
      </div>
    </>
  )
}
