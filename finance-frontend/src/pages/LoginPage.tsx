import { useState, type FormEvent } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import axios from 'axios'
import { useAuth } from '../context/AuthContext'

interface AuthResponse {
  token:     string
  companyId: number
  email:     string
}

export default function LoginPage() {
  const { login }       = useAuth()
  const navigate        = useNavigate()
  const [email, setEmail]       = useState('')
  const [password, setPassword] = useState('')
  const [error, setError]       = useState<string | null>(null)
  const [loading, setLoading]   = useState(false)

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    setLoading(true)

    try {
      const res = await axios.post<AuthResponse>(
        'http://localhost:8080/api/v1/auth/login',
        { email, password }
      )
      login(res.data.token, res.data.companyId, res.data.email)
      navigate('/', { replace: true })
    } catch (err) {
      if (axios.isAxiosError(err)) {
        const msg = (err.response?.data as { error?: string })?.error
        setError(msg ?? 'Invalid email or password')
      } else {
        setError('Could not connect to server. Make sure Spring Boot is running.')
      }
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={styles.page}>
      <div style={styles.card}>

        {/* Brand */}
        <div style={styles.brand}>
          <div style={styles.brandIcon}>💼</div>
          <div>
            <h1 style={styles.brandTitle}>Finance Assistant</h1>
            <p style={styles.brandSub}>AI-Powered Financial Intelligence</p>
          </div>
        </div>

        <h2 style={styles.heading}>Sign in to your account</h2>

        {error && (
          <div style={styles.errorBox}>
            ⚠️ {error}
          </div>
        )}

        <form onSubmit={handleSubmit} style={styles.form}>
          <div style={styles.field}>
            <label style={styles.label}>Email</label>
            <input
              type="email"
              value={email}
              onChange={e => setEmail(e.target.value)}
              placeholder="admin@finance.com"
              required
              autoFocus
              style={styles.input}
            />
          </div>

          <div style={styles.field}>
            <label style={styles.label}>Password</label>
            <input
              type="password"
              value={password}
              onChange={e => setPassword(e.target.value)}
              placeholder="••••••••"
              required
              style={styles.input}
            />
          </div>

          <button
            type="submit"
            disabled={loading}
            style={{ ...styles.btn, opacity: loading ? 0.7 : 1 }}
          >
            {loading ? '⏳ Signing in…' : 'Sign In →'}
          </button>
        </form>

        <p style={styles.footer}>
          Don't have an account?{' '}
          <Link to="/register" style={styles.link}>Create one</Link>
        </p>

        {/* Dev hint */}
        <div style={styles.hint}>
          <strong>Dev account:</strong> admin@finance.com / password123
        </div>

      </div>
    </div>
  )
}

// ── Styles — match the existing App.css dark theme variables ──────────────────

const styles: Record<string, React.CSSProperties> = {
  page: {
    minHeight:       '100vh',
    display:         'flex',
    alignItems:      'center',
    justifyContent:  'center',
    background:      '#0a0f1e',
    padding:         '24px',
  },
  card: {
    width:           '100%',
    maxWidth:        '420px',
    background:      '#0d1526',
    border:          '1px solid #1a2744',
    borderRadius:    '16px',
    padding:         '40px 36px',
    boxShadow:       '0 24px 64px rgba(0,0,0,0.5)',
  },
  brand: {
    display:         'flex',
    alignItems:      'center',
    gap:             '14px',
    marginBottom:    '32px',
  },
  brandIcon: {
    fontSize:        '36px',
    background:      'rgba(59,130,246,0.15)',
    borderRadius:    '12px',
    padding:         '10px',
    border:          '1px solid rgba(59,130,246,0.3)',
  },
  brandTitle: {
    fontSize:        '18px',
    fontWeight:      700,
    color:           '#e2e8f0',
    margin:          0,
  },
  brandSub: {
    fontSize:        '12px',
    color:           '#4a5a7a',
    margin:          0,
  },
  heading: {
    fontSize:        '20px',
    fontWeight:      700,
    color:           '#e2e8f0',
    marginBottom:    '24px',
    marginTop:       0,
  },
  errorBox: {
    background:      'rgba(239,68,68,0.1)',
    border:          '1px solid rgba(239,68,68,0.3)',
    color:           '#f87171',
    borderRadius:    '8px',
    padding:         '12px 16px',
    fontSize:        '13px',
    marginBottom:    '20px',
  },
  form: {
    display:         'flex',
    flexDirection:   'column',
    gap:             '18px',
  },
  field: {
    display:         'flex',
    flexDirection:   'column',
    gap:             '6px',
  },
  label: {
    fontSize:        '12px',
    fontWeight:      600,
    color:           '#8b9ec7',
    textTransform:   'uppercase',
    letterSpacing:   '0.6px',
  },
  input: {
    background:      '#0a1428',
    border:          '1px solid #1a2744',
    borderRadius:    '8px',
    padding:         '11px 14px',
    color:           '#e2e8f0',
    fontSize:        '14px',
    outline:         'none',
    fontFamily:      'Inter, sans-serif',
    transition:      'border-color 0.2s',
  },
  btn: {
    marginTop:       '8px',
    padding:         '13px',
    background:      '#3b82f6',
    color:           '#fff',
    border:          'none',
    borderRadius:    '8px',
    fontSize:        '14px',
    fontWeight:      600,
    cursor:          'pointer',
    fontFamily:      'Inter, sans-serif',
    transition:      'background 0.2s',
    boxShadow:       '0 2px 12px rgba(59,130,246,0.35)',
  },
  footer: {
    marginTop:       '24px',
    textAlign:       'center',
    fontSize:        '13px',
    color:           '#4a5a7a',
  },
  link: {
    color:           '#60a5fa',
    textDecoration:  'none',
    fontWeight:      600,
  },
  hint: {
    marginTop:       '16px',
    padding:         '10px 14px',
    background:      'rgba(59,130,246,0.07)',
    border:          '1px solid rgba(59,130,246,0.2)',
    borderRadius:    '8px',
    fontSize:        '12px',
    color:           '#4a5a7a',
    textAlign:       'center',
  },
}
