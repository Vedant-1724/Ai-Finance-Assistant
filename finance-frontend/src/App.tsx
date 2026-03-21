import { Suspense, lazy, useState } from 'react'
import { Navigate, Route, Routes, useNavigate } from 'react-router-dom'
import Dashboard from './components/Dashboard'
import ChatAssistant from './components/ChatAssistant'
import InvoiceUpload from './components/InvoiceUpload'
import StatusBanner from './components/StatusBanner'
import StatementImport from './components/StatementImport'
import BudgetPlanner from './components/BudgetPlanner'
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import VerifyEmailPage from './pages/VerifyEmailPage'
import ForgotPasswordPage from './pages/ForgotPasswordPage'
import ResetPasswordPage from './pages/ResetPasswordPage'
import SubscriptionPage from './pages/SubscriptionPage'
import TaxPage from './pages/TaxPage'
import HealthScorePage from './pages/HealthScorePage'
import AuditLogPage from './pages/AuditLogPage'
import TeamPage from './pages/TeamPage'
import SettingsPage from './pages/SettingsPage'
import JoinTeamPage from './pages/JoinTeamPage'
import { useAuth } from './context/AuthContext'
import './App.css'
import './PremiumUI.css'

const ChartsSection = lazy(() => import('./components/ChartsSection'))

type Tab =
  | 'dashboard'
  | 'charts'
  | 'budget'
  | 'chat'
  | 'invoices'
  | 'import'
  | 'tax'
  | 'health'
  | 'audit'
  | 'team'
  | 'settings'

