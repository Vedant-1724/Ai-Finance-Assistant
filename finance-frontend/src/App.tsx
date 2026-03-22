import { Suspense, lazy, useEffect, useMemo, useState } from 'react'
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
  const { user, logout, isFree, capabilities } = useAuth()
  const navigate = useNavigate()

  const [activeTab, setActiveTab] = useState<Tab>('dashboard')
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false)
  const [dashboardKey, setDashboardKey] = useState(0)

  const companyId = user!.companyId
  const isPaidWorkspace = user!.subscriptionTier !== 'FREE'
  const canUseChat = capabilities.canUseAiTools && (user!.subscriptionTier === 'ACTIVE' || user!.subscriptionTier === 'MAX')

  const tabMeta = useMemo(() => ({
    dashboard: { label: 'Dashboard', icon: '📊', section: 'Main', visible: true, locked: false, lockLabel: '' },
    charts: { label: 'Charts', icon: '📈', section: 'Main', visible: true, locked: false, lockLabel: '' },
    budget: { label: 'Budget', icon: '🎯', section: 'Main', visible: capabilities.canEditFinance, locked: false, lockLabel: '' },
    chat: {
      label: 'AI Assistant',
      icon: '🤖',
      section: 'Main',
      visible: capabilities.canUseAiTools,
      locked: !canUseChat,
      lockLabel: user!.subscriptionTier === 'TRIAL' ? 'Upgrade to Pro or Max for AI chat' : 'Upgrade to access',
    },
    invoices: {
      label: 'Invoices',
      icon: '📄',
      section: 'Finance',
      visible: capabilities.canEditFinance,
      locked: !isPaidWorkspace,
      lockLabel: 'Upgrade to access',
    },
    import: { label: 'Import', icon: '⬆️', section: 'Finance', visible: capabilities.canEditFinance, locked: false, lockLabel: '' },
    tax: { label: 'Tax & GST', icon: '🧾', section: 'Finance', visible: true, locked: !isPaidWorkspace, lockLabel: 'Upgrade to access' },
    health: { label: 'Health Score', icon: '💚', section: 'Finance', visible: true, locked: !isPaidWorkspace, lockLabel: 'Upgrade to access' },
    audit: {
      label: 'Audit Log',
      icon: '📋',
      section: 'Workspace',
      visible: capabilities.canViewAudit,
      locked: !isPaidWorkspace,
      lockLabel: 'Upgrade to access',
    },
    team: {
      label: 'Team',
      icon: '👥',
      section: 'Workspace',
      visible: capabilities.canManageTeam,
      locked: !isPaidWorkspace,
      lockLabel: 'Upgrade to access',
    },
    settings: { label: 'Settings', icon: '⚙️', section: 'Workspace', visible: true, locked: false, lockLabel: '' },
  }), [capabilities.canEditFinance, capabilities.canManageTeam, capabilities.canUseAiTools, capabilities.canViewAudit, canUseChat, isPaidWorkspace, user])

  const visibleTabs = (Object.entries(tabMeta) as Array<[Tab, typeof tabMeta[Tab]]>).filter(([, meta]) => meta.visible)
  const sections = ['Main', 'Finance', 'Workspace'] as const

  useEffect(() => {
    if (!visibleTabs.some(([tab]) => tab === activeTab)) {
      setActiveTab(visibleTabs[0]?.[0] ?? 'dashboard')
    }
  }, [activeTab, visibleTabs])

  const navTo = (tab: Tab) => {
    if (tabMeta[tab].locked) {
      navigate('/subscription')
      setSidebarOpen(false)
      return
    }
    setActiveTab(tab)
    setSidebarOpen(false)
  }

  const handleImportSuccess = () => {
    setDashboardKey(current => current + 1)
    navTo('dashboard')
  }

  const NavBtn = ({
    tab,
  }: {
    tab: Tab
  }) => (
    <button
      className={`nav-btn ${activeTab === tab ? 'active' : ''} ${tabMeta[tab].locked ? 'nav-locked' : ''}`}
      onClick={() => navTo(tab)}
      title={tabMeta[tab].locked ? tabMeta[tab].lockLabel : tabMeta[tab].label}
    >
      <span className="nav-icon">{tabMeta[tab].icon}</span>
      {!sidebarCollapsed && <span className="nav-label">{tabMeta[tab].label}</span>}
      {tabMeta[tab].locked && !sidebarCollapsed && <span className="lock-icon">🔒</span>}
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
          {sections.map(section => {
            const tabs = visibleTabs.filter(([, meta]) => meta.section === section)
            if (tabs.length === 0) {
              return null
            }

            return (
              <div key={section}>
                <div className="nav-section-label">{section}</div>
                {tabs.map(([tab]) => (
                  <NavBtn key={tab} tab={tab} />
                ))}
              </div>
            )
          })}
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
              {!sidebarCollapsed && (
                <span className="tier-badge" style={{ marginLeft: 6, background: 'rgba(148,163,184,0.18)', color: '#cbd5e1' }}>
                  {user?.role}
                </span>
              )}
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
              {capabilities.canManageBilling
                ? isFree ? '⬆️ Upgrade Workspace' : '💳 Manage Plan'
                : '💳 View Workspace Plan'}
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

        {/* ── Premium Top Navbar ── */}
        <div className="top-navbar">
          <div className="navbar-search">
            <svg className="navbar-search-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
              <circle cx="11" cy="11" r="8" />
              <line x1="21" y1="21" x2="16.65" y2="16.65" />
            </svg>
            <input
              className="navbar-search-input"
              type="text"
              placeholder="Search transactions, reports…"
              aria-label="Search"
            />
          </div>
          <div className="navbar-actions">
            <button className="navbar-btn" title="Notifications" aria-label="Notifications">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
                <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
                <path d="M13.73 21a2 2 0 0 1-3.46 0" />
              </svg>
              <span className="notif-dot" />
            </button>
            <button className="navbar-btn" title="Theme" aria-label="Toggle theme">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
                <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z" />
              </svg>
            </button>
            <div className="navbar-avatar" title={user?.email}>
              {user?.email?.[0]?.toUpperCase() ?? '?'}
            </div>
          </div>
        </div>

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

