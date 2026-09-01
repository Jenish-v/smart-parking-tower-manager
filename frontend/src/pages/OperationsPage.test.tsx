import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'

import { ApiError, enterVehicle } from '../api/parkingSessions'
import { OperationsPage } from './OperationsPage'

vi.mock('../api/parkingSessions', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/parkingSessions')>()
  return { ...actual, enterVehicle: vi.fn(), exitVehicle: vi.fn() }
})

const activeSession = {
  sessionId: 'session-1',
  facilityId: 'facility-1',
  vehicleIdentifier: 'TOR 501',
  requiredSize: 'SMALL' as const,
  status: 'ACTIVE' as const,
  space: { floorNumber: 1, zoneCode: 'A', spaceNumber: 7 },
  enteredAt: '2026-08-19T20:00:00Z',
  exitedAt: null,
  reservationId: 'reservation-1',
}

describe('parking operations', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('submits an entry and displays the assigned space', async () => {
    vi.mocked(enterVehicle).mockResolvedValue(activeSession)
    const user = userEvent.setup()
    render(<OperationsPage />)

    await user.type(screen.getByLabelText('Vehicle identifier', { selector: '#entry-vehicle' }), 'TOR 501')
    await user.click(screen.getByRole('button', { name: 'Assign space' }))

    expect(await screen.findByText('Entry recorded')).toBeInTheDocument()
    expect(screen.getByText('Floor 1 · Zone A · Space 7')).toBeInTheDocument()
    expect(screen.getByText('Reservation reservation-1')).toBeInTheDocument()
    expect(enterVehicle).toHaveBeenCalledWith(
      'd936bb7d-3027-47aa-a47b-d04a37e07310',
      { vehicleIdentifier: 'TOR 501', requiredSize: 'SMALL' },
      expect.any(String),
    )
  })

  it('keeps the idempotency key when an entry is retried after failure', async () => {
    vi.mocked(enterVehicle)
      .mockRejectedValueOnce(new ApiError({
        type: 'about:blank', title: 'Unavailable', status: 503, detail: 'Try the entry again.',
      }))
      .mockResolvedValueOnce(activeSession)
    const user = userEvent.setup()
    render(<OperationsPage />)

    const input = screen.getByLabelText('Vehicle identifier', { selector: '#entry-vehicle' })
    await user.type(input, 'TOR 501')
    await user.click(screen.getByRole('button', { name: 'Assign space' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Try the entry again.')

    await user.click(screen.getByRole('button', { name: 'Assign space' }))
    expect(await screen.findByText('Entry recorded')).toBeInTheDocument()

    const firstKey = vi.mocked(enterVehicle).mock.calls[0][2]
    const retryKey = vi.mocked(enterVehicle).mock.calls[1][2]
    expect(retryKey).toBe(firstKey)
  })
})
