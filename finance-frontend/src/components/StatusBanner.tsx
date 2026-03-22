import { useEffect, useState } from 'react'
import axios from 'axios'
import api from '../api'
import { useAuth } from '../context/AuthContext'

interface StatusBannerProps {
  onUpgrade: () => void
}

interface SubscriptionStatusResponse {
  trialEligible: boolean
}

export default function StatusBanner({ onUpgrade }: StatusBannerProps) {
  const { user, isFree, isTrial, capabilities } = useAuth()
  const [trialEligible, setTrialEligible] = useState(false)

  useEffect(() => {
    let cancelled = false

    if (!user || !isFree || !capabilities.canManageBilling) {
      setTrialEligible(false)
      return () => {
        cancelled = true
      }
    }

    const loadStatus = async () => {
      try {
        const res = await api.get<SubscriptionStatusResponse>('/api/v1/subscription/status')
        if (!cancelled) {
          setTrialEligible(Boolean(res.data.trialEligible))
        }
      } catch (error) {
        if (!cancelled && !(axios.isAxiosError(error) && error.response?.status === 401)) {
          setTrialEligible(false)
        }
      }
    }

    void loadStatus()

    return () => {
      cancelled = true
    }
  }, [user, isFree, capabilities.canManageBilling])

  if (!user || user.subscriptionTier === 'ACTIVE' || user.subscriptionTier === 'MAX' || isTrial) {
    return null
  }

  return (
    <div className="premium-status-banner">
      <div className="banner-content">
        <div className="banner-icon-wrapper">
          <span className="banner-icon">✨</span>
        </div>
        <div className="banner-text-content">
          <h4 className="banner-title">Unlock Premium</h4>
          <p className="banner-subtitle">
            {capabilities.canManageBilling && trialEligible
              ? 'Start your 3-day premium trial today. Access advanced AI insights, team syncing, and full reporting.'
              : capabilities.canManageBilling
                ? 'Upgrade for premium reports, health score, team tools, and AI chat.'
                : 'Your workspace owner can upgrade for premium reports, health tools, and AI chat.'}
          </p>
        </div>
      </div>
      <button
        className="btn-liquid-glass banner-action-btn"
        onClick={onUpgrade}
      >
        <span>
          {capabilities.canManageBilling
            ? trialEligible ? 'Start 3-Day Trial' : 'View Plans'
            : 'View Workspace Plan'}
        </span>
      </button>
    </div>
  )
}
