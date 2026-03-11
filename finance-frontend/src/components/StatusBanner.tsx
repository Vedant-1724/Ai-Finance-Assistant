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
  const { user, isFree, isTrial, isMax } = useAuth()
  const [trialEligible, setTrialEligible] = useState(false)

  useEffect(() => {
    let cancelled = false

    if (!user || !isFree) {
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
  }, [user, isFree])

  if (!user) {
    return null
  }

  if (isMax) {
    return (
      <div
        className="status-banner banner-max"
        style={{
          backgroundColor: 'rgba(139, 92, 246, 0.1)',
          borderBottom: '1px solid rgba(139, 92, 246, 0.2)',
          color: '#c4b5fd',
        }}
      >
        <span className="banner-text">
          👑 <strong>Max Plan</strong> — full access unlocked.{' '}
          {user.aiChatsRemaining > 0
            ? `${user.aiChatsRemaining} AI chats remaining today.`
            : 'Daily AI chat limit reached. It resets at midnight.'}
        </span>
      </div>
    )
  }

  if (!isFree && !isTrial) {
    return (
      <div className="status-banner banner-active">
        <span className="banner-text">
          ✅ <strong>Pro Plan</strong> — premium features are active.{' '}
          {user.aiChatsRemaining > 0
            ? `${user.aiChatsRemaining} AI chats remaining today.`
            : 'Daily AI chat limit reached. It resets at midnight.'}
        </span>
      </div>
    )
  }

  if (isTrial) {
    const days = user.trialDaysRemaining ?? 0
    const urgent = days <= 1

    return (
      <div className="status-banner banner-trial">
        <span className="banner-text">
          {urgent ? '⚠️' : '🎉'} <strong>Premium Trial</strong> —{' '}
          {days <= 0 ? 'Your trial has ended.' : days === 1 ? 'Last day remaining.' : `${days} days remaining.`}{' '}
          AI chat starts on Pro and Max.
        </span>
        <button className="banner-cta" onClick={onUpgrade}>
          Upgrade for AI chat →
        </button>
      </div>
    )
  }

  return (
    <div className="status-banner banner-free">
      <span className="banner-text">
        🔓 <strong>Free Plan</strong> — core tracking is active.{' '}
        {trialEligible
          ? 'Start your one-time 3-day trial or upgrade for premium reports, health score, team tools, and AI chat.'
          : 'Upgrade for premium reports, health score, team tools, and AI chat.'}
      </span>
      <button
        className="btn-gradient"
        style={{ padding: '6px 16px', fontSize: '13px', marginLeft: '12px' }}
        onClick={onUpgrade}
      >
        {trialEligible ? '🚀 Start 3-Day Trial →' : '⭐ View Plans →'}
      </button>
    </div>
  )
}
