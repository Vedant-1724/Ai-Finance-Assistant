import { useState } from 'react'
import { Routes, Route, Navigate } from 'react-router-dom'
import Dashboard from './components/Dashboard'
import ChatAssistant from './components/ChatAssistant'
import InvoiceUpload from './components/InvoiceUpload'
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import { useAuth } from './context/AuthContext'
import './App.css'

type Tab = 'dashboard' | 'chat' | 'invoices'

// ── Protected wrapper — redirects to /login if not authenticated ──────────────
function ProtectedApp() {
  const { user, logout } = useAuth()
  const [activeTab, setActiveTab] = useState<Tab>('dashboard')

  // companyId comes from the JWT (set during login) — no more hardcoded 1
  const companyId = user!.companyId

  return (
    <div className="app">
      <header className="app-header">
        <div className="brand">
          <div className="brand-icon">💼</div>
          <div className="brand-text">
            <h1>Finance & Accounting Assistant</h1>
            <p>AI-Powered Financial Intelligence</p>
          </div>
        </div>

        <nav>
          <button
            className={activeTab === 'dashboard' ? 'active' : ''}
            onClick={() => setActiveTab('dashboard')}
          >
            📊 Dashboard
          </button>
          <button
            className={activeTab === 'invoices' ? 'active' : ''}
            onClick={() => setActiveTab('invoices')}
          >
            🧾 Invoices
          </button>
          <button
            className={activeTab === 'chat' ? 'active' : ''}
            onClick={() => setActiveTab('chat')}
          >
            💬 AI Assistant
          </button>
        </nav>

        {/* User info + logout */}
        <div className="user-menu">
          <span className="user-email">{user!.email}</span>
          <button className="btn-logout" onClick={logout}>
            Sign Out
          </button>
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

// ── Root App — defines all routes ─────────────────────────────────────────────
function App() {
  const { isAuthenticated } = useAuth()

  return (
    <Routes>
      {/* Public routes */}
      <Route path="/login"    element={
        isAuthenticated ? <Navigate to="/" replace /> : <LoginPage />
      } />
      <Route path="/register" element={
        isAuthenticated ? <Navigate to="/" replace /> : <RegisterPage />
      } />

      {/* Protected route — redirects to /login if not authenticated */}
      <Route path="/" element={
        isAuthenticated ? <ProtectedApp /> : <Navigate to="/login" replace />
      } />

      {/* Catch-all — redirect to home */}
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}

export default App
