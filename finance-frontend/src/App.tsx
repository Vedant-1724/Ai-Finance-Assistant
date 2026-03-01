// PATH: finance-frontend/src/App.tsx

import { useState } from 'react'
import { Routes, Route, Navigate, useNavigate } from 'react-router-dom'
import Dashboard from './components/Dashboard'
import ChatAssistant from './components/ChatAssistant'
import InvoiceUpload from './components/InvoiceUpload'
import StatusBanner from './components/StatusBanner'
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import SubscriptionPage from './pages/SubscriptionPage'
import { useAuth } from './context/AuthContext'
import './App.css'

type Tab = 'dashboard' | 'chat' | 'invoices'

function ProtectedApp() {
  const { user, logout, isPremium, isFree } = useAuth()
  const navigate                            = useNavigate()
  const [activeTab, setTab]                 = useState<Tab>('dashboard')
  const [sidebarOpen, setSidebarOpen]       = useState(false)
  const companyId                           = user!.companyId

  return (
    <div className="app-shell">
      {/* Status Banner */}
      <StatusBanner onUpgrade={() => navigate('/subscription')} />

      {/* Header */}
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
                    <stop stopColor="#3b82f6"/>
                    <stop offset="1" stopColor="#8b5cf6"/>
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
          <button
            className={`nav-btn ${activeTab === 'dashboard' ? 'active' : ''}`}
            onClick={() => { setTab('dashboard'); setSidebarOpen(false) }}
          >
            <svg width="16" height="16" viewBox="0 0 16 16" fill="currentColor">
              <rect x="1" y="1" width="6" height="6" rx="1"/><rect x="9" y="1" width="6" height="6" rx="1"/>
              <rect x="1" y="9" width="6" height="6" rx="1"/><rect x="9" y="9" width="6" height="6" rx="1"/>
            </svg>
            Dashboard
          </button>
          <button
            className={`nav-btn ${activeTab === 'chat' ? 'active' : ''}`}
            onClick={() => { setTab('chat'); setSidebarOpen(false) }}
          >
            <svg width="16" height="16" viewBox="0 0 16 16" fill="currentColor">
              <path d="M14 1H2a1 1 0 00-1 1v9a1 1 0 001 1h3l2 3 2-3h5a1 1 0 001-1V2a1 1 0 00-1-1z"/>
            </svg>
            AI Assistant
            {isFree && <span className="nav-badge">3/day</span>}
          </button>
          <button
            className={`nav-btn ${activeTab === 'invoices' ? 'active' : ''} ${isFree ? 'nav-locked' : ''}`}
            onClick={() => { setTab('invoices'); setSidebarOpen(false) }}
          >
            <svg width="16" height="16" viewBox="0 0 16 16" fill="currentColor">
              <path d="M10 1H3a1 1 0 00-1 1v12a1 1 0 001 1h10a1 1 0 001-1V5l-4-4zM9 2l3 3H9V2z"/>
            </svg>
            Invoices
            {isFree && <span className="lock-icon">🔒</span>}
          </button>
        </nav>

        <div className="header-right">
          <div className="tier-badge" data-tier={user!.subscriptionTier}>
            {user!.subscriptionTier === 'ACTIVE' && '⭐ Pro'}
            {user!.subscriptionTier === 'TRIAL'  && `⏳ Trial · ${user!.trialDaysRemaining}d`}
            {user!.subscriptionTier === 'FREE'   && '🆓 Free'}
          </div>
          {isFree && (
            <button className="btn-upgrade-header" onClick={() => navigate('/subscription')}>
              Upgrade
            </button>
          )}
          <div className="user-menu">
            <div className="user-avatar">{user!.email[0].toUpperCase()}</div>
            <div className="user-info">
              <span className="user-email">{user!.email}</span>
            </div>
            <button className="btn-logout" onClick={logout} title="Sign out">
              <svg width="16" height="16" viewBox="0 0 16 16" fill="currentColor">
                <path d="M6 2H2a1 1 0 00-1 1v10a1 1 0 001 1h4M11 11l3-3-3-3M14 8H6"/>
              </svg>
            </button>
          </div>
        </div>
      </header>

      {sidebarOpen && <div className="nav-overlay" onClick={() => setSidebarOpen(false)} />}

      <main className="app-main">
        {activeTab === 'dashboard' && <Dashboard companyId={companyId} />}
        {activeTab === 'invoices'  && <InvoiceUpload companyId={companyId} />}
        {activeTab === 'chat'      && <ChatAssistant />}
      </main>
    </div>
  )
}

function App() {
  const { isAuthenticated } = useAuth()

  return (
    <Routes>
      <Route path="/login"        element={isAuthenticated ? <Navigate to="/" replace /> : <LoginPage />} />
      <Route path="/register"     element={isAuthenticated ? <Navigate to="/" replace /> : <RegisterPage />} />
      <Route path="/subscription" element={isAuthenticated ? <SubscriptionPage /> : <Navigate to="/login" replace />} />
      <Route path="/"             element={isAuthenticated ? <ProtectedApp /> : <Navigate to="/login" replace />} />
      <Route path="*"             element={<Navigate to="/" replace />} />
    </Routes>
  )
}

export default App