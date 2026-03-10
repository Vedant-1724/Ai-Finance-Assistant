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

  const bg = isExpired ? '#3b0a0a' : isUrgent ? '#2d1800' : '#0a1f3b'
  const border = isExpired ? '#ef4444' : isUrgent ? '#f97316' : '#3b82f6'
  const color = isExpired ? '#fca5a5' : isUrgent ? '#fdba74' : '#93c5fd'
  const btnBg = isExpired ? '#dc2626' : isUrgent ? '#ea580c' : '#2563eb'
  const icon = isExpired ? '🔒' : isUrgent ? '⚠️' : '🕐'

  const message = isExpired
    ? 'Your premium access is inactive. Upgrade to continue using premium features.'
    : isUrgent
      ? 'Your premium trial ends tomorrow. Upgrade now to keep access uninterrupted.'
      : `Premium Trial — ${sub.trialDaysRemaining} day${sub.trialDaysRemaining !== 1 ? 's' : ''} remaining`

  return (
    <div
      style={{
        background: bg,
        borderBottom: `2px solid ${border}`,
        padding: '10px 20px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        gap: 16,
        fontSize: 14,
        color,
        flexWrap: 'wrap',
      }}
    >
      <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        <span>{icon}</span>
        <span>{message}</span>
      </span>

      <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
        <button
          onClick={onUpgrade}
          style={{
            background: btnBg,
            color: '#fff',
            border: 'none',
            borderRadius: 6,
            padding: '6px 16px',
            cursor: 'pointer',
            fontWeight: 700,
            fontSize: 13,
            whiteSpace: 'nowrap',
          }}
        >
          {isExpired ? '🚀 Upgrade Now' : '⭐ View Plans'}
        </button>

        {!isExpired && (
          <button
            onClick={() => setDismissed(true)}
            title="Dismiss"
            style={{
              background: 'transparent',
              border: 'none',
              color,
              cursor: 'pointer',
              fontSize: 18,
              lineHeight: 1,
              padding: '0 4px',
            }}
          >
            ×
          </button>
        )}
      </div>
    </div>
  )
}
