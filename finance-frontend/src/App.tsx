// PATH: finance-frontend/src/App.tsx
// FIX: <StatementImport> now receives onImportSuccess prop (was missing → TS compile error)
//      onImportSuccess triggers dashboard refresh after a statement import.

import { useState } from 'react'
import { Routes, Route, Navigate, useNavigate } from 'react-router-dom'
import Dashboard from './components/Dashboard'
import ChatAssistant from './components/ChatAssistant'
import InvoiceUpload from './components/InvoiceUpload'
import StatusBanner from './components/StatusBanner'
import StatementImport from './components/StatementImport'
import BudgetPlanner from './components/BudgetPlanner'
import ChartsSection from './components/ChartsSection'
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import SubscriptionPage from './pages/SubscriptionPage'
import TaxPage from './pages/TaxPage'
import HealthScorePage from './pages/HealthScorePage'
import AuditLogPage from './pages/AuditLogPage'
import TeamPage from './pages/TeamPage'
import SettingsPage from './pages/SettingsPage'
import { useAuth } from './context/AuthContext'
import './App.css'

type Tab =
  | 'dashboard' | 'charts' | 'budget' | 'chat'
  | 'invoices' | 'import' | 'tax' | 'health'
  | 'audit' | 'team' | 'settings'

// ── Protected shell ───────────────────────────────────────────────────────────
function ProtectedApp() {
  const { user, logout, isFree } = useAuth()
  const navigate = useNavigate()

  const [activeTab, setTab] = useState<Tab>('dashboard')
  const [sidebarOpen, setSidebar] = useState(false)
  // Key-bump to force Dashboard to re-fetch after a statement import
  const [dashboardKey, setDashboardKey] = useState(0)

  const companyId = user!.companyId

  const navTo = (tab: Tab) => { setTab(tab); setSidebar(false) }

  // Called by StatementImport → switch back to dashboard + refresh data
  const handleImportSuccess = () => {
    setDashboardKey(k => k + 1)
    navTo('dashboard')
  }

  // ── Nav button ─────────────────────────────────────────────────────────────
  const NavBtn = ({
    tab, label, icon, locked = false,
  }: { tab: Tab; label: string; icon: string; locked?: boolean }) => (
    <button
      className={`nav-btn ${activeTab === tab ? 'active' : ''} ${locked ? 'nav-locked' : ''}`}
      onClick={() => navTo(tab)}
      title={locked ? 'Upgrade to access' : label}
    >
      <span>{icon}</span>
      {label}
      {locked && <span className="lock-icon">🔒</span>}
    </button>
  )

  return (
    <div className="app-shell">
      {/* Trial / free tier reminder banner */}
      <StatusBanner onUpgrade={() => navigate('/subscription')} />

      {/* ── Header ─────────────────────────────────────────────────────────── */}
      <header className="app-header">
        <div className="header-left">
          {/* Hamburger (mobile) */}
          <button
            className="hamburger"
            onClick={() => setSidebar(!sidebarOpen)}
            aria-label="Toggle menu"
          >
            <span /><span /><span />
          </button>

          {/* Brand */}
          <div className="brand">
            <div className="brand-logo">
              <svg width="28" height="28" viewBox="0 0 28 28" fill="none">
                <rect width="28" height="28" rx="8" fill="url(#brandGrad)" />
                <path
                  d="M8 18l4-8 4 6 2-4 2 6"
                  stroke="white" strokeWidth="2"
                  strokeLinecap="round" strokeLinejoin="round"
                />
                <defs>
                  <linearGradient id="brandGrad" x1="0" y1="0" x2="28" y2="28">
                    <stop stopColor="#3b82f6" />
                    <stop offset="1" stopColor="#8b5cf6" />
                  </linearGradient>
                </defs>
              </svg>
            </div>
            <div className="brand-text">
              <span className="brand-name">FinanceAI</span>
              <span className="brand-tag">Assistant</span>
            </div>
          </div>
        </div>

        {/* ── Navigation ──────────────────────────────────────────────────── */}
        <nav className={`app-nav ${sidebarOpen ? 'open' : ''}`}>
          {/* Main */}
          <div className="nav-section-label">Main</div>
          <NavBtn tab="dashboard" label="Dashboard" icon="📊" />
          <NavBtn tab="charts" label="Charts" icon="📈" locked={isFree} />
          <NavBtn tab="budget" label="Budget" icon="🎯" />
          <NavBtn tab="chat" label="AI Assistant" icon="🤖" />

          {/* Finance */}
          <div className="nav-section-label">Finance</div>
          <NavBtn tab="invoices" label="Invoices" icon="📄" locked={isFree} />
          <NavBtn tab="import" label="Import" icon="⬆️" />
          <NavBtn tab="tax" label="Tax & GST" icon="🧾" locked={isFree} />
          <NavBtn tab="health" label="Health Score" icon="💚" locked={isFree} />

          {/* Pro */}
          <div className="nav-section-label">Pro</div>
          <NavBtn tab="audit" label="Audit Log" icon="📋" locked={isFree} />
          <NavBtn tab="team" label="Team" icon="👥" locked={isFree} />
          <NavBtn tab="settings" label="Settings" icon="⚙️" />
        </nav>

        {/* Mobile nav backdrop */}
        {sidebarOpen && (
          <div className="nav-overlay" onClick={() => setSidebar(false)} />
        )}

        {/* ── Right side ──────────────────────────────────────────────────── */}
        <div className="header-right">
          {/* Tier badge */}
          <span className={`tier-badge ${user?.subscriptionTier === 'ACTIVE' ? 'active' :
              user?.subscriptionTier === 'TRIAL' ? 'trial' : 'free'
            }`}>
            {user?.subscriptionTier ?? 'FREE'}
          </span>

          <span className="user-email">{user?.email}</span>

          <button className="btn-upgrade" onClick={() => navigate('/subscription')}>
            {isFree ? '⬆️ Upgrade' : '💳 Plan'}
          </button>

          <button className="btn-logout" onClick={logout} title="Logout">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none"
              stroke="currentColor" strokeWidth="2" strokeLinecap="round">
              <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
              <polyline points="16 17 21 12 16 7" />
              <line x1="21" y1="12" x2="9" y2="12" />
            </svg>
          </button>
        </div>
      </header>

      {/* ── Main content area ───────────────────────────────────────────────── */}
      <main className="app-main">
        {/* FIX: dashboardKey forces remount/re-fetch after import */}
        {activeTab === 'dashboard' && (
          <Dashboard key={dashboardKey} companyId={companyId} />
        )}
        {activeTab === 'charts' && <ChartsSection companyId={companyId} />}
        {activeTab === 'budget' && <BudgetPlanner companyId={companyId} />}
        {activeTab === 'chat' && <ChatAssistant />}
        {activeTab === 'invoices' && <InvoiceUpload companyId={companyId} />}

        {/* FIX: onImportSuccess is now passed — resolves TS compile error */}
        {activeTab === 'import' && (
          <StatementImport
            companyId={companyId}
            onImportSuccess={handleImportSuccess}
          />
        )}

        {activeTab === 'tax' && <TaxPage companyId={companyId} />}
        {activeTab === 'health' && <HealthScorePage companyId={companyId} />}
        {activeTab === 'audit' && <AuditLogPage companyId={companyId} />}
        {activeTab === 'team' && <TeamPage companyId={companyId} />}
        {activeTab === 'settings' && <SettingsPage />}
      </main>
    </div>
  )
}

// ── Root router ───────────────────────────────────────────────────────────────
export default function App() {
  const { user } = useAuth()

  return (
    <Routes>
      <Route
        path="/login"
        element={!user ? <LoginPage /> : <Navigate to="/" replace />}
      />
      <Route
        path="/register"
        element={!user ? <RegisterPage /> : <Navigate to="/" replace />}
      />
      <Route
        path="/subscription"
        element={user ? <SubscriptionPage /> : <Navigate to="/login" replace />}
      />
      <Route
        path="/*"
        element={user ? <ProtectedApp /> : <Navigate to="/login" replace />}
      />
    </Routes>
  )
}