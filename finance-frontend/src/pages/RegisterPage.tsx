// PATH: finance-frontend/src/pages/RegisterPage.tsx

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

export default function RegisterPage() {
  const { login }   = useAuth()
  const navigate    = useNavigate()
  const [email, setEmail]           = useState('')
  const [password, setPassword]     = useState('')
  const [confirm, setConfirm]       = useState('')
  const [companyName, setCompany]   = useState('')
  const [error, setError]           = useState<string | null>(null)
  const [loading, setLoading]       = useState(false)

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    if (password !== confirm) { setError('Passwords do not match'); return }
    if (password.length < 8)  { setError('Password must be at least 8 characters'); return }
    setLoading(true)

    try {
      const res = await axios.post<AuthResponse>(
        '/api/v1/auth/register',
        { email, password, companyName }
      )
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
        setError(msg ?? 'Registration failed. Please try again.')
      } else {
        setError('Could not connect to server.')
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

        <h1 className="auth-heading">Create your account</h1>
        <p className="auth-subheading">Start free — no credit card required</p>

        {error && <div className="error-box">⚠️ {error}</div>}

        <form className="auth-form" onSubmit={handleSubmit}>
          <div className="form-field">
            <label className="input-label">Company / Business Name</label>
            <input
              type="text"
              value={companyName}
              onChange={e => setCompany(e.target.value)}
              placeholder="Acme Pvt Ltd"
              required
              autoFocus
              className="input-field"
            />
          </div>

          <div className="form-field">
            <label className="input-label">Email address</label>
            <input
              type="email"
              value={email}
              onChange={e => setEmail(e.target.value)}
              placeholder="you@example.com"
              required
              className="input-field"
            />
          </div>

          <div className="form-field">
            <label className="input-label">Password</label>
            <input
              type="password"
              value={password}
              onChange={e => setPassword(e.target.value)}
              placeholder="Min. 8 characters"
              required
              className="input-field"
            />
          </div>

          <div className="form-field">
            <label className="input-label">Confirm Password</label>
            <input
              type="password"
              value={confirm}
              onChange={e => setConfirm(e.target.value)}
              placeholder="Repeat your password"
              required
              className="input-field"
            />
          </div>

          <div style={{ background: 'rgba(59,130,246,0.06)', border: '1px solid rgba(59,130,246,0.15)', borderRadius: 'var(--radius-sm)', padding: '12px 14px', fontSize: '12px', color: 'var(--text-secondary)', lineHeight: '1.6' }}>
            ✅ <strong style={{ color: 'var(--text-primary)' }}>Free tier forever</strong> — basic features with no time limit<br/>
            ⭐ <strong style={{ color: 'var(--text-primary)' }}>5-day Premium Trial</strong> — unlock anytime from your dashboard
          </div>

          <button
            type="submit"
            disabled={loading}
            className="btn-gradient"
            style={{ marginTop: '4px', width: '100%', justifyContent: 'center', display: 'flex', alignItems: 'center', gap: '8px' }}
          >
            {loading ? <><span className="spinner" style={{width:16,height:16}} /> Creating account…</> : 'Create Free Account →'}
          </button>
        </form>

        <p className="auth-footer">
          Already have an account?{' '}
          <Link to="/login" className="auth-link">Sign in</Link>
        </p>
      </div>
    </div>
  )
}