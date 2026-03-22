import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import axios from 'axios'
import api from '../api'
import { useAuth } from '../context/AuthContext'

interface AuthResponse {
  companyId: number
  email: string
  role: string
  subscriptionStatus: string
  trialDaysRemaining: number
  aiChatsRemaining: number
  aiChatDailyLimit: number
  canManageBilling?: boolean
}

export default function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const next = searchParams.get('next') || '/'

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const [showPwd, setShowPwd] = useState(false)

  const registerHref = next === '/' ? '/register' : `/register?next=${encodeURIComponent(next)}`

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setError(null)
    setLoading(true)

    try {
      const res = await api.post<AuthResponse>(
        '/api/v1/auth/login',
        { email, password },
        { headers: { 'X-Skip-401-Redirect': 'true' } }
      )

      login(res.data)
      navigate(next, { replace: true })
    } catch (err) {
      if (axios.isAxiosError(err)) {
        const data = err.response?.data as { error?: string; message?: string } | undefined
        const msg = data?.error ?? data?.message
        if (msg === 'EMAIL_UNVERIFIED') {
          setError('Please verify your email address before logging in. Check your inbox for the verification link.')
        } else {
          setError(msg ?? 'Login failed. Check your email and password.')
        }
      } else {
        setError('Cannot connect to server.')
      }
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="auth-page">
      <div className="auth-card glass-container" style={{ maxWidth: 460, margin: '0 auto', boxShadow: 'var(--glass-shadow-lg)' }}>
        <div className="auth-brand">
          <div className="auth-brand-icon">
            <svg width="26" height="26" viewBox="0 0 26 26" fill="none">
              <path d="M4 16l5-10 5 8 3-5 5 7" stroke="white" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
          </div>
          <div>
            <div className="auth-brand-name">FinanceAI</div>
            <div className="auth-brand-tag">AI-Powered Financial Intelligence</div>
          </div>
        </div>

        <h1 className="auth-heading">Welcome back</h1>
        <p className="auth-subheading">Sign in to your account to continue</p>

        {error && <div className="error-box">⚠️ {error}</div>}

        <form className="auth-form" onSubmit={handleSubmit}>
          <div className="form-field">
            <label className="glass-label">Email address</label>
            <input
              type="email"
              value={email}
              onChange={event => setEmail(event.target.value)}
              placeholder="you@example.com"
              required
              autoFocus
              className="glass-input"
            />
          </div>

          <div className="form-field">
            <label className="glass-label" style={{ display: 'flex', justifyContent: 'space-between' }}>
              Password
              <span
                style={{
                  fontSize: '11px',
                  color: 'var(--text-accent)',
                  cursor: 'pointer',
                  fontWeight: 400,
                  textTransform: 'none',
                  letterSpacing: 0,
                }}
                onClick={() => setShowPwd(current => !current)}
              >
                {showPwd ? 'Hide' : 'Show'}
              </span>
            </label>
            <input
              type={showPwd ? 'text' : 'password'}
              value={password}
              onChange={event => setPassword(event.target.value)}
              placeholder="Your password"
              required
              className="glass-input"
            />
          </div>

          <button
            type="submit"
            disabled={loading}
            className="glass-btn glass-btn-primary"
            style={{ marginTop: '16px', width: '100%', justifyContent: 'center', display: 'flex', alignItems: 'center', gap: '8px' }}
          >
            {loading ? <><span className="spinner" style={{ width: 16, height: 16 }} /> Signing in…</> : <span>Sign In &rarr;</span>}
          </button>
        </form>

        <div style={{ display: 'flex', alignItems: 'center', margin: '1.5rem 0', color: 'var(--text-muted)', fontSize: '12px' }}>
          <div style={{ flex: 1, height: '1px', background: 'var(--border)' }} />
          <span style={{ padding: '0 10px', textTransform: 'uppercase', letterSpacing: '0.05em', fontWeight: 600 }}>New to FinanceAI?</span>
          <div style={{ flex: 1, height: '1px', background: 'var(--border)' }} />
        </div>

        <button
          type="button"
          onClick={() => navigate(registerHref)}
          className="glass-btn"
          style={{
            width: '100%',
            justifyContent: 'center',
            display: 'flex',
            alignItems: 'center',
            padding: '12px',
            borderRadius: 'var(--radius-sm)',
            border: '1px solid var(--glass-border)',
            fontWeight: 600,
            cursor: 'pointer',
            transition: 'all 0.2s ease',
          }}
        >
          Create New Account
        </button>

        <p className="auth-footer" style={{ marginTop: '1.5rem', textAlign: 'center' }}>
          Forgot your password? <Link to="/forgot-password" className="auth-link">Reset it</Link>
        </p>
      </div>
    </div>
  )
}
