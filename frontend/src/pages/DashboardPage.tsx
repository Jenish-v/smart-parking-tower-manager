import { PageHeader } from '../components/PageHeader'
import { referenceFacilityId } from '../config'
import { useOccupancy } from '../hooks/useOccupancy'

const numberFormatter = new Intl.NumberFormat('en-CA')

function formatCount(value: number) {
  return numberFormatter.format(value)
}

function utilization(occupied: number, operational: number) {
  return operational === 0 ? 0 : Math.round((occupied / operational) * 1_000) / 10
}

export function DashboardPage() {
  const { snapshot, initialLoading, refreshing, error, streamConnected, refresh } =
    useOccupancy(referenceFacilityId)

  const metrics = snapshot ? [
    { label: 'Available', value: snapshot.availableSpaces, detail: 'Operational and unoccupied' },
    { label: 'Occupied', value: snapshot.occupiedSpaces, detail: 'Active allocations' },
    { label: 'Operational', value: snapshot.operationalSpaces, detail: 'Spaces accepting vehicles' },
    { label: 'Total capacity', value: snapshot.totalSpaces, detail: '6 floors · 36 zones' },
  ] : []

  return (
    <>
      <PageHeader
        eyebrow="Operator overview"
        title="Reference tower"
        description="Current facility and floor occupancy from the parking service."
        actions={(
          <div className="occupancy-actions">
            <span className={`connection-badge ${streamConnected ? 'connected' : ''}`}>
              {streamConnected ? 'Live stream connected' : '15-second fallback active'}
            </span>
            <button className="refresh-button" type="button" onClick={() => void refresh()} disabled={refreshing}>
              {refreshing ? 'Refreshing…' : 'Refresh now'}
            </button>
          </div>
        )}
      />

      {initialLoading && <p className="occupancy-feedback" role="status">Loading occupancy…</p>}
      {error && (
        <p className="operation-error occupancy-feedback" role="alert">
          {snapshot ? 'Showing the last snapshot. ' : ''}{error}
        </p>
      )}

      {snapshot && (
        <>
          <section className="metric-grid occupancy-metrics" aria-label="Current facility occupancy">
            {metrics.map((metric) => (
              <article className="metric-card" key={metric.label}>
                <p className="metric-label">{metric.label}</p>
                <p className="metric-value">{formatCount(metric.value)}</p>
                <p className="metric-detail">{metric.detail}</p>
              </article>
            ))}
          </section>

          <section className="content-grid occupancy-content">
            <article className="panel floor-panel">
              <div className="panel-heading">
                <div>
                  <p className="eyebrow accent">Floor status</p>
                  <h2>Occupancy by floor</h2>
                </div>
                <span className="panel-total">Snapshot {new Date(snapshot.capturedAt).toLocaleTimeString()}</span>
              </div>
              <ol className="floor-list">
                {snapshot.floors.map((floor) => {
                  const percent = utilization(floor.occupiedSpaces, floor.operationalSpaces)
                  return (
                    <li key={floor.floorNumber}>
                      <div className="floor-summary">
                        <strong>Floor {floor.floorNumber}</strong>
                        <span>{formatCount(floor.availableSpaces)} available · {percent}% occupied</span>
                      </div>
                      <div className="occupancy-bar" aria-label={`Floor ${floor.floorNumber}: ${percent}% occupied`}>
                        <span style={{ width: `${percent}%` }} />
                      </div>
                    </li>
                  )
                })}
              </ol>
            </article>

            <article className="panel system-panel">
              <p className="eyebrow accent">System state</p>
              <h2>Development environment</h2>
              <dl className="status-list">
                <div><dt>Facility model</dt><dd><span className="status-dot" />Configured</dd></div>
                <div><dt>Parking API</dt><dd>Available</dd></div>
                <div><dt>Occupancy snapshot</dt><dd>Connected</dd></div>
                <div><dt>Streaming</dt><dd>{streamConnected ? 'Connected' : 'Reconnecting'}</dd></div>
                <div><dt>Authentication</dt><dd>Not implemented</dd></div>
              </dl>
            </article>
          </section>
        </>
      )}
    </>
  )
}
