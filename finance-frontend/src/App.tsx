// PATH: finance-frontend/src/App.tsx
//
// CHANGES vs original:
//  1. TrialBanner component added above main content (shows days remaining / expired)
//  2. /subscription route added — SubscriptionPage with Razorpay checkout
//  3. subscriptionTab state controls whether the upgrade page is shown
//  4. 402 Payment Required responses in api.ts interceptor redirect to /subscription

import { useState } from 'react'
import { Routes, Route, Navigate, useNavigate } from 'react-router-dom'
import Dashboard from './components/Dashboard'
import ChatAssistant from './components/ChatAssistant'
import InvoiceUpload from './components/InvoiceUpload'
import TrialBanner from './components/TrialBanner'
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import SubscriptionPage from './pages/SubscriptionPage'
import { useAuth } from './context/AuthContext'
import './App.css'

type Tab = 'dashboard' | 'chat' | 'invoices'

// ── Protected app wrapper ─────────────────────────────────────────────────────
function ProtectedApp() {
  const { user, logout }    = useAuth()
  const navigate             = useNavigate()
  const [activeTab, setTab]  = useState<Tab>('dashboard')
  const companyId            = user!.companyId

  return (
    <div className="app">
      {/* Trial/subscription banner — shows for TRIAL and EXPIRED users */}
      <TrialBanner onUpgrade={() => navigate('/subscription')} />

      <header className="app-header">
        <div className="brand">
          <div className="brand-icon">💼</div>
          <div className="brand-text">
            <h1>Finance &amp; Accounting Assistant</h1>
            <p>AI-Powered Financial Intelligence</p>
          </div>
        </div>

        <nav>
          <button
            className={activeTab === 'dashboard' ? 'active' : ''}
            onClick={() => setTab('dashboard')}
          >📊 Dashboard</button>
          <button
            className={activeTab === 'invoices' ? 'active' : ''}
            onClick={() => setTab('invoices')}
          >🧾 Invoices</button>
          <button
            className={activeTab === 'chat' ? 'active' : ''}
            onClick={() => setTab('chat')}
          >💬 AI Assistant</button>
        </nav>

        <div className="user-menu">
          <span className="user-email">{user!.email}</span>
          <button
            className="btn-upgrade"
            onClick={() => navigate('/subscription')}
            style={{ marginRight: 8 }}
          >⭐ Upgrade</button>
          <button className="btn-logout" onClick={logout}>Sign Out</button>
        </div>
      </header>

      <main>
        {activeTab === 'dashboard' && <Dashboard companyId={companyId} />}
        {activeTab === 'invoices'  && <InvoiceUpload companyId={companyId} />}
        {activeTab === 'chat'      && <ChatAssistant />}
      </main>
    </div>
  )
}

// ── Root app — all routes ────────────────────────────────────────────────────
function App() {
  const { isAuthenticated } = useAuth()

  return (
    <Routes>
      <Route path="/login"    element={isAuthenticated ? <Navigate to="/" replace /> : <LoginPage />} />
      <Route path="/register" element={isAuthenticated ? <Navigate to="/" replace /> : <RegisterPage />} />
      <Route path="/subscription" element={isAuthenticated ? <SubscriptionPage /> : <Navigate to="/login" replace />} />
      <Route path="/" element={isAuthenticated ? <ProtectedApp /> : <Navigate to="/login" replace />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}

export default App
