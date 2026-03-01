// PATH: finance-frontend/src/components/ChatAssistant.tsx
//
// CHANGES:
//  - Calls /api/v1/ai/chat (Spring Boot proxy) instead of Python directly
//    This enables per-user daily limit enforcement on the backend
//  - Shows remaining daily chat count from auth context
//  - Shows upgrade prompt when daily limit is hit

import { useState, useRef, useEffect } from 'react'
import axios from 'axios'
import { useAuth } from '../context/AuthContext'

interface Message { role: 'user' | 'assistant'; content: string }

export default function ChatAssistant() {
  const { user, updateAiChats } = useAuth()
  const [messages, setMessages] = useState<Message[]>([
    { role: 'assistant', content: '👋 Hi! I\'m your AI finance assistant. Ask me anything about your finances, cash flow, or accounting.' }
  ])
  const [input, setInput]     = useState('')
  const [loading, setLoading] = useState(false)
  const bottomRef             = useRef<HTMLDivElement>(null)

  const chatsRemaining = user?.aiChatsRemaining ?? 0
  const dailyLimit     = user?.subscriptionTier === 'ACTIVE' ? 50 : user?.subscriptionTier === 'TRIAL' ? 10 : 3

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  const sendMessage = async () => {
    const q = input.trim()
    if (!q || loading || chatsRemaining <= 0) return

    setMessages(prev => [...prev, { role: 'user', content: q }])
    setInput('')
    setLoading(true)

    try {
      const res = await axios.post(
        '/api/v1/ai/chat',
        { question: q },
        { headers: { Authorization: `Bearer ${user?.token}` } }
      )
      const data = res.data as { answer?: string; aiChatsRemaining?: number }

      setMessages(prev => [...prev, {
        role: 'assistant',
        content: data.answer || 'I couldn\'t process that. Please try again.'
      }])

      // Update remaining chats in context
      if (typeof data.aiChatsRemaining === 'number') {
        updateAiChats(data.aiChatsRemaining)
      }
    } catch (err: any) {
      const errCode = err?.response?.data?.error
      if (errCode === 'DAILY_LIMIT_EXCEEDED') {
        setMessages(prev => [...prev, {
          role: 'assistant',
          content: `⚠️ You've used all ${dailyLimit} AI chats for today. Your limit resets at midnight.\n\n${user?.subscriptionTier === 'FREE' ? 'Upgrade to Trial or Pro for more daily messages!' : 'Upgrade to Pro for 50 chats/day!'}`
        }])
        updateAiChats(0)
      } else {
        setMessages(prev => [...prev, {
          role: 'assistant',
          content: '⚠️ The AI service is temporarily unavailable. Please try again in a moment.'
        }])
      }
    } finally {
      setLoading(false)
    }
  }

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); sendMessage() }
  }

  const limitClass = chatsRemaining === 0 ? 'exhausted' : chatsRemaining <= 1 ? 'warning' : ''

  return (
    <div>
      <div className="page-header">
        <h1 className="page-title">AI Finance Assistant</h1>
        <p className="page-subtitle">Ask questions about your finances in plain English</p>
      </div>

      <div className="chat-container">
        <div className="chat-header">
          <div className="chat-title">
            <div style={{ width: 8, height: 8, background: 'var(--green)', borderRadius: '50%' }} />
            AI Assistant
          </div>
          <div className={`chat-limit-badge ${limitClass}`}>
            {chatsRemaining > 0
              ? `${chatsRemaining} / ${dailyLimit} chats today`
              : `Daily limit reached · Resets at midnight`}
          </div>
        </div>

        <div className="chat-messages">
          {messages.map((msg, i) => (
            <div key={i} className={`chat-msg ${msg.role}`}>
              <div className={`msg-avatar ${msg.role === 'assistant' ? 'ai' : 'user'}`}>
                {msg.role === 'assistant' ? '🤖' : user?.email[0].toUpperCase()}
              </div>
              <div className="msg-bubble" style={{ whiteSpace: 'pre-wrap' }}>
                {msg.content}
              </div>
            </div>
          ))}
          {loading && (
            <div className="chat-msg assistant">
              <div className="msg-avatar ai">🤖</div>
              <div className="msg-bubble" style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
                <span className="spinner" style={{ width: 14, height: 14 }} />
                <span style={{ color: 'var(--text-muted)', fontSize: 13 }}>Thinking…</span>
              </div>
            </div>
          )}
          <div ref={bottomRef} />
        </div>

        <div className="chat-input-area">
          <textarea
            className="chat-textarea"
            value={input}
            onChange={e => setInput(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder={chatsRemaining > 0 ? "Ask about your finances…  (Enter to send)" : "Daily limit reached. Come back tomorrow!"}
            disabled={loading || chatsRemaining <= 0}
            rows={1}
          />
          <button
            className="chat-send-btn"
            onClick={sendMessage}
            disabled={loading || !input.trim() || chatsRemaining <= 0}
          >
            Send ➤
          </button>
        </div>
      </div>
    </div>
  )
}