// PATH: finance-frontend/src/components/StatusBanner.tsx
// Shows a contextual top banner based on subscription tier:
//   FREE  → prompt to start trial
//   TRIAL → countdown of days remaining
//   ACTIVE→ thank-you / renewal date (silent — no banner clutter)

import { useAuth } from '../context/AuthContext'

interface StatusBannerProps {
  onUpgrade: () => void
}

export default function StatusBanner({ onUpgrade }: StatusBannerProps) {
  const { user, isPremium, isFree, isTrial } = useAuth()

  if (!user) return null

  // ── Active paid subscriber — no intrusive banner ───────────────────────────
  if (isPremium && !isTrial) {
    return (
      <div className="status-banner banner-active">
        <span className="banner-text">
          ✅ <strong>Pro Plan</strong> — All features unlocked. {
            user.aiChatsRemaining > 0
              ? `${user.aiChatsRemaining} AI chats remaining today.`
              : 'Daily AI chat limit reached — resets at midnight.'
          }
        </span>
      </div>
    )
  }

  // ── Trial user ─────────────────────────────────────────────────────────────
  if (isTrial) {
    const days = user.trialDaysRemaining ?? 0
    const urgent = days <= 1

    return (
      <div className="status-banner banner-trial">
        <span className="banner-text">
          {urgent ? '⚠️' : '🎉'}{' '}
          <strong>Free Trial</strong> —{' '}
          {days <= 0
            ? 'Your trial has expired.'
            : days === 1
            ? 'Last day of your trial!'
            : `${days} days remaining.`}{' '}
          {user.aiChatsRemaining > 0
            ? `${user.aiChatsRemaining} AI chats left today.`
            : 'Daily AI chat limit reached.'}
        </span>
        <button className="banner-cta" onClick={onUpgrade}>
          Upgrade to Pro →
        </button>
      </div>
    )
  }

  // ── Free tier ──────────────────────────────────────────────────────────────
  if (isFree) {
    return (
      <div className="status-banner banner-free">
        <span className="banner-text">
          🔓 <strong>Free Plan</strong> — Limited to 10 transactions &amp; 3 AI chats/day.
          Unlock forecasts, charts, invoices, tax tools &amp; more.
        </span>
        <button className="banner-cta" onClick={onUpgrade}>
          Start Free Trial →
        </button>
      </div>
    )
  }

  return null
}