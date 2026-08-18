import { PageHeader } from '../components/PageHeader'

const facilityFacts = [
  { label: 'Floors', value: '6', detail: 'Numbered 1–6' },
  { label: 'Zones', value: '36', detail: 'A–F on every floor' },
  { label: 'Spaces', value: '7,200', detail: '200 in each zone' },
]

export function DashboardPage() {
  return (
    <>
      <PageHeader
        eyebrow="Operator overview"
        title="Reference tower"
        description="Configuration is available. Live occupancy will appear after the reporting endpoint is connected."
        actions={<span className="connection-badge">API connection pending</span>}
      />

      <section className="metric-grid" aria-label="Reference facility configuration">
        {facilityFacts.map((fact) => (
          <article className="metric-card" key={fact.label}>
            <p className="metric-label">{fact.label}</p>
            <p className="metric-value">{fact.value}</p>
            <p className="metric-detail">{fact.detail}</p>
          </article>
        ))}
      </section>

      <section className="content-grid">
        <article className="panel">
          <div className="panel-heading">
            <div>
              <p className="eyebrow accent">Space distribution</p>
              <h2>Per zone</h2>
            </div>
            <span className="panel-total">200 spaces</span>
          </div>
          <div className="distribution-bar" aria-label="100 small, 80 medium, and 20 large spaces">
            <span className="small" style={{ width: '50%' }} />
            <span className="medium" style={{ width: '40%' }} />
            <span className="large" style={{ width: '10%' }} />
          </div>
          <ul className="legend">
            <li><span className="legend-swatch small" />Small <strong>100</strong></li>
            <li><span className="legend-swatch medium" />Medium <strong>80</strong></li>
            <li><span className="legend-swatch large" />Large <strong>20</strong></li>
          </ul>
        </article>

        <article className="panel system-panel">
          <p className="eyebrow accent">System state</p>
          <h2>Development environment</h2>
          <dl className="status-list">
            <div><dt>Facility model</dt><dd><span className="status-dot" />Configured</dd></div>
            <div><dt>Parking API</dt><dd>Available</dd></div>
            <div><dt>Live occupancy</dt><dd>Next slice</dd></div>
            <div><dt>Authentication</dt><dd>Not implemented</dd></div>
          </dl>
        </article>
      </section>
    </>
  )
}
