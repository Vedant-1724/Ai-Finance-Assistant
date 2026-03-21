import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import api from '../api'

type SubscriptionTier = 'FREE' | 'TRIAL' | 'ACTIVE' | 'MAX'
type WorkspaceRole = 'OWNER' | 'EDITOR' | 'VIEWER'

interface UserCapabilities {
  canEditFinance: boolean
  canUseAiTools: boolean
  canManageBilling: boolean
  canManageTeam: boolean
  canManageCompanyProfile: boolean
  canUseBankSync: boolean
  canViewAudit: boolean
}

interface UserInfo {
  companyId: number
  email: string
  role: WorkspaceRole
  subscriptionTier: SubscriptionTier
  trialDaysRemaining: number
  aiChatsRemaining: number
  aiChatDailyLimit: number
  canManageBilling: boolean
}

interface AuthContextType {
  user: UserInfo | null
  capabilities: UserCapabilities
  isAuthenticated: boolean
  authReady: boolean
  isPremium: boolean
  isFree: boolean
  isTrial: boolean
  isMax: boolean
  login: (payload: AuthMeResponse) => void
  logout: () => Promise<void>
  refreshSession: () => Promise<void>
  updateSubscription: (
    tier: string,
    daysRemaining: number,
    aiChatsRemaining: number,
    aiChatDailyLimit?: number
  ) => void
  updateAiChats: (remaining: number) => void
  updateProfile: (email: string) => void
}

interface AuthMeResponse {
  companyId: number
  email: string
  role: string
  subscriptionStatus: string
  trialDaysRemaining: number
  aiChatsRemaining: number
  aiChatDailyLimit: number
  canManageBilling?: boolean
}

const AuthContext = createContext<AuthContextType | null>(null)

function normalizeTier(subscriptionStatus: string): SubscriptionTier {
  if (subscriptionStatus === 'ACTIVE' || subscriptionStatus === 'TRIAL' || subscriptionStatus === 'MAX') {
    return subscriptionStatus
  }
  return 'FREE'
}

function normalizeRole(role: string | undefined): WorkspaceRole {
  if (role === 'OWNER' || role === 'EDITOR') {
    return role
  }
  return 'VIEWER'
}

function getCapabilities(user: UserInfo | null): UserCapabilities {
  if (!user) {
    return {
      canEditFinance: false,
      canUseAiTools: false,
      canManageBilling: false,
      canManageTeam: false,
      canManageCompanyProfile: false,
      canUseBankSync: false,
      canViewAudit: false,
    }
  }

  const owner = user.role === 'OWNER'
  const editor = user.role === 'EDITOR'

  return {
    canEditFinance: owner || editor,
    canUseAiTools: owner || editor,
    canManageBilling: owner || user.canManageBilling,
    canManageTeam: owner,
    canManageCompanyProfile: owner,
    canUseBankSync: owner,
    canViewAudit: owner,
  }
}

function buildUserInfo(data: AuthMeResponse): UserInfo {
  return {
    companyId: data.companyId,
    email: data.email,
    role: normalizeRole(data.role),
    subscriptionTier: normalizeTier(data.subscriptionStatus),
    trialDaysRemaining: data.trialDaysRemaining,
    aiChatsRemaining: data.aiChatsRemaining,
    aiChatDailyLimit: data.aiChatDailyLimit ?? 0,
    canManageBilling: Boolean(data.canManageBilling ?? data.role === 'OWNER'),
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

        // On ANY error (401, network failure, DNS error), clear stale session
        setUser(null)
        localStorage.removeItem('auth_user')
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

  const login = (payload: AuthMeResponse) => {
    const nextUser = buildUserInfo(payload)
    setUser(nextUser)
    localStorage.setItem('auth_user', JSON.stringify(nextUser))
    setAuthReady(true)
  }

  const refreshSession = async () => {
    const res = await api.get<AuthMeResponse>('/api/v1/auth/me', {
      headers: { 'X-Skip-401-Redirect': 'true' },
    })
    const nextUser = buildUserInfo(res.data)
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

  const updateSubscription = (
    tier: string,
    daysRemaining: number,
    aiChatsRemaining: number,
    aiChatDailyLimit?: number
  ) => {
    if (!user) return

    const updated: UserInfo = {
      ...user,
      subscriptionTier: normalizeTier(tier),
      trialDaysRemaining: daysRemaining,
      aiChatsRemaining,
      aiChatDailyLimit: typeof aiChatDailyLimit === 'number' ? aiChatDailyLimit : user.aiChatDailyLimit,
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
  const capabilities = getCapabilities(user)

  return (
    <AuthContext.Provider
      value={{
        user,
        capabilities,
        isAuthenticated: !!user,
        authReady,
        isPremium,
        isFree,
        isTrial,
        isMax,
        login,
        logout,
        refreshSession,
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
