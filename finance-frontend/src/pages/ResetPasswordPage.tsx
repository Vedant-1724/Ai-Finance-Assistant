import { useState, type FormEvent } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import axios from 'axios'

export default function ResetPasswordPage() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const token = searchParams.get('token')

  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [status, setStatus] = useState<'idle' | 'loading' | 'success' | 'error'>('idle')
  const [message, setMessage] = useState('')

  if (!token) {
    return (
      <div className="auth-page">
        <div className="auth-card">
          <div className="verification-box error">
            <span className="icon-error">❌</span>
            <p style={{ marginTop: '1rem' }}>Invalid or missing password reset token.</p>
          </div>
        </div>
      </div>
    )
  }

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()

    if (password !== confirmPassword) {
      setStatus('error')
      setMessage('Passwords do not match.')
      return
    }

    if (password.length < 6) {
      setStatus('error')
      setMessage('Password must be at least 6 characters.')
      return
    }

    setStatus('loading')
    setMessage('')

    try {
      const res = await axios.post('/api/v1/auth/reset-password', {
        token,
        newPassword: password
      })
      setStatus('success')
      setMessage(res.data.message || 'Your password has been reset successfully.')
    } catch (err: any) {
      setStatus('error')
      setMessage(err.response?.data?.error || 'Failed to reset password. The token may be expired.')
    }
  }

  return (
    <div className="auth-page">
      <div className="auth-card">
        <h2 className="auth-heading">Choose a New Password</h2>
        <p className="auth-subheading">Please enter your new password below.</p>

        {status === 'success' ? (
          <div className="verification-box success">
            <span className="icon-success">✅</span>
            <p style={{ marginTop: '1rem' }}>{message}</p>
            <button
              className="btn-primary"
              onClick={() => navigate('/login')}
              style={{ width: '100%', marginTop: '1.5rem' }}
            >
              Go to Login
            </button>
          </div>
        ) : (
          <>
            {status === 'error' && <div className="error-box">⚠️ {message}</div>}

            <form className="auth-form" onSubmit={handleSubmit}>
              <div className="form-field">
                <label className="input-label">New Password</label>
                <input
                  type="password"
                  value={password}
                  onChange={e => setPassword(e.target.value)}
                  placeholder="Min. 6 characters"
                  required
                  autoFocus
                  className="input-field"
                />
              </div>

              <div className="form-field">
                <label className="input-label">Confirm New Password</label>
                <input
                  type="password"
                  value={confirmPassword}
                  onChange={e => setConfirmPassword(e.target.value)}
                  placeholder="Re-enter password"
                  required
                  className="input-field"
                />
              </div>

              <button
                type="submit"
                disabled={status === 'loading'}
                className="btn-gradient"
                style={{ width: '100%', marginTop: '4px', justifyContent: 'center', display: 'flex', alignItems: 'center', gap: '8px' }}
              >
                {status === 'loading' ? <><span className="spinner" style={{ width: 16, height: 16 }} /> Saving…</> : 'Reset Password'}
              </button>
            </form>
          </>
        )}
      </div>
    </div>
  )
}
