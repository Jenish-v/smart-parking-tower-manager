import type { ParkingSession } from '../api/parkingSessions'

interface SessionResultProps {
  session: ParkingSession
  message: string
}

function formatMinorUnits(amount: number, currency: string) {
  const currencyFormat = new Intl.NumberFormat('en-CA', { style: 'currency', currency })
  const fractionDigits = currencyFormat.resolvedOptions().maximumFractionDigits ?? 2
  return `${new Intl.NumberFormat('en-CA', {
    minimumFractionDigits: fractionDigits,
    maximumFractionDigits: fractionDigits,
  }).format(amount / (10 ** fractionDigits))} ${currency}`
}

export function SessionResult({ session, message }: SessionResultProps) {
  return (
    <div className="operation-result" role="status">
      <p className="result-message">{message}</p>
      <p className="space-assignment">
        Floor {session.space.floorNumber} · Zone {session.space.zoneCode} · Space {session.space.spaceNumber}
      </p>
      <p className="result-meta">Vehicle {session.vehicleIdentifier} · {session.requiredSize.toLowerCase()} space</p>
      {session.reservationId && <p className="result-meta">Reservation {session.reservationId}</p>}
      {session.receipt && (
        <div className="receipt-summary">
          <p className="receipt-total">Total {formatMinorUnits(session.receipt.totalMinor, session.receipt.currency)}</p>
          <p className="result-meta">
            Receipt {session.receipt.receiptId} · {session.receipt.billingIncrements} billing increments
          </p>
        </div>
      )}
    </div>
  )
}
