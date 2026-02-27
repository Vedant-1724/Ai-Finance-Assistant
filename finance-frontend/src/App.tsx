import { useState } from 'react'
import Dashboard from './components/Dashboard'
import ChatAssistant from './components/ChatAssistant'
import InvoiceUpload from './components/InvoiceUpload'
import './App.css'

type Tab = 'dashboard' | 'chat' | 'invoices'

function App() {
  const [activeTab, setActiveTab] = useState<Tab>('dashboard')

  const companyId = 1

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
      </header>

      <main>
        {activeTab === 'dashboard' && (
          // Removed token prop — Dashboard no longer declares it
          <Dashboard companyId={companyId} />
        )}
        {activeTab === 'invoices' && (
          <InvoiceUpload companyId={companyId} />
        )}
        {activeTab === 'chat' && (
          <ChatAssistant />
        )}
      </main>
    </div>
  )
}

export default App
