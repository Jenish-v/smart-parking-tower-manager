import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'

import { App } from './App'

describe('operator application', () => {
  it('renders the maintained facility model without claiming live occupancy', () => {
    render(<MemoryRouter><App /></MemoryRouter>)

    expect(screen.getByRole('heading', { name: 'Reference tower' })).toBeInTheDocument()
    expect(screen.getByText('7,200')).toBeInTheDocument()
    expect(screen.getByText('API connection pending')).toBeInTheDocument()
  })

  it('navigates to parking operations through the main layout', async () => {
    const user = userEvent.setup()
    render(<MemoryRouter><App /></MemoryRouter>)

    await user.click(screen.getByRole('link', { name: 'Parking operations' }))

    expect(screen.getByRole('heading', { name: 'Entry and exit' })).toBeInTheDocument()
    expect(screen.getByText('Workflow integration pending')).toBeInTheDocument()
  })
})
