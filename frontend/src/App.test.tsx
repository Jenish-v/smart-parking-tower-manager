import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'

import { App } from './App'

vi.mock('./api/parkingSessions', async (importOriginal) => ({
  ...await importOriginal<typeof import('./api/parkingSessions')>(),
  getOccupancy: vi.fn().mockResolvedValue({
    facilityId: 'd936bb7d-3027-47aa-a47b-d04a37e07310',
    capturedAt: '2026-08-21T22:30:00Z',
    totalSpaces: 7200,
    operationalSpaces: 7200,
    occupiedSpaces: 0,
    availableSpaces: 7200,
    floors: [],
  }),
  openOccupancyStream: vi.fn().mockReturnValue(vi.fn()),
}))

describe('operator application', () => {
  it('renders current occupancy for the maintained facility model', async () => {
    render(<MemoryRouter><App /></MemoryRouter>)

    expect(screen.getByRole('heading', { name: 'Reference tower' })).toBeInTheDocument()
    expect(await screen.findAllByText('7,200')).toHaveLength(3)
    expect(screen.getByText('15-second fallback active')).toBeInTheDocument()
  })

  it('navigates to parking operations through the main layout', async () => {
    const user = userEvent.setup()
    render(<MemoryRouter><App /></MemoryRouter>)

    await user.click(screen.getByRole('link', { name: 'Parking operations' }))

    expect(screen.getByRole('heading', { name: 'Entry and exit' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Assign space' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Complete exit' })).toBeDisabled()
  })
})
