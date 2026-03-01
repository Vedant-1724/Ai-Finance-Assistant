import { useState, type FormEvent } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import axios from 'axios'
import { useAuth } from '../context/AuthContext'

interface AuthResponse {
  token:              string
  companyId:          number
  email:              string
  subscriptionStatus: string
  trialDaysRemaining: number
  aiChatsRemaining:   number
}

export default function LoginPage() {
  const { login }   = useAuth()
  const navigate    = useNavigate()
  const [email, setEmail]       = useState('')
  const [password, setPassword] = useState('')
  const [error, setError]       = useState<string | null>(null)
  const [loading, setLoading]   = useState(false)
  const [showPwd, setShowPwd]   = useState(false)

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    setLoading(true)

    try {
      const res = await axios.post<AuthResponse>('/api/v1/auth/login', { email, password })
      login(
        res.data.token,
        res.data.companyId,
        res.data.email,
        res.data.subscriptionStatus,
        res.data.trialDaysRemaining,
        res.data.aiChatsRemaining
      )
      navigate('/', { replace: true })
    } catch (err) {
      if (axios.isAxiosError(err)) {
        const msg = (err.response?.data as { error?: string })?.error
        setError(msg ?? 'Login failed. Check your email and password.')
      } else {
        setError('Cannot connect to server.')
      }
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="auth-page">
      <div className="auth-card">
        <div className="auth-brand">
          <div className="auth-brand-icon">
            <svg width="26" height="26" viewBox="0 0 26 26" fill="none">
              <path d="M4 16l5-10 5 8 3-5 5 7" stroke="white" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"/>
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
            <label className="input-label">Email address</label>
            <input
              type="email"
              value={email}
              onChange={e => setEmail(e.target.value)}
              placeholder="you@example.com"
              required
              autoFocus
              className="input-field"
            />
          </div>

          <div className="form-field">
            <label className="input-label" style={{ display: 'flex', justifyContent: 'space-between' }}>
              Password
              <span style={{ fontSize: '11px', color: 'var(--text-accent)', cursor: 'pointer', fontWeight: 400, textTransform: 'none', letterSpacing: 0 }}
                onClick={() => setShowPwd(!showPwd)}>
                {showPwd ? 'Hide' : 'Show'}
              </span>
            </label>
            <input
              type={showPwd ? 'text' : 'password'}
              value={password}
              onChange={e => setPassword(e.target.value)}
              placeholder="Your password"
              required
              className="input-field"
            />
          </div>

          <button
            type="submit"
            disabled={loading}
            className="btn-gradient"
            style={{ marginTop: '4px', width: '100%', justifyContent: 'center', display: 'flex', alignItems: 'center', gap: '8px' }}
          >
            {loading ? <><span className="spinner" style={{width:16,height:16}} /> Signing in…</> : 'Sign In →'}
          </button>
        </form>

        <p className="auth-footer">
          Don't have an account?{' '}
          <Link to="/register" className="auth-link">Create one free</Link>
        </p>
      </div>
    </div>
  )
}