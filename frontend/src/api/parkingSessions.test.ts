import { findActiveSession, listVehicleHistory } from './parkingSessions'

describe('parking session API client', () => {
  it('encodes facility and vehicle identifiers for active lookup', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ sessionId: 'session-1' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    await findActiveSession('facility/one', 'TOR 501')

    expect(fetchMock).toHaveBeenCalledOnce()
    const [url, options] = fetchMock.mock.calls[0]
    expect(url).toBe(
      '/api/v1/facilities/facility%2Fone/parking-sessions/active?vehicleIdentifier=TOR+501',
    )
    expect(new Headers(options?.headers).get('Accept')).toBe('application/json')
  })

  it('maps problem details to a typed API error', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({
        type: 'about:blank',
        title: 'Not found',
        status: 404,
        detail: 'No parking sessions were found.',
        code: 'SESSION_NOT_FOUND',
      }), { status: 404, headers: { 'Content-Type': 'application/problem+json' } }),
    )

    await expect(listVehicleHistory('facility-1', 'UNKNOWN')).rejects.toMatchObject({
      message: 'No parking sessions were found.',
      problem: { status: 404, code: 'SESSION_NOT_FOUND' },
    })
  })
})
