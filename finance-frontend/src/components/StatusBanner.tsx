// PATH: finance-frontend/src/components/StatusBanner.tsx
// Replaces TrialBanner.tsx — handles FREE, TRIAL, ACTIVE, EXPIRED states

import axios from 'axios'
import { useState } from 'react'
import { useAuth } from '../context/AuthContext'

interface Props { onUpgrade: () => void }

export default function StatusBanner({ onUpgrade }: Props) {
  const { user, isFree, isTrial, updateSubscription } = useAuth()
  const [starting, setStarting] = useState(false)
  const [trialStarted, setTrialStarted] = useState(false)

  // Check if trial was already used
  const trialAlreadyUsed = false // We rely on the server to tell us

  if (!user) return null

  const handleStartTrial = async () => {
    setStarting(true)
    try {
      const token = user.token
      const res = await axios.post(
        '/api/v1/subscription/start-trial', {},
        { headers: { Authorization: `Bearer ${token}` } }
      )
      const data = res.data as { tier: string; trialDaysRemaining: number; aiChatsRemaining: number }
      updateSubscription(data.tier, data.trialDaysRemaining, data.aiChatsRemaining)
      setTrialStarted(true)
    } catch (err: any) {
      const errCode = err?.response?.data?.error
      if (errCode === 'TRIAL_ALREADY_USED') {
        onUpgrade() // Redirect to subscription page
      }
    } finally {
      setStarting(false)
    }
  }

  if (trialStarted) {
    return (
      <div className="status-banner banner-trial">
        <span className="banner-text">🎉 Your 5-day free trial has started! Enjoy all premium features.</span>
      </div>
    )
  }

  if (isTrial) {
    return (
      <div className="status-banner banner-trial">
        <span className="banner-text">
          ⏳ <strong>Free Trial</strong> — {user.trialDaysRemaining} day{user.trialDaysRemaining !== 1 ? 's' : ''} remaining. Upgrade before it ends to keep premium access.
        </span>
        <button className="banner-cta" onClick={onUpgrade}>Upgrade to Pro</button>
      </div>
    )
  }

  if (isFree) {
    return (
      <div className="status-banner banner-free">
        <span className="banner-text">
          🆓 You're on the <strong>Free plan</strong> — limited features. Try 5 days of premium for free.
        </span>
        <div style={{ display: 'flex', gap: '8px' }}>
          <button className="banner-cta" onClick={handleStartTrial} disabled={starting}>
            {starting ? '⏳' : '🚀 Start Free Trial'}
          </button>
          <button
            onClick={onUpgrade}
            style={{ padding: '5px 12px', background: 'none', border: '1px solid rgba(59,130,246,0.3)', color: 'var(--text-accent)', borderRadius: '6px', fontSize: '12px', cursor: 'pointer' }}
          >
            View Plans
          </button>
        </div>
      </div>
    )
  }

  // ACTIVE — no banner needed (or optional success message)
  return null
}