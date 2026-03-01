// PATH: finance-frontend/src/components/TrialBanner.tsx
//
// NEW FILE — shows a dismissible banner:
//   - Blue  : Trial active,  N days remaining
//   - Orange: Trial active,  1 day remaining
//   - Red   : Trial expired / subscription cancelled → shows hard CTA
//
// Polls /api/v1/payment/status once on mount.
// Hidden for ACTIVE subscribers.

import { useEffect, useState } from 'react'
import api from '../api'

interface SubStatus {
  status:        'TRIAL' | 'ACTIVE' | 'EXPIRED' | 'CANCELLED'
  daysRemaining: number
  canAccess:     boolean
}

interface TrialBannerProps {
  onUpgrade: () => void
}

export default function TrialBanner({ onUpgrade }: TrialBannerProps) {
  const [sub,       setSub]       = useState<SubStatus | null>(null)
  const [dismissed, setDismissed] = useState(false)

  useEffect(() => {
    api.get<SubStatus>('/api/v1/payment/status')
      .then(r => setSub(r.data))
      .catch(() => {/* ignore if endpoint unavailable */})
  }, [])

  // Nothing to show for active paid subscribers or while loading
  if (!sub || sub.status === 'ACTIVE') return null
  if (dismissed && sub.canAccess)     return null   // allow dismissing warning-only banners

  const isExpired = !sub.canAccess || sub.status === 'EXPIRED' || sub.status === 'CANCELLED'
  const isUrgent  = !isExpired && sub.daysRemaining <= 1

  const bg      = isExpired ? '#3b0a0a' : isUrgent ? '#2d1800' : '#0a1f3b'
  const border  = isExpired ? '#ef4444' : isUrgent ? '#f97316' : '#3b82f6'
  const color   = isExpired ? '#fca5a5' : isUrgent ? '#fdba74' : '#93c5fd'
  const btnBg   = isExpired ? '#dc2626' : isUrgent ? '#ea580c' : '#2563eb'
  const icon    = isExpired ? '🔒' : isUrgent ? '⚠️' : '🕐'

  const message = isExpired
    ? 'Your free trial has ended. Upgrade to continue accessing your financial data.'
    : isUrgent
    ? 'Your free trial expires tomorrow! Upgrade now to keep uninterrupted access.'
    : `Free Trial — ${sub.daysRemaining} day${sub.daysRemaining !== 1 ? 's' : ''} remaining`

  return (
    <div style={{
      background:     bg,
      borderBottom:   `2px solid ${border}`,
      padding:        '10px 20px',
      display:        'flex',
      alignItems:     'center',
      justifyContent: 'space-between',
      gap:            16,
      fontSize:       14,
      color,
      flexWrap:       'wrap',
    }}>
      <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        <span>{icon}</span>
        <span>{message}</span>
      </span>

      <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
        <button
          onClick={onUpgrade}
          style={{
            background:   btnBg,
            color:        '#fff',
            border:       'none',
            borderRadius: 6,
            padding:      '6px 16px',
            cursor:       'pointer',
            fontWeight:   700,
            fontSize:     13,
            whiteSpace:   'nowrap',
          }}
        >
          {isExpired ? '🚀 Upgrade Now' : '⭐ Upgrade — ₹499/mo'}
        </button>

        {/* Allow dismissing non-expired banners */}
        {!isExpired && (
          <button
            onClick={() => setDismissed(true)}
            title="Dismiss"
            style={{
              background:   'transparent',
              border:       'none',
              color,
              cursor:       'pointer',
              fontSize:     18,
              lineHeight:   1,
              padding:      '0 4px',
            }}
          >×</button>
        )}
      </div>
    </div>
  )
}
