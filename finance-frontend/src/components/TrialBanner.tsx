import { useEffect, useState } from 'react'
import axios from 'axios'
import api from '../api'

interface TrialBannerProps {
  onUpgrade: () => void
}

interface SubscriptionStatusResponse {
  tier: 'FREE' | 'TRIAL' | 'ACTIVE' | 'MAX'
  status: string
  trialDaysRemaining: number
  hasPremiumAccess: boolean
}

export default function TrialBanner({ onUpgrade }: TrialBannerProps) {
  const [sub, setSub] = useState<SubscriptionStatusResponse | null>(null)
  const [dismissed, setDismissed] = useState(false)

  useEffect(() => {
    const load = async () => {
      try {
        const res = await api.get<SubscriptionStatusResponse>('/api/v1/payment/status')
        setSub(res.data)
      } catch (error) {
        if (axios.isAxiosError(error) && error.response?.status === 401) {
          return
        }
      }
    }

    void load()
  }, [])

  if (!sub || sub.tier === 'ACTIVE' || sub.tier === 'MAX') {
    return null
  }

  if (dismissed && sub.hasPremiumAccess) {
    return null
  }

  const isExpired = !sub.hasPremiumAccess || sub.status === 'EXPIRED' || sub.status === 'CANCELLED' || sub.tier === 'FREE'
  const isUrgent = !isExpired && sub.trialDaysRemaining <= 1

  return (
    <div className={`premium-trial-banner ${isExpired ? 'expired' : isUrgent ? 'urgent' : 'active'}`}>
      <div className="banner-content">
        <div className="banner-icon-wrapper">
          <span className="banner-icon">{isExpired ? '🔒' : isUrgent ? '⚠️' : '🕐'}</span>
        </div>
        <div className="banner-text-content">
          <h4 className="banner-title">
            {isExpired ? 'Premium Access Inactive' : isUrgent ? 'Trial Ending Soon' : 'Premium Trial Active'}
          </h4>
          <p className="banner-subtitle">
            {isExpired
              ? 'Upgrade to continue using premium features like AI Assistant and Health Score.'
              : isUrgent
                ? 'Your premium trial ends tomorrow. Upgrade now to avoid interruption.'
                : `${sub.trialDaysRemaining} day${sub.trialDaysRemaining !== 1 ? 's' : ''} remaining in your trial.`}
          </p>
        </div>
      </div>

      <div className="banner-actions">
        <button className="btn-liquid-glass" onClick={onUpgrade}>
          <span>{isExpired ? 'Upgrade Now' : 'View Plans'}</span>
        </button>
        {!isExpired && (
          <button className="btn-dismiss" onClick={() => setDismissed(true)} title="Dismiss">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
              <line x1="18" y1="6" x2="6" y2="18" />
              <line x1="6" y1="6" x2="18" y2="18" />
            </svg>
          </button>
        )}
      </div>
    </div>
  )
}