function ProtectedApp() {
  const { user, logout, isFree, isTrial } = useAuth()
  const navigate = useNavigate()

  const [activeTab, setActiveTab] = useState<Tab>('dashboard')
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false)
  const [dashboardKey, setDashboardKey] = useState(0)

  const companyId = user!.companyId

  const navTo = (tab: Tab) => {
    setActiveTab(tab)
    setSidebarOpen(false)
  }

  const handleImportSuccess = () => {
    setDashboardKey(current => current + 1)
    navTo('dashboard')
  }

  const NavBtn = ({
    tab,
    label,
    icon,
    locked = false,
  }: {
    tab: Tab
    label: string
    icon: string
    locked?: boolean
  }) => (
    <button
      className={`nav-btn ${activeTab === tab ? 'active' : ''} ${locked ? 'nav-locked' : ''}`}
      onClick={() => navTo(tab)}
      title={locked ? 'Upgrade to access' : label}
    >
      <span className="nav-icon">{icon}</span>
      {!sidebarCollapsed && <span className="nav-label">{label}</span>}
      {locked && !sidebarCollapsed && <span className="lock-icon">🔒</span>}
    </button>
  )

  return (
    <div className={`app-shell ${sidebarCollapsed ? 'sidebar-collapsed' : ''}`}>
      <aside className={`app-sidebar ${sidebarOpen ? 'open' : ''} ${sidebarCollapsed ? 'collapsed' : ''}`}>
        <div className="sidebar-header">
          <div
            className="brand"
            style={{ cursor: sidebarCollapsed ? 'pointer' : 'default' }}
            onClick={() => {
              if (sidebarCollapsed) {
                setSidebarCollapsed(false)
              }
            }}
          >
            <div className="brand-logo">
              <svg width="28" height="28" viewBox="0 0 28 28" fill="none">
                <rect width="28" height="28" rx="8" fill="url(#brandGrad)" />
                <path d="M8 18l4-8 4 6 2-4 2 6" stroke="white" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
                <defs>
                  <linearGradient id="brandGrad" x1="0" y1="0" x2="28" y2="28">
                    <stop stopColor="#3b82f6" />
                    <stop offset="1" stopColor="#8b5cf6" />
                  </linearGradient>
                </defs>
              </svg>
            </div>
            {!sidebarCollapsed && (
              <div className="brand-text">
                <span className="brand-name">FinanceAI</span>
                <span className="brand-tag">Assistant</span>
              </div>
            )}
          </div>
          {!sidebarCollapsed && (
            <button className="sidebar-toggle-btn" onClick={() => setSidebarCollapsed(current => !current)}>
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M15 18l-6-6 6-6" />
              </svg>
            </button>
          )}
        </div>

        <nav className="app-nav">
          <div className="nav-section-label">Main</div>
          <NavBtn tab="dashboard" label="Dashboard" icon="📊" />
          <NavBtn tab="charts" label="Charts" icon="📈" />
          <NavBtn tab="budget" label="Budget" icon="🎯" />
          <NavBtn tab="chat" label="AI Assistant" icon="🤖" locked={isFree || isTrial} />

          <div className="nav-section-label">Finance</div>
          <NavBtn tab="invoices" label="Invoices" icon="📄" locked={isFree} />
          <NavBtn tab="import" label="Import" icon="⬆️" />
          <NavBtn tab="tax" label="Tax & GST" icon="🧾" locked={isFree} />
          <NavBtn tab="health" label="Health Score" icon="💚" locked={isFree} />

          <div className="nav-section-label">Pro</div>
          <NavBtn tab="audit" label="Audit Log" icon="📋" locked={isFree} />
          <NavBtn tab="team" label="Team" icon="👥" locked={isFree} />
          <NavBtn tab="settings" label="Settings" icon="⚙️" />
        </nav>

        <div className="sidebar-footer">
          <div className="user-profile-sm">
            <div className="user-email-wrap">
              <span
                className={`tier-badge ${
                  user?.subscriptionTier === 'MAX'
                    ? 'max'
                    : user?.subscriptionTier === 'ACTIVE'
                      ? 'active'
                      : user?.subscriptionTier === 'TRIAL'
                        ? 'trial'
                        : 'free'
                }`}
              >
                {user?.subscriptionTier ?? 'FREE'}
              </span>
              <span className="user-email" title={user?.email}>{user?.email}</span>
            </div>
            <button className="btn-logout" onClick={() => { void logout() }} title="Logout">
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
                <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
                <polyline points="16 17 21 12 16 7" />
                <line x1="21" y1="12" x2="9" y2="12" />
              </svg>
            </button>
          </div>
          {!sidebarCollapsed && (
            <button className="btn-upgrade" onClick={() => navigate('/subscription')}>
              {isFree ? '⬆️ Upgrade to Pro' : '💳 View Plan'}
            </button>
          )}
        </div>
      </aside>

      {sidebarOpen && <div className="nav-overlay" onClick={() => setSidebarOpen(false)} />}

      <div className="app-content-wrapper">
        <header className="mobile-header">
          <button className="hamburger" onClick={() => setSidebarOpen(true)} aria-label="Open menu">
            <span />
            <span />
            <span />
          </button>
          <div className="brand-logo" style={{ transform: 'scale(0.8)' }}>
            <svg width="28" height="28" viewBox="0 0 28 28" fill="none">
              <rect width="28" height="28" rx="8" fill="url(#brandGrad2)" />
              <path d="M8 18l4-8 4 6 2-4 2 6" stroke="white" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
              <defs>
                <linearGradient id="brandGrad2" x1="0" y1="0" x2="28" y2="28">
                  <stop stopColor="#3b82f6" />
                  <stop offset="1" stopColor="#8b5cf6" />
                </linearGradient>
              </defs>
            </svg>
          </div>
          <div className="mobile-brand" style={{ marginLeft: 8 }}>FinanceAI</div>
        </header>

        <StatusBanner onUpgrade={() => navigate('/subscription')} />

        <main className="app-main">
          {activeTab === 'dashboard' && <Dashboard key={dashboardKey} companyId={companyId} onOpenCharts={() => navTo('charts')} />}
          {activeTab === 'charts' && (
            <Suspense fallback={<div className="loading">⏳ Loading charts…</div>}>
              <ChartsSection companyId={companyId} />
            </Suspense>
          )}
          {activeTab === 'budget' && <BudgetPlanner companyId={companyId} />}
          {activeTab === 'chat' && <ChatAssistant />}
          {activeTab === 'invoices' && <InvoiceUpload companyId={companyId} />}
          {activeTab === 'import' && (
            <StatementImport companyId={companyId} onImportSuccess={handleImportSuccess} />
          )}
          {activeTab === 'tax' && <TaxPage companyId={companyId} />}
          {activeTab === 'health' && <HealthScorePage companyId={companyId} />}
          {activeTab === 'audit' && <AuditLogPage companyId={companyId} />}
          {activeTab === 'team' && <TeamPage companyId={companyId} />}
          {activeTab === 'settings' && <SettingsPage />}
        </main>
      </div>
    </div>
  )
}

export default function App() {
  const { user, authReady } = useAuth()

  if (!authReady) {
    return <div className="loading">⏳ Restoring your session…</div>
  }

  return (
    <Routes>
      <Route path="/login" element={!user ? <LoginPage /> : <Navigate to="/" replace />} />
      <Route path="/register" element={!user ? <RegisterPage /> : <Navigate to="/" replace />} />
      <Route path="/verify-email" element={<VerifyEmailPage />} />
      <Route path="/forgot-password" element={!user ? <ForgotPasswordPage /> : <Navigate to="/" replace />} />
      <Route path="/reset-password" element={!user ? <ResetPasswordPage /> : <Navigate to="/" replace />} />
      <Route path="/join" element={<JoinTeamPage />} />
      <Route path="/subscription" element={user ? <SubscriptionPage /> : <Navigate to="/login" replace />} />
      <Route path="/*" element={user ? <ProtectedApp /> : <Navigate to="/login" replace />} />
    </Routes>
  )
}

