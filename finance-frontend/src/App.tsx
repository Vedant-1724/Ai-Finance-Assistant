// PATH: finance-frontend/src/App.tsx
// UPDATED: Added Budget, Charts, Tax, Health Score, Audit, Team, Settings tabs

import { useState } from 'react'
import { Routes, Route, Navigate, useNavigate } from 'react-router-dom'
import Dashboard from './components/Dashboard'
import ChatAssistant from './components/ChatAssistant'
import InvoiceUpload from './components/InvoiceUpload'
import StatusBanner from './components/StatusBanner'
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import SubscriptionPage from './pages/SubscriptionPage'
import BudgetPlanner from './components/BudgetPlanner'
import ChartsSection from './components/ChartsSection'
import TaxPage from './pages/TaxPage'
import HealthScorePage from './pages/HealthScorePage'
import AuditLogPage from './pages/AuditLogPage'
import TeamPage from './pages/TeamPage'
import SettingsPage from './pages/SettingsPage'
import { useAuth } from './context/AuthContext'
import StatementImport from './components/StatementImport'
import './App.css'

type Tab = 'dashboard' | 'charts' | 'budget' | 'chat' | 'invoices' | 'import' | 'tax' | 'health' | 'audit' | 'team' | 'settings'

function ProtectedApp() {
  const { user, logout, isPremium, isFree } = useAuth()
  const navigate = useNavigate()
  const [activeTab, setTab] = useState<Tab>('dashboard')
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const companyId = user!.companyId

  const navTo = (tab: Tab) => { setTab(tab); setSidebarOpen(false) }

  const NavBtn = ({ tab, label, icon, locked = false }: {tab: Tab; label: string; icon: string; locked?: boolean}) => (
    <button
      className={`nav-btn ${activeTab === tab ? 'active' : ''} ${locked ? 'nav-locked' : ''}`}
      onClick={() => navTo(tab)}
    >
      <span>{icon}</span> {label}
      {locked && <span className="lock-icon">🔒</span>}
    </button>
  )

  return (
    <div className="app-shell">
      <StatusBanner onUpgrade={() => navigate('/subscription')} />

      <header className="app-header">
        <div className="header-left">
          <button className="hamburger" onClick={() => setSidebarOpen(!sidebarOpen)} aria-label="Menu">
            <span /><span /><span />
          </button>
          <div className="brand">
            <div className="brand-logo">
              <svg width="28" height="28" viewBox="0 0 28 28" fill="none">
                <rect width="28" height="28" rx="8" fill="url(#brandGrad)"/>
                <path d="M8 18l4-8 4 6 2-4 2 6" stroke="white" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                <defs>
                  <linearGradient id="brandGrad" x1="0" y1="0" x2="28" y2="28">
                    <stop stopColor="#3b82f6"/><stop offset="1" stopColor="#8b5cf6"/>
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

        <nav className={`app-nav ${sidebarOpen ? 'open' : ''}`}>
          {/* Core */}
          <div className="nav-section-label">Main</div>
          <NavBtn tab="dashboard" label="Dashboard"   icon="📊" />
          <NavBtn tab="charts"   label="Charts"       icon="📈" locked={isFree} />
          <NavBtn tab="budget"   label="Budget"       icon="🎯" />
          <NavBtn tab="chat"     label="AI Assistant" icon="🤖" />

          {/* Finance tools */}
          <div className="nav-section-label">Finance</div>
          <NavBtn tab="invoices" label="Invoices"     icon="📄" locked={isFree} />
          <NavBtn tab="import"   label="Import"       icon="⬆️" />
          <NavBtn tab="tax"      label="Tax & GST"    icon="🧾" locked={isFree} />
          <NavBtn tab="health"   label="Health Score" icon="💚" locked={isFree} />

          {/* Pro features */}
          <div className="nav-section-label">Pro</div>
          <NavBtn tab="audit"    label="Audit Log"    icon="📋" locked={isFree} />
          <NavBtn tab="team"     label="Team"         icon="👥" locked={isFree} />
          <NavBtn tab="settings" label="Settings"     icon="⚙️" />
        </nav>

        <div className="header-right">
          <span className="user-email">{user?.email}</span>
          <button className="btn-upgrade" onClick={() => navigate('/subscription')}>
            {isFree ? '⬆️ Upgrade' : '💳 Plan'}
          </button>
          <button className="btn-logout" onClick={logout}>Logout</button>
        </div>
      </header>

      <main className="app-main">
        {activeTab === 'dashboard' && <Dashboard companyId={companyId} />}
        {activeTab === 'charts'    && <ChartsSection companyId={companyId} />}
        {activeTab === 'budget'    && <BudgetPlanner companyId={companyId} />}
        {activeTab === 'chat'      && <ChatAssistant />}
        {activeTab === 'invoices'  && <InvoiceUpload />}
        {activeTab === 'import'    && <StatementImport companyId={companyId} />}
        {activeTab === 'tax'       && <TaxPage companyId={companyId} />}
        {activeTab === 'health'    && <HealthScorePage companyId={companyId} />}
        {activeTab === 'audit'     && <AuditLogPage companyId={companyId} />}
        {activeTab === 'team'      && <TeamPage companyId={companyId} />}
        {activeTab === 'settings'  && <SettingsPage />}
      </main>
    </div>
  )
}

export default function App() {
  const { user } = useAuth()
  return (
    <Routes>
      <Route path="/login"        element={!user ? <LoginPage /> : <Navigate to="/" replace />} />
      <Route path="/register"     element={!user ? <RegisterPage /> : <Navigate to="/" replace />} />
      <Route path="/subscription" element={user  ? <SubscriptionPage /> : <Navigate to="/login" replace />} />
      <Route path="/*"            element={user  ? <ProtectedApp /> : <Navigate to="/login" replace />} />
    </Routes>
  )
}
