import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import axios from 'axios'
import api from '../api'

interface ForgotPasswordResponse {
  message: string
  emailDeliveryEnabled: boolean
  resetUrl?: string
}

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const [status, setStatus] = useState<'idle' | 'loading' | 'success' | 'error'>('idle')
  const [result, setResult] = useState<ForgotPasswordResponse | null>(null)
  const [message, setMessage] = useState('')

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setStatus('loading')
    setMessage('')
    setResult(null)

    try {
      const res = await api.post<ForgotPasswordResponse>('/api/v1/auth/forgot-password', { email })
      setStatus('success')
      setResult(res.data)
      setMessage(res.data.message || 'If that email exists, a password reset link has been sent.')
    } catch (err: unknown) {
      setStatus('error')
      if (axios.isAxiosError(err)) {
        const data = err.response?.data as { error?: string } | undefined
        setMessage(data?.error || 'Failed to send reset email. Please try again.')
      } else {
        setMessage('Failed to send reset email. Please try again.')
      }
    }
  }

  return (
    <div className="auth-page">
      <div className="auth-card">
        <h2 className="auth-heading">Reset Password</h2>
        <p className="auth-subheading">Enter your email and we'll send you a link to reset your password.</p>

        {status === 'success' ? (
          <div className="verification-box success">
            <span className="icon-success">✅</span>
            <div style={{ marginTop: '1rem', display: 'grid', gap: 12 }}>
              <p>{message}</p>
              {!result?.emailDeliveryEnabled && result?.resetUrl && (
                <div style={{ display: 'grid', gap: 10, textAlign: 'left' }}>
                  <a href={result.resetUrl} className="btn-liquid-glass" style={{ textAlign: 'center' }}>
                    Open Reset Link
                  </a>
                  <div style={{ fontSize: 12, lineHeight: 1.5, wordBreak: 'break-word' }}>
                    Reset link: <a href={result.resetUrl}>{result.resetUrl}</a>
                  </div>
                </div>
              )}
            </div>
            <Link to="/login" className="btn-liquid-glass" style={{ display: 'block', textAlign: 'center', marginTop: '1.5rem', width: '100%' }}>
              Return to Login
            </Link>
          </div>
        ) : (
          <>
            {status === 'error' && <div className="error-box">⚠️ {message}</div>}

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

              <button
                type="submit"
                disabled={status === 'loading'}
                className="btn-liquid-glass"
                style={{ width: '100%', justifyContent: 'center', display: 'flex', alignItems: 'center', gap: '8px' }}
              >
                {status === 'loading' ? <><span className="spinner" style={{ width: 16, height: 16 }} /> Sending Link…</> : 'Send Reset Link'}
              </button>
            </form>

            <p className="auth-footer" style={{ marginTop: '1.5rem' }}>
              Remembered your password?{' '}
              <Link to="/login" className="auth-link">Back to Login</Link>
            </p>
          </>
        )}
      </div>
    </div>
  )
}
