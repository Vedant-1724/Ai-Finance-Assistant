import { useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import axios from 'axios'
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

    const verify = async () => {
      try {
        const res = await api.get<{ message?: string }>(`/api/v1/auth/verify-email?token=${encodeURIComponent(token)}`)
        setStatus('success')
        setMessage(res.data.message || 'Email verified successfully. You can now log in.')
      } catch (error) {
        setStatus('error')
        if (axios.isAxiosError(error)) {
          const data = error.response?.data as { error?: string; message?: string } | undefined
          setMessage(data?.error ?? data?.message ?? 'Verification failed. The token may be expired.')
        } else {
          setMessage('Verification failed. The token may be expired.')
        }
      }
    }

    void verify()
  }, [searchParams])

  return (
    <div className="auth-page">
      <div className="auth-card">
        <h2 className="auth-heading">Email Verification</h2>
        <p className="auth-subheading">We are confirming your email address now.</p>

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
