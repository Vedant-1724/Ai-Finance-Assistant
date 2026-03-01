// CHANGES:
//  - Added subscriptionTier, trialDaysRemaining, aiChatsRemaining to UserInfo
//  - Added updateSubscription() so components can refresh tier after start-trial
//  - isPremium / isFree / isTrial computed helpers

import { createContext, useContext, useState, type ReactNode } from 'react'

interface UserInfo {
  token:              string
  companyId:          number
  email:              string
  subscriptionTier:   'FREE' | 'TRIAL' | 'ACTIVE'
  trialDaysRemaining: number
  aiChatsRemaining:   number
}

interface AuthContextType {
  user:             UserInfo | null
  isAuthenticated:  boolean
  isPremium:        boolean
  isFree:           boolean
  isTrial:          boolean
  login:            (token: string, companyId: number, email: string,
                     subscriptionStatus: string, trialDaysRemaining: number,
                     aiChatsRemaining: number) => void
  logout:           () => void
  updateSubscription: (tier: string, daysRemaining: number, aiChatsRemaining: number) => void
  updateAiChats:    (remaining: number) => void
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
    token: string,
    companyId: number,
    email: string,
    subscriptionStatus: string,
    trialDaysRemaining: number,
    aiChatsRemaining: number
  ) => {
    const tier = (subscriptionStatus === 'ACTIVE' || subscriptionStatus === 'TRIAL')
      ? subscriptionStatus as 'ACTIVE' | 'TRIAL'
      : 'FREE'

    const userInfo: UserInfo = {
      token,
      companyId,
      email,
      subscriptionTier:   tier,
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
    const normalizedTier = (tier === 'ACTIVE' || tier === 'TRIAL') ? tier as 'ACTIVE' | 'TRIAL' : 'FREE'
    const updated = {
      ...user,
      subscriptionTier:   normalizedTier,
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

  const isPremium = user?.subscriptionTier === 'ACTIVE' || user?.subscriptionTier === 'TRIAL'
  const isFree    = !isPremium
  const isTrial   = user?.subscriptionTier === 'TRIAL'

  return (
    <AuthContext.Provider value={{
      user,
      isAuthenticated: !!user,
      isPremium,
      isFree,
      isTrial,
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