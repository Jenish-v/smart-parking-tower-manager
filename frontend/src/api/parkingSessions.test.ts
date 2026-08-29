import {
  enterVehicle,
  findActiveSession,
  getOccupancy,
  listVehicleHistory,
  openOccupancyStream,
  type OccupancySnapshot,
} from './parkingSessions'

class FakeEventSource {
  static latest: FakeEventSource

  readonly close = vi.fn()
  readonly url: string
  private readonly listeners = new Map<string, EventListener[]>()

  constructor(url: string | URL) {
    this.url = url.toString()
    FakeEventSource.latest = this
  }

  addEventListener(type: string, listener: EventListener) {
    this.listeners.set(type, [...(this.listeners.get(type) ?? []), listener])
  }

  emit(type: string, event: Event) {
    this.listeners.get(type)?.forEach((listener) => listener(event))
  }
}

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

  it('sends entry commands with JSON and an idempotency key', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ sessionId: 'session-1' }), {
        status: 201,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    await enterVehicle('facility-1', { vehicleIdentifier: 'TOR 501', requiredSize: 'MEDIUM' }, 'request-1')

    const [url, options] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/v1/facilities/facility-1/parking-sessions/entries')
    expect(options?.method).toBe('POST')
    expect(new Headers(options?.headers).get('Idempotency-Key')).toBe('request-1')
    expect(options?.body).toBe(JSON.stringify({ vehicleIdentifier: 'TOR 501', requiredSize: 'MEDIUM' }))
  })

  it('requests an abortable occupancy snapshot', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ facilityId: 'facility-1', floors: [] }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    const controller = new AbortController()

    await getOccupancy('facility/one', controller.signal)

    const [url, options] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/v1/facilities/facility%2Fone/occupancy')
    expect(options?.signal).toBe(controller.signal)
  })

  it('opens and closes the facility occupancy stream', () => {
    vi.stubGlobal('EventSource', FakeEventSource)
    const onSnapshot = vi.fn()
    const onConnectionChange = vi.fn()
    const streamed: OccupancySnapshot = {
      facilityId: 'facility-1',
      capturedAt: '2026-08-22T22:00:00Z',
      totalSpaces: 7200,
      operationalSpaces: 7200,
      occupiedSpaces: 1,
      availableSpaces: 7199,
      floors: [],
    }

    const close = openOccupancyStream('facility/one', onSnapshot, onConnectionChange)
    FakeEventSource.latest.emit('open', new Event('open'))
    FakeEventSource.latest.emit(
      'occupancy',
      new MessageEvent('occupancy', { data: JSON.stringify(streamed) }),
    )

    expect(FakeEventSource.latest.url).toBe('/api/v1/facilities/facility%2Fone/occupancy/stream')
    expect(onConnectionChange).toHaveBeenCalledWith(true)
    expect(onSnapshot).toHaveBeenCalledWith(streamed)
    close()
    expect(FakeEventSource.latest.close).toHaveBeenCalledOnce()
    vi.unstubAllGlobals()
  })
})
