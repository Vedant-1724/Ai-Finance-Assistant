import axios from 'axios'
import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import api from '../api'

type SubscriptionTier = 'FREE' | 'TRIAL' | 'ACTIVE' | 'MAX'

interface UserInfo {
  companyId: number
  email: string
  subscriptionTier: SubscriptionTier
  trialDaysRemaining: number
  aiChatsRemaining: number
}

interface AuthContextType {
  user: UserInfo | null
  isAuthenticated: boolean
  authReady: boolean
  isPremium: boolean
  isFree: boolean
  isTrial: boolean
  isMax: boolean
  login: (
    companyId: number,
    email: string,
    subscriptionStatus: string,
    trialDaysRemaining: number,
    aiChatsRemaining: number
  ) => void
  logout: () => Promise<void>
  updateSubscription: (tier: string, daysRemaining: number, aiChatsRemaining: number) => void
  updateAiChats: (remaining: number) => void
  updateProfile: (email: string) => void
}

interface AuthMeResponse {
  companyId: number
  email: string
  subscriptionStatus: string
  trialDaysRemaining: number
  aiChatsRemaining: number
}

const AuthContext = createContext<AuthContextType | null>(null)

function normalizeTier(subscriptionStatus: string): SubscriptionTier {
  if (subscriptionStatus === 'ACTIVE' || subscriptionStatus === 'TRIAL' || subscriptionStatus === 'MAX') {
    return subscriptionStatus
  }
  return 'FREE'
}

function buildUserInfo(data: AuthMeResponse): UserInfo {
  return {
    companyId: data.companyId,
    email: data.email,
    subscriptionTier: normalizeTier(data.subscriptionStatus),
    trialDaysRemaining: data.trialDaysRemaining,
    aiChatsRemaining: data.aiChatsRemaining,
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserInfo | null>(() => {
    try {
      const stored = localStorage.getItem('auth_user')
      return stored ? JSON.parse(stored) as UserInfo : null
    } catch {
      return null
    }
  })
  const [authReady, setAuthReady] = useState(false)

  useEffect(() => {
    let cancelled = false

    const syncSession = async () => {
      try {
        const res = await api.get<AuthMeResponse>('/api/v1/auth/me', {
          headers: { 'X-Skip-401-Redirect': 'true' },
        })

        if (cancelled) return

        const nextUser = buildUserInfo(res.data)
        setUser(nextUser)
        localStorage.setItem('auth_user', JSON.stringify(nextUser))
      } catch (error) {
        if (cancelled) return

        if (axios.isAxiosError(error) && error.response?.status === 401) {
          setUser(null)
          localStorage.removeItem('auth_user')
        }
      } finally {
        if (!cancelled) {
          setAuthReady(true)
        }
      }
    }

    void syncSession()
    return () => {
      cancelled = true
    }
  }, [])

  const login = (
    companyId: number,
    email: string,
    subscriptionStatus: string,
    trialDaysRemaining: number,
    aiChatsRemaining: number
  ) => {
    const nextUser: UserInfo = {
      companyId,
      email,
      subscriptionTier: normalizeTier(subscriptionStatus),
      trialDaysRemaining,
      aiChatsRemaining,
    }

    setUser(nextUser)
    localStorage.setItem('auth_user', JSON.stringify(nextUser))
    setAuthReady(true)
  }

  const logout = async () => {
    try {
      await api.post(
        '/api/v1/auth/logout',
        {},
        { headers: { 'X-Skip-401-Redirect': 'true' } }
      )
    } catch {
      // Best effort server logout. We always clear local state.
    } finally {
      setUser(null)
      localStorage.removeItem('auth_user')
    }
  }

  const updateSubscription = (tier: string, daysRemaining: number, aiChatsRemaining: number) => {
    if (!user) return

    const updated: UserInfo = {
      ...user,
      subscriptionTier: normalizeTier(tier),
      trialDaysRemaining: daysRemaining,
      aiChatsRemaining,
    }

    setUser(updated)
    localStorage.setItem('auth_user', JSON.stringify(updated))
  }

  const updateAiChats = (remaining: number) => {
    if (!user) return

    const updated: UserInfo = { ...user, aiChatsRemaining: remaining }
    setUser(updated)
    localStorage.setItem('auth_user', JSON.stringify(updated))
  }

  const updateProfile = (email: string) => {
    if (!user) return

    const updated: UserInfo = { ...user, email }
    setUser(updated)
    localStorage.setItem('auth_user', JSON.stringify(updated))
  }

  const isPremium = user?.subscriptionTier === 'ACTIVE' || user?.subscriptionTier === 'TRIAL' || user?.subscriptionTier === 'MAX'
  const isFree = !isPremium
  const isTrial = user?.subscriptionTier === 'TRIAL'
  const isMax = user?.subscriptionTier === 'MAX'

  return (
    <AuthContext.Provider
      value={{
        user,
        isAuthenticated: !!user,
        authReady,
        isPremium,
        isFree,
        isTrial,
        isMax,
        login,
        logout,
        updateSubscription,
        updateAiChats,
        updateProfile,
      }}
    >
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used within AuthProvider')
  }
  return context
}
