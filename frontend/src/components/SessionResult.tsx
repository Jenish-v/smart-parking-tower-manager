import type { ParkingSession } from '../api/parkingSessions'

interface SessionResultProps {
  session: ParkingSession
  message: string
}

export function SessionResult({ session, message }: SessionResultProps) {
  return (
    <div className="operation-result" role="status">
      <p className="result-message">{message}</p>
      <p className="space-assignment">
        Floor {session.space.floorNumber} · Zone {session.space.zoneCode} · Space {session.space.spaceNumber}
      </p>
      <p className="result-meta">Vehicle {session.vehicleIdentifier} · {session.requiredSize.toLowerCase()} space</p>
    </div>
  )
}
