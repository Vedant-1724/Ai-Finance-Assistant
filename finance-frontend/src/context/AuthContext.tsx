import { createContext, useContext, useState, useEffect, type ReactNode } from 'react'

// ── Types ─────────────────────────────────────────────────────────────────────

interface AuthUser {
  email:     string
  companyId: number
}

interface AuthContextType {
  token:           string | null
  user:            AuthUser | null
  isAuthenticated: boolean
  login:           (token: string, companyId: number, email: string) => void
  logout:          () => void
}

// ── Context ───────────────────────────────────────────────────────────────────

const AuthContext = createContext<AuthContextType | null>(null)

// ── Provider ──────────────────────────────────────────────────────────────────

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(
    () => localStorage.getItem('token')
  )
  const [user, setUser] = useState<AuthUser | null>(() => {
    const email     = localStorage.getItem('email')
    const companyId = localStorage.getItem('companyId')
    if (email && companyId) {
      return { email, companyId: Number(companyId) }
    }
    return null
  })

  // Keep localStorage in sync whenever token changes
  useEffect(() => {
    if (token) {
      localStorage.setItem('token', token)
    } else {
      localStorage.removeItem('token')
      localStorage.removeItem('email')
      localStorage.removeItem('companyId')
    }
  }, [token])

  const login = (newToken: string, companyId: number, email: string) => {
    localStorage.setItem('token',     newToken)
    localStorage.setItem('email',     email)
    localStorage.setItem('companyId', String(companyId))
    setToken(newToken)
    setUser({ email, companyId })
  }

  const logout = () => {
    setToken(null)
    setUser(null)
  }

  return (
    <AuthContext.Provider value={{
      token,
      user,
      isAuthenticated: !!token,
      login,
      logout,
    }}>
      {children}
    </AuthContext.Provider>
  )
}

// ── Hook ──────────────────────────────────────────────────────────────────────

export function useAuth(): AuthContextType {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>')
  return ctx
}
