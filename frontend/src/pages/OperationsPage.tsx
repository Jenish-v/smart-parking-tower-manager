import { PageHeader } from '../components/PageHeader'

export function OperationsPage() {
  return (
    <>
      <PageHeader
        eyebrow="Parking operations"
        title="Entry and exit"
        description="The operator workflow will use the versioned parking-session API in the next delivery slice."
      />
      <section className="empty-state">
        <p className="empty-state-index">01</p>
        <div>
          <h2>Workflow integration pending</h2>
          <p>
            Entry and exit controls are intentionally unavailable until mutation feedback and stale-state handling are
            implemented.
          </p>
        </div>
      </section>
    </>
  )
}
