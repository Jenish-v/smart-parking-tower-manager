import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'

import { listVehicleHistory } from '../api/parkingSessions'
import { SessionsPage } from './SessionsPage'

vi.mock('../api/parkingSessions', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/parkingSessions')>()
  return { ...actual, listVehicleHistory: vi.fn() }
})

describe('session search', () => {
  it('shows active and completed sessions returned by the backend', async () => {
    vi.mocked(listVehicleHistory).mockResolvedValue([
      {
        sessionId: 'session-1', facilityId: 'facility-1', vehicleIdentifier: 'TOR 501', requiredSize: 'SMALL',
        status: 'ACTIVE', space: { floorNumber: 1, zoneCode: 'A', spaceNumber: 7 },
        enteredAt: '2026-08-19T20:00:00Z', exitedAt: null, reservationId: 'reservation-1', receipt: null,
      },
      {
        sessionId: 'session-2', facilityId: 'facility-1', vehicleIdentifier: 'TOR 501', requiredSize: 'SMALL',
        status: 'COMPLETED', space: { floorNumber: 2, zoneCode: 'B', spaceNumber: 18 },
        enteredAt: '2026-08-18T20:00:00Z', exitedAt: '2026-08-18T22:00:00Z', reservationId: null,
        receipt: {
          receiptId: 'receipt-1', ratePlanId: 'rate-1', ratePlanVersion: 1, billableDuration: 'PT1H50M',
          billingIncrements: 8, grossChargeMinor: 1000, capDiscountMinor: 0, totalMinor: 1000,
          currency: 'CAD', issuedAt: '2026-08-18T22:00:00Z',
        },
      },
    ])
    const user = userEvent.setup()
    render(<SessionsPage />)

    await user.type(screen.getByLabelText('Vehicle identifier'), 'TOR 501')
    await user.click(screen.getByRole('button', { name: 'Search sessions' }))

    expect(await screen.findByText('Currently parked')).toBeInTheDocument()
    expect(screen.getByText('Space 7')).toBeInTheDocument()
    expect(screen.getByText('Space 18')).toBeInTheDocument()
    expect(screen.getByText('Reservation reservation-1')).toBeInTheDocument()
    expect(screen.getByText('Receipt total 10.00 CAD')).toBeInTheDocument()
  })

  it('renders an explicit empty state', async () => {
    vi.mocked(listVehicleHistory).mockResolvedValue([])
    const user = userEvent.setup()
    render(<SessionsPage />)

    await user.type(screen.getByLabelText('Vehicle identifier'), 'NONE')
    await user.click(screen.getByRole('button', { name: 'Search sessions' }))

    expect(await screen.findByText('No sessions found for NONE.')).toBeInTheDocument()
  })
})
