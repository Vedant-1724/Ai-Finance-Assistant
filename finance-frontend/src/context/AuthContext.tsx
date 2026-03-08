// CHANGES:
//  - Added subscriptionTier, trialDaysRemaining, aiChatsRemaining to UserInfo
//  - Added updateSubscription() so components can refresh tier after start-trial
//  - isPremium / isFree / isTrial computed helpers

import { createContext, useContext, useState, type ReactNode } from 'react'

interface UserInfo {
  companyId: number
  email: string
  subscriptionTier: 'FREE' | 'TRIAL' | 'ACTIVE' | 'MAX'
  trialDaysRemaining: number
  aiChatsRemaining: number
}

interface AuthContextType {
  user: UserInfo | null
  isAuthenticated: boolean
  isPremium: boolean
  isFree: boolean
  isTrial: boolean
  isMax: boolean
  login: (companyId: number, email: string,
    subscriptionStatus: string, trialDaysRemaining: number,
    aiChatsRemaining: number) => void
  logout: () => void
  updateSubscription: (tier: string, daysRemaining: number, aiChatsRemaining: number) => void
  updateAiChats: (remaining: number) => void
}

const AuthContext = createContext<AuthContextType | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserInfo | null>(() => {
    try {
      const stored = localStorage.getItem('auth_user')
      return stored ? JSON.parse(stored) : null
    } catch {
      return null
    }
  })

  const login = (
    companyId: number,
    email: string,
    subscriptionStatus: string,
    trialDaysRemaining: number,
    aiChatsRemaining: number
  ) => {
    const tier = (subscriptionStatus === 'ACTIVE' || subscriptionStatus === 'TRIAL' || subscriptionStatus === 'MAX')
      ? subscriptionStatus as 'ACTIVE' | 'TRIAL' | 'MAX'
      : 'FREE'

    const userInfo: UserInfo = {
      companyId,
      email,
      subscriptionTier: tier,
      trialDaysRemaining,
      aiChatsRemaining,
    }
    setUser(userInfo)
    localStorage.setItem('auth_user', JSON.stringify(userInfo))
  }

  const logout = () => {
    setUser(null)
    localStorage.removeItem('auth_user')
  }

  const updateSubscription = (tier: string, daysRemaining: number, aiChatsRemaining: number) => {
    if (!user) return
    const normalizedTier: 'ACTIVE' | 'TRIAL' | 'FREE' | 'MAX' = (tier === 'ACTIVE' || tier === 'TRIAL' || tier === 'MAX') ? tier as 'ACTIVE' | 'TRIAL' | 'MAX' : 'FREE'
    const updated: UserInfo = {
      ...user,
      subscriptionTier: normalizedTier,
      trialDaysRemaining: daysRemaining,
      aiChatsRemaining,
    }
    setUser(updated)
    localStorage.setItem('auth_user', JSON.stringify(updated))
  }

  const updateAiChats = (remaining: number) => {
    if (!user) return
    const updated = { ...user, aiChatsRemaining: remaining }
    setUser(updated)
    localStorage.setItem('auth_user', JSON.stringify(updated))
  }

  const isPremium = user?.subscriptionTier === 'ACTIVE' || user?.subscriptionTier === 'TRIAL' || user?.subscriptionTier === 'MAX'
  const isFree = !isPremium
  const isTrial = user?.subscriptionTier === 'TRIAL'
  const isMax = user?.subscriptionTier === 'MAX'

  return (
    <AuthContext.Provider value={{
      user,
      isAuthenticated: !!user,
      isPremium,
      isFree,
      isTrial,
      isMax,
      login,
      logout,
      updateSubscription,
      updateAiChats,
    }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}