import { useCallback, useEffect, useRef, useState } from 'react'

import { getOccupancy, type OccupancySnapshot } from '../api/parkingSessions'

const refreshIntervalMilliseconds = 15_000

interface OccupancyState {
  snapshot: OccupancySnapshot | null
  initialLoading: boolean
  refreshing: boolean
  error: string | null
}

export function useOccupancy(facilityId: string) {
  const [state, setState] = useState<OccupancyState>({
    snapshot: null,
    initialLoading: true,
    refreshing: false,
    error: null,
  })
  const controllerRef = useRef<AbortController | null>(null)
  const requestIdRef = useRef(0)

  const refresh = useCallback(async () => {
    const requestId = ++requestIdRef.current
    controllerRef.current?.abort()
    const controller = new AbortController()
    controllerRef.current = controller

    setState((current) => ({
      ...current,
      initialLoading: current.snapshot === null,
      refreshing: current.snapshot !== null,
      error: null,
    }))

    try {
      const snapshot = await getOccupancy(facilityId, controller.signal)
      if (requestId === requestIdRef.current) {
        setState({ snapshot, initialLoading: false, refreshing: false, error: null })
      }
    } catch (error) {
      if (requestId !== requestIdRef.current || controller.signal.aborted) {
        return
      }
      setState((current) => ({
        ...current,
        initialLoading: false,
        refreshing: false,
        error: error instanceof Error ? error.message : 'Occupancy could not be refreshed.',
      }))
    }
  }, [facilityId])

  useEffect(() => {
    void refresh()
    const interval = window.setInterval(() => {
      if (document.visibilityState === 'visible') {
        void refresh()
      }
    }, refreshIntervalMilliseconds)
    const refreshWhenVisible = () => {
      if (document.visibilityState === 'visible') {
        void refresh()
      }
    }
    document.addEventListener('visibilitychange', refreshWhenVisible)

    return () => {
      window.clearInterval(interval)
      document.removeEventListener('visibilitychange', refreshWhenVisible)
      requestIdRef.current += 1
      controllerRef.current?.abort()
    }
  }, [refresh])

  return { ...state, refresh }
}
