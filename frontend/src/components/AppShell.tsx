import { NavLink, Outlet } from 'react-router-dom'

const navigation = [
  { to: '/', label: 'Overview', end: true },
  { to: '/operations', label: 'Parking operations' },
  { to: '/reservations', label: 'Reservations' },
  { to: '/sessions', label: 'Session search' },
]

export function AppShell() {
  return (
    <div className="app-shell">
      <a className="skip-link" href="#main-content">Skip to main content</a>
      <header className="topbar">
        <div className="brand-mark" aria-hidden="true">SP</div>
        <div>
          <p className="eyebrow">Smart Parking</p>
          <p className="product-name">Tower Operations</p>
        </div>
        <div className="environment-status">
          <span className="status-dot" aria-hidden="true" />
          Development
        </div>
      </header>
      <aside className="sidebar" aria-label="Primary navigation">
        <p className="nav-label">Workspace</p>
        <nav>
          {navigation.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) => isActive ? 'nav-link active' : 'nav-link'}
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-note">
          <p className="sidebar-note-title">Reference facility</p>
          <p>6 floors · 36 zones · 7,200 spaces</p>
        </div>
      </aside>
      <main id="main-content" className="main-content" tabIndex={-1}>
        <Outlet />
      </main>
    </div>
  )
}
