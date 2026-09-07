import {
  adjustFee,
  enterVehicle,
  findActiveSession,
  getOccupancy,
  listVehicleHistory,
  openOccupancyStream,
  type OccupancySnapshot,
} from './parkingSessions'
import { setAccessTokenProvider } from './http'

describe('parking session API client', () => {
  afterEach(() => setAccessTokenProvider(() => null))

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

  it('attaches the current bearer token to API requests', async () => {
    setAccessTokenProvider(() => 'access-token')
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ sessionId: 'session-1' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    await findActiveSession('facility-1', 'TOR 501')

    expect(new Headers(fetchMock.mock.calls[0][1]?.headers).get('Authorization'))
      .toBe('Bearer access-token')
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

  it('puts a reason-coded adjustment with a client-selected identifier', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ receiptId: 'receipt-1', adjustments: [] }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    const command = {
      amountMinor: -100,
      reason: 'CUSTOMER_SERVICE' as const,
      reasonDetail: 'Validated service recovery',
    }

    await adjustFee('facility/one', 'session/one', 'adjustment/one', command)

    const [url, options] = fetchMock.mock.calls[0]
    expect(url).toBe(
      '/api/v1/facilities/facility%2Fone/parking-sessions/session%2Fone/receipt/adjustments/adjustment%2Fone',
    )
    expect(options?.method).toBe('PUT')
    expect(options?.body).toBe(JSON.stringify(command))
  })

  it('opens an authenticated facility occupancy stream and parses events', async () => {
    setAccessTokenProvider(() => 'stream-token')
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

    const encoder = new TextEncoder()
    let releaseStream: (() => void) | undefined
    const body = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(encoder.encode(`event: occupancy\ndata: ${JSON.stringify(streamed)}\n\n`))
        releaseStream = () => controller.close()
      },
    })
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(body, { status: 200, headers: { 'Content-Type': 'text/event-stream' } }),
    )
    const close = openOccupancyStream('facility/one', onSnapshot, onConnectionChange)

    await vi.waitFor(() => expect(onSnapshot).toHaveBeenCalledWith(streamed))

    expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/facilities/facility%2Fone/occupancy/stream')
    expect(new Headers(fetchMock.mock.calls[0][1]?.headers).get('Authorization')).toBe('Bearer stream-token')
    expect(onConnectionChange).toHaveBeenCalledWith(true)
    close()
    releaseStream?.()
  })
})
