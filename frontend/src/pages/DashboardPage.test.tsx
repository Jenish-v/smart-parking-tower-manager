import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'

import { getOccupancy, type OccupancySnapshot } from '../api/parkingSessions'
import { DashboardPage } from './DashboardPage'

vi.mock('../api/parkingSessions', () => ({ getOccupancy: vi.fn() }))

const snapshot: OccupancySnapshot = {
  facilityId: 'd936bb7d-3027-47aa-a47b-d04a37e07310',
  capturedAt: '2026-08-21T22:30:00Z',
  totalSpaces: 7200,
  operationalSpaces: 7199,
  occupiedSpaces: 3,
  availableSpaces: 7196,
  floors: [
    {
      floorNumber: 1,
      totalSpaces: 1200,
      operationalSpaces: 1200,
      occupiedSpaces: 3,
      availableSpaces: 1197,
    },
    {
      floorNumber: 6,
      totalSpaces: 1200,
      operationalSpaces: 1199,
      occupiedSpaces: 0,
      availableSpaces: 1199,
    },
  ],
}

describe('occupancy dashboard', () => {
  beforeEach(() => {
    vi.mocked(getOccupancy).mockReset().mockResolvedValue(snapshot)
  })

  it('renders current facility and floor occupancy', async () => {
    render(<DashboardPage />)

    expect(await screen.findByText('7,196')).toBeInTheDocument()
    expect(screen.getByText('Floor 1')).toBeInTheDocument()
    expect(screen.getByText('1,197 available · 0.3% occupied')).toBeInTheDocument()
    expect(screen.getByText('Connected')).toBeInTheDocument()
  })

  it('keeps the last snapshot when a refresh fails', async () => {
    const user = userEvent.setup()
    vi.mocked(getOccupancy)
      .mockResolvedValueOnce(snapshot)
      .mockRejectedValueOnce(new Error('The occupancy service is unavailable.'))
    render(<DashboardPage />)
    await screen.findByText('7,196')

    await user.click(screen.getByRole('button', { name: 'Refresh now' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Showing the last snapshot. The occupancy service is unavailable.',
    )
    expect(screen.getByText('7,196')).toBeInTheDocument()
    await waitFor(() => expect(screen.getByRole('button', { name: 'Refresh now' })).toBeEnabled())
  })

  it('refreshes a visible dashboard every 15 seconds', async () => {
    vi.useFakeTimers()
    try {
      render(<DashboardPage />)
      await act(async () => Promise.resolve())
      expect(getOccupancy).toHaveBeenCalledOnce()

      await act(async () => vi.advanceTimersByTimeAsync(15_000))

      expect(getOccupancy).toHaveBeenCalledTimes(2)
    } finally {
      vi.useRealTimers()
    }
  })
})
