import {
  cancelReservation,
  createReservation,
  listVehicleReservations,
} from './reservations'

describe('reservation API client', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('creates a reservation with a client-selected replay identifier', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ reservationId: 'reservation-1' }), {
        status: 201,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    const command = {
      vehicleIdentifier: 'TOR 901',
      requiredSize: 'MEDIUM' as const,
      startsAt: '2026-09-01T14:00:00.000Z',
      endsAt: '2026-09-01T15:00:00.000Z',
    }

    await createReservation('facility/one', 'reservation/one', command)

    const [url, options] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/v1/facilities/facility%2Fone/reservations/reservation%2Fone')
    expect(options?.method).toBe('PUT')
    expect(new Headers(options?.headers).get('Content-Type')).toBe('application/json')
    expect(options?.body).toBe(JSON.stringify(command))
  })

  it('lists encoded vehicle history and cancels by identifier', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(
        new Response('[]', { status: 200, headers: { 'Content-Type': 'application/json' } }),
      )
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ reservationId: 'reservation-1' }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
      )

    await listVehicleReservations('facility-1', 'TOR 901')
    await cancelReservation('facility-1', 'reservation-1')

    expect(fetchMock.mock.calls[0][0]).toBe(
      '/api/v1/facilities/facility-1/reservations?vehicleIdentifier=TOR+901',
    )
    expect(fetchMock.mock.calls[1][0]).toBe(
      '/api/v1/facilities/facility-1/reservations/reservation-1',
    )
    expect(fetchMock.mock.calls[1][1]?.method).toBe('DELETE')
  })
})
