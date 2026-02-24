import { useState, useRef, useEffect } from 'react'
import axios from 'axios'

interface Message {
  role: 'user' | 'assistant'
  content: string
}

function ChatAssistant() {
  const [messages, setMessages] = useState<Message[]>([
    { role: 'assistant', content: 'Hi! I am your AI Finance Assistant. Ask me anything about your finances!' }
  ])
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const bottomRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  const sendMessage = async () => {
    if (!input.trim() || loading) return

    const userMsg: Message = { role: 'user', content: input }
    setMessages(prev => [...prev, userMsg])
    setInput('')
    setLoading(true)

    try {
      const res = await axios.post<{ answer: string }>(
        'http://localhost:5000/chat',
        { question: input }
      )
      setMessages(prev => [
        ...prev,
        { role: 'assistant', content: res.data.answer }
      ])
    } catch {
      setMessages(prev => [
        ...prev,
        { role: 'assistant', content: '⚠️ Could not reach the AI service. Make sure the Python server is running on port 5000.' }
      ])
    } finally {
      setLoading(false)
    }
  }

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      sendMessage()
    }
  }

  return (
    <div className="chat-container">
      <h2>💬 AI Finance Assistant</h2>
      <div className="chat-messages">
        {messages.map((msg, i) => (
          <div key={i} className={`chat-bubble ${msg.role}`}>
            <strong>{msg.role === 'user' ? '👤 You' : '🤖 Assistant'}</strong>
            <p>{msg.content}</p>
          </div>
        ))}
        {loading && (
          <div className="chat-bubble assistant">
            <p>⏳ Thinking...</p>
          </div>
        )}
        <div ref={bottomRef} />
      </div>
      <div className="chat-input-row">
        <textarea
          value={input}
          onChange={e => setInput(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="Ask anything — e.g. 'What was my net income last month?'"
          rows={2}
        />
        <button onClick={sendMessage} disabled={loading}>
          Send ➤
        </button>
      </div>
    </div>
  )
}

export default ChatAssistant
