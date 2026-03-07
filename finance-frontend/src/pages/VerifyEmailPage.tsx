import { useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import api from '../api'

export default function VerifyEmailPage() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const [status, setStatus] = useState<'verifying' | 'success' | 'error'>('verifying')
  const [message, setMessage] = useState('Verifying your email address...')

  useEffect(() => {
    const token = searchParams.get('token')
    if (!token) {
      setStatus('error')
      setMessage('Invalid or missing verification token.')
      return
    }

    api.get(`/auth/verify-email?token=${token}`)
      .then(res => {
        setStatus('success')
        setMessage(res.data.message || 'Email verified successfully! You can now log in.')
      })
      .catch(err => {
        setStatus('error')
        setMessage(err.response?.data?.error || 'Verification failed. The token may be expired.')
      })
  }, [searchParams])

  return (
    <div className="login-container">
      <div className="auth-card">
        <h2 className="auth-title">Email Verification</h2>

        <div className={`verification-box ${status}`}>
          {status === 'verifying' && <span className="spinner">⏳</span>}
          {status === 'success' && <span className="icon-success">✅</span>}
          {status === 'error' && <span className="icon-error">❌</span>}

          <p className="auth-subtitle" style={{ marginTop: '1rem' }}>
            {message}
          </p>
        </div>

        {status !== 'verifying' && (
          <button
            type="button"
            className="btn-primary"
            onClick={() => navigate('/login')}
            style={{ width: '100%', marginTop: '2rem' }}
          >
            Go to Login
          </button>
        )}
      </div>
    </div>
  )
}
