import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import axios from 'axios'
import api from '../api'

interface RegisterResponse {
  message: string
  emailDeliveryEnabled: boolean
  verificationUrl?: string
}

export default function RegisterPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const next = searchParams.get('next') || '/'
  const loginHref = next === '/' ? '/login' : `/login?next=${encodeURIComponent(next)}`

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [companyName, setCompanyName] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState<RegisterResponse | null>(null)
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    if (!success || !success.emailDeliveryEnabled) return

    const timer = window.setTimeout(() => {
      navigate(loginHref, { replace: true })
    }, 2500)

    return () => window.clearTimeout(timer)
  }, [loginHref, navigate, success])

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setError(null)
    setSuccess(null)

    if (password !== confirm) {
      setError('Passwords do not match.')
      return
    }

    if (!/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).+$/.test(password)) {
      setError('Password must include an uppercase letter, a lowercase letter, and a number.')
      return
    }

    setLoading(true)
    try {
      const res = await api.post<RegisterResponse>('/api/v1/auth/register', {
        email,
        password,
        companyName,
      })
      setSuccess(res.data)
      setEmail('')
      setPassword('')
      setConfirm('')
      setCompanyName('')
    } catch (err) {
      if (axios.isAxiosError(err)) {
        const data = err.response?.data as { error?: string; message?: string } | undefined
        setError(data?.error ?? data?.message ?? 'Registration failed. Please try again.')
      } else {
        setError('Could not connect to the server.')
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
              <path d="M4 16l5-10 5 8 3-5 5 7" stroke="white" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
          </div>
          <div>
            <div className="auth-brand-name">FinanceAI</div>
            <div className="auth-brand-tag">AI-Powered Financial Intelligence</div>
          </div>
        </div>

        <h1 className="auth-heading">Create your account</h1>
        <p className="auth-subheading">Start free and unlock the 3-day premium trial when you are ready.</p>

        {error && <div className="error-box">⚠️ {error}</div>}
        {success && (
          <div className="success-box" style={{ display: 'grid', gap: 12 }}>
            <div>✅ {success.message}{success.emailDeliveryEnabled ? ' Redirecting to login…' : ''}</div>
            {!success.emailDeliveryEnabled && success.verificationUrl && (
              <div style={{ display: 'grid', gap: 10 }}>
                <a href={success.verificationUrl} className="btn-secondary" style={{ textAlign: 'center' }}>
                  Verify Email Now
                </a>
                <div style={{ fontSize: 12, lineHeight: 1.5, wordBreak: 'break-word', color: 'var(--text-secondary)' }}>
                  Verification link: <a href={success.verificationUrl}>{success.verificationUrl}</a>
                </div>
                <div style={{ fontSize: 12, color: 'var(--text-secondary)' }}>
                  After verification, use the sign-in link below.
                </div>
              </div>
            )}
          </div>
        )}

        <form className="auth-form" onSubmit={handleSubmit}>
          <div className="form-field">
            <label className="input-label">Company / Business Name</label>
            <input
              type="text"
              value={companyName}
              onChange={event => setCompanyName(event.target.value)}
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
              onChange={event => setEmail(event.target.value)}
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
              onChange={event => setPassword(event.target.value)}
              placeholder="At least 8 characters with Aa1"
              required
              className="input-field"
            />
          </div>

          <div className="form-field">
            <label className="input-label">Confirm Password</label>
            <input
              type="password"
              value={confirm}
              onChange={event => setConfirm(event.target.value)}
              placeholder="Repeat your password"
              required
              className="input-field"
            />
          </div>

          <div
            style={{
              background: 'rgba(59,130,246,0.06)',
              border: '1px solid rgba(59,130,246,0.15)',
              borderRadius: 'var(--radius-sm)',
              padding: '12px 14px',
              fontSize: '12px',
              color: 'var(--text-secondary)',
              lineHeight: '1.6',
            }}
          >
            ✅ <strong style={{ color: 'var(--text-primary)' }}>Free tier forever</strong> with core tracking and exports
            <br />
            ⭐ <strong style={{ color: 'var(--text-primary)' }}>3-day premium trial</strong> available anytime after signup
          </div>

          <button
            type="submit"
            disabled={loading}
            className="btn-liquid-glass"
            style={{ marginTop: '16px', width: '100%', justifyContent: 'center', display: 'flex', alignItems: 'center', gap: '8px' }}
          >
            {loading ? <><span className="spinner" style={{ width: 16, height: 16 }} /> Creating account…</> : <span>Create Free Account &rarr;</span>}
          </button>
        </form>

        <p className="auth-footer">
          Already have an account? <Link to={loginHref} className="auth-link">Sign in</Link>
        </p>
      </div>
    </div>
  )
}
