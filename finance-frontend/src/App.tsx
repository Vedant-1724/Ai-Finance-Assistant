import { useState } from 'react'
import Dashboard from './components/Dashboard'
import ChatAssistant from './components/ChatAssistant'
import './App.css'

function App() {
  const [activeTab, setActiveTab] = useState<'dashboard' | 'chat'>('dashboard')

  // Temporary hardcoded values for development
  const companyId = 1
  const token = 'test-token'

  return (
    <div className="app">
      <header className="app-header">
        <h1>💼 Finance & Accounting Assistant</h1>
        <nav>
          <button
            className={activeTab === 'dashboard' ? 'active' : ''}
            onClick={() => setActiveTab('dashboard')}
          >
            📊 Dashboard
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
        {activeTab === 'dashboard'
          ? <Dashboard companyId={companyId} token={token} />
          : <ChatAssistant />
        }
      </main>
    </div>
  )
}

export default App
