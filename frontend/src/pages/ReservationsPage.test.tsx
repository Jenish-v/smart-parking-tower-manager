import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'

import { ApiError } from '../api/http'
import {
  cancelReservation,
  createReservation,
  listVehicleReservations,
  type Reservation,
} from '../api/reservations'
import { ReservationsPage } from './ReservationsPage'

vi.mock('../api/reservations', async (importOriginal) => ({
  ...await importOriginal<typeof import('../api/reservations')>(),
  cancelReservation: vi.fn(),
  createReservation: vi.fn(),
  listVehicleReservations: vi.fn(),
}))

const confirmed: Reservation = {
  reservationId: '8ab2e819-f05b-41d7-b54d-32e59c9f035d',
  facilityId: 'd936bb7d-3027-47aa-a47b-d04a37e07310',
  vehicleIdentifier: 'TOR 901',
  requiredSize: 'MEDIUM',
  startsAt: '2026-09-01T14:00:00Z',
  endsAt: '2026-09-01T15:00:00Z',
  createdAt: '2026-08-31T20:00:00Z',
  status: 'CONFIRMED',
  resolvedAt: null,
}

describe('reservation operations', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('creates a reservation and preserves its identifier across a safe retry', async () => {
    vi.mocked(createReservation)
      .mockRejectedValueOnce(new ApiError({
        type: 'about:blank', title: 'Unavailable', status: 503, detail: 'Try the reservation again.',
      }))
      .mockResolvedValueOnce(confirmed)
    const user = userEvent.setup()
    render(<ReservationsPage />)

    await user.type(screen.getByLabelText('Vehicle identifier', { selector: '#reservation-vehicle' }), 'TOR 901')
    await user.selectOptions(screen.getByLabelText('Required space size'), 'MEDIUM')
    await user.type(screen.getByLabelText('Arrival window starts'), '2026-09-01T14:00')
    await user.type(screen.getByLabelText('Arrival window ends'), '2026-09-01T15:00')
    await user.click(screen.getByRole('button', { name: 'Create reservation' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Try the reservation again.')

    await user.click(screen.getByRole('button', { name: 'Create reservation' }))
    expect(await screen.findByText('Reservation confirmed')).toBeInTheDocument()
    expect(vi.mocked(createReservation).mock.calls[1][1])
      .toBe(vi.mocked(createReservation).mock.calls[0][1])
  })

  it('searches reservation history and cancels a confirmed reservation', async () => {
    vi.mocked(listVehicleReservations).mockResolvedValue([confirmed])
    vi.mocked(cancelReservation).mockResolvedValue({
      ...confirmed,
      status: 'CANCELLED',
      resolvedAt: '2026-08-31T21:00:00Z',
    })
    const user = userEvent.setup()
    render(<ReservationsPage />)

    await user.type(screen.getByLabelText('Vehicle identifier', { selector: '#reservation-search' }), 'TOR 901')
    await user.click(screen.getByRole('button', { name: 'Search reservations' }))

    expect(await screen.findByText('CONFIRMED')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Cancel reservation' }))
    expect(await screen.findByText('CANCELLED')).toBeInTheDocument()
    expect(cancelReservation).toHaveBeenCalledWith(
      'd936bb7d-3027-47aa-a47b-d04a37e07310',
      confirmed.reservationId,
    )
  })
})
