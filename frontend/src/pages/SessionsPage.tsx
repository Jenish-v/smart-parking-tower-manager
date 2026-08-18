import { PageHeader } from '../components/PageHeader'

export function SessionsPage() {
  return (
    <>
      <PageHeader
        eyebrow="Session search"
        title="Find a vehicle"
        description={
          'Active-session and history search will be connected to the parking-session API in the next delivery slice.'
        }
      />
      <section className="empty-state">
        <p className="empty-state-index">02</p>
        <div>
          <h2>Search integration pending</h2>
          <p>No sample records are shown. This view will display only data returned by the backend.</p>
        </div>
      </section>
    </>
  )
}
