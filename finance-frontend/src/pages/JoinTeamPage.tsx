import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import axios from 'axios'
import api from '../api'
import { useAuth } from '../context/AuthContext'

type JoinState = 'ready' | 'accepting' | 'success' | 'error'

export default function JoinTeamPage() {
  const { user, refreshSession } = useAuth()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token')
  const [status, setStatus] = useState<JoinState>(token ? 'ready' : 'error')
  const [message, setMessage] = useState(token ? 'Sign in to accept this team invitation.' : 'Missing team invitation token.')

  const nextHref = useMemo(() => {
    const raw = token ? `/join?token=${encodeURIComponent(token)}` : '/join'
    return encodeURIComponent(raw)
  }, [token])

  useEffect(() => {
    if (!token || !user || status !== 'ready') {
      return
    }

    const acceptInvite = async () => {
      setStatus('accepting')
      setMessage('Accepting your team invitation...')

      try {
        const res = await api.post<{ message?: string }>('/api/v1/team/accept', { token })
        await refreshSession()
        setStatus('success')
        setMessage(res.data.message || 'Team invitation accepted successfully.')
      } catch (error) {
        setStatus('error')
        if (axios.isAxiosError(error)) {
          const data = error.response?.data as { error?: string; message?: string } | undefined
          setMessage(data?.error ?? data?.message ?? 'Failed to accept the invitation.')
        } else {
          setMessage('Failed to accept the invitation.')
        }
      }
    }

    void acceptInvite()
  }, [status, token, user])

  if (!token) {
    return (
      <div className="auth-page">
        <div className="auth-card">
          <h1 className="auth-heading">Team Invite</h1>
          <p className="auth-subheading">This invite link is missing its token.</p>
          <button type="button" className="btn-liquid-glass" onClick={() => navigate('/login')} style={{ width: '100%', padding: '12px' }}>
            <span>Go to Login</span>
          </button>
        </div>
      </div>
    )
  }

  if (!user) {
    return (
      <div className="auth-page">
        <div className="auth-card">
          <h1 className="auth-heading">Join Team Workspace</h1>
          <p className="auth-subheading">Sign in or create an account to accept this invitation.</p>
          <div className="success-box" style={{ marginBottom: '1rem' }}>
            You will return to this invite automatically after signing in.
          </div>
          <div style={{ display: 'grid', gap: 12 }}>
            <Link to={`/login?next=${nextHref}`} className="btn-gradient" style={{ justifyContent: 'center', display: 'flex' }}>
              Sign In to Accept Invite
            </Link>
            <Link to={`/register?next=${nextHref}`} className="btn-secondary" style={{ justifyContent: 'center', display: 'flex' }}>
              Create Account First
            </Link>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="auth-page">
      <div className="auth-card">
        <h1 className="auth-heading">Join Team Workspace</h1>
        <p className="auth-subheading">{message}</p>

        {status === 'accepting' && <div className="success-box">⏳ Processing your invitation…</div>}
        {status === 'success' && <div className="success-box">✅ {message}</div>}
        {status === 'error' && <div className="error-box">⚠️ {message}</div>}

        <div style={{ display: 'grid', gap: 12, marginTop: '1rem' }}>
          {status === 'error' && (
            <button type="button" className="btn-liquid-glass" onClick={() => setStatus('ready')} style={{ padding: '10px' }}>
              <span>Try Again</span>
            </button>
          )}
          {status === 'success' && (
            <button type="button" className="btn-liquid-glass" onClick={() => navigate('/', { replace: true })} style={{ padding: '10px' }}>
              <span>Go to Dashboard</span>
            </button>
          )}
          <button type="button" className="btn-secondary" onClick={() => navigate('/', { replace: true })}>
            Back to App
          </button>
        </div>
      </div>
    </div>
  )
}
