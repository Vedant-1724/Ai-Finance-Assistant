import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import axios from 'axios'
import api from '../api'
import { useAuth } from '../context/AuthContext'

declare global {
  interface Window {
    Razorpay: new (options: Record<string, unknown>) => { open: () => void }
  }
}

interface SubscriptionStatusResponse {
  companyId: number
  role: 'OWNER' | 'EDITOR' | 'VIEWER'
  tier: 'FREE' | 'TRIAL' | 'ACTIVE' | 'MAX'
  status: string
  trialDaysRemaining: number
  aiChatsRemaining: number
  aiChatDailyLimit: number
  hasPremiumAccess: boolean
  trialAlreadyUsed: boolean
  trialEligible: boolean
  canManageBilling: boolean
  expiresAt: string
  paymentConfigured: boolean
  paymentMessage?: string
  message?: string
}

interface PaymentOrderResponse {
  id?: string
  orderId?: string
  amount: number
  currency: string
  keyId: string
  plan: string
  message?: string
  error?: string
  paymentConfigured?: boolean
}

const FREE_FEATURES = [
  'Manual transaction tracking',
  'Statement import and CSV export',
  'Interactive charts, budget planner, and core dashboard',
  'Account settings and security tools',
  'No AI chat',
]

const TRIAL_FEATURES = [
  'Premium reports for 3 days',
  'Advanced analytics, tax tools, and health score',
  'Team workspace and audit log',
  'Forecasts and anomaly detection',
  'AI chat unlocks on Pro and Max',
]

const PRO_FEATURES = [
  'Everything in Trial',
  'AI chat: 20 messages per day',
  'Recurring finance workflows',
  'Priority premium feature access',
  '₹399 per month billed monthly',
]

const MAX_FEATURES = [
  'Everything in Pro',
  'AI chat: 50 messages per day',
  'Highest daily usage allowance',
  'Best fit for power users and teams',
  '₹899 per month billed monthly',
]

async function ensureRazorpayLoaded() {
  if (window.Razorpay) {
    return
  }

  await new Promise<void>((resolve, reject) => {
    const script = document.createElement('script')
    script.src = 'https://checkout.razorpay.com/v1/checkout.js'
    script.async = true
    script.onload = () => resolve()
    script.onerror = () => reject(new Error('Failed to load Razorpay checkout.'))
    document.body.appendChild(script)
  })
}

export default function SubscriptionPage() {
  const { user, updateSubscription, capabilities } = useAuth()
  const navigate = useNavigate()
  const [subStatus, setSubStatus] = useState<SubscriptionStatusResponse | null>(null)
  const [loading, setLoading] = useState(false)
  const [trialLoading, setTrialLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [msg, setMsg] = useState<string | null>(null)

  const loadStatus = async () => {
    if (!user) return
    try {
      const response = await api.get<SubscriptionStatusResponse>('/api/v1/subscription/status')
      setSubStatus(response.data)
    } catch {
      // Keep page usable with auth context fallback.
    }
  }

  useEffect(() => {
    void loadStatus()
  }, [user])

  const handleStartTrial = async () => {
    setTrialLoading(true)
    setError(null)
    setMsg(null)

    try {
      const res = await api.post<SubscriptionStatusResponse>('/api/v1/subscription/start-trial')
      setSubStatus(res.data)
      updateSubscription(res.data.tier, res.data.trialDaysRemaining, res.data.aiChatsRemaining, res.data.aiChatDailyLimit)
      setMsg(res.data.message ?? 'Your 3-day premium trial has started.')
    } catch (err) {
      if (axios.isAxiosError(err)) {
        const data = err.response?.data as { error?: string; message?: string } | undefined
        setError(data?.message ?? data?.error ?? 'Failed to start trial. Please try again.')
      } else {
        setError('Failed to start trial. Please try again.')
      }
    } finally {
      setTrialLoading(false)
    }
  }

  const handleSubscribe = async (amount: number, tier: 'ACTIVE' | 'MAX', label: 'Pro' | 'Max') => {
    if (!user) return
    if (subStatus && !subStatus.paymentConfigured) {
      setError(subStatus.paymentMessage ?? 'Online payments are unavailable in this environment.')
      return
    }

    setLoading(true)
    setError(null)
    setMsg(null)

    try {
      const orderRes = await api.post<PaymentOrderResponse>('/api/v1/payment/create-order', {
        amount,
        currency: 'INR',
        receipt: `receipt_${Date.now()}`,
      })
      const order = orderRes.data
      const orderId = order.orderId ?? order.id

      if (!orderId) {
        throw new Error(order.message || order.error || 'Payment order was not created.')
      }

      await ensureRazorpayLoaded()

      const options = {
        key: order.keyId,
        amount: order.amount,
        currency: order.currency,
        name: 'FinanceAI',
        description: `${label} subscription`,
        order_id: orderId,
        prefill: { email: user.email },
        handler: async (paymentResponse: Record<string, string>) => {
          await api.post('/api/v1/payment/verify', { ...paymentResponse, plan: tier })
          const statusRes = await api.get<SubscriptionStatusResponse>('/api/v1/payment/status')
          setSubStatus(statusRes.data)
          updateSubscription(statusRes.data.tier, statusRes.data.trialDaysRemaining, statusRes.data.aiChatsRemaining, statusRes.data.aiChatDailyLimit)
          setMsg(`Welcome to FinanceAI ${label}.`)
          navigate('/', { replace: true })
        },
        theme: { color: label === 'Max' ? '#6d28d9' : '#2563eb' },
      }

      new window.Razorpay(options).open()
    } catch (err) {
      if (axios.isAxiosError(err)) {
        const data = err.response?.data as { error?: string; message?: string } | undefined
        setError(data?.message ?? data?.error ?? 'Failed to open payment. Please try again.')
      } else if (err instanceof Error) {
        setError(err.message)
      } else {
        setError('Failed to open payment. Please try again.')
      }
    } finally {
      setLoading(false)
    }
  }

  const currentTier = subStatus?.tier ?? user?.subscriptionTier ?? 'FREE'
  const isActive = currentTier === 'ACTIVE'
  const isTrial = currentTier === 'TRIAL'
  const isMax = currentTier === 'MAX'
  const isFree = currentTier === 'FREE'
  const canManageBilling = capabilities.canManageBilling && (subStatus?.canManageBilling ?? true)
  const paymentConfigured = subStatus?.paymentConfigured ?? true
  const trialEligible = canManageBilling && (subStatus?.trialEligible ?? false)
  const trialStatusKnown = subStatus !== null
  const showTrialPlan = canManageBilling && (isFree || isTrial)
  const freeSummary = isFree
    ? canManageBilling
      ? trialEligible
        ? 'Start with Free, then activate the 3-day premium trial or subscribe when you need AI chat.'
        : trialStatusKnown
          ? 'Your free plan is active. The one-time trial is unavailable, so upgrade when you need AI chat.'
          : 'Your free plan is active. Checking trial eligibility and upgrade options.'
      : 'This workspace is on the Free plan. Only the workspace owner can start the trial or upgrade.'
    : ''
  const heroSummary = isMax
    ? canManageBilling
      ? 'You are on Max with the highest AI allowance.'
      : 'This workspace is on Max with the highest AI allowance.'
    : isActive
      ? canManageBilling
        ? `You are on Pro with ${subStatus?.aiChatsRemaining ?? user?.aiChatsRemaining ?? 0} AI chats left today.`
        : `This workspace is on Pro with ${subStatus?.aiChatsRemaining ?? user?.aiChatsRemaining ?? 0} AI chats left today.`
      : isTrial
      ? canManageBilling
        ? `Premium trial active: ${subStatus?.trialDaysRemaining ?? 0} day(s) remaining.`
        : `This workspace is on a premium trial with ${subStatus?.trialDaysRemaining ?? 0} day(s) remaining.`
      : freeSummary

  return (
    <div style={{ maxWidth: 1280, margin: '0 auto', padding: '32px 20px', position: 'relative' }}>
      <button
        onClick={() => navigate('/')}
        aria-label="Close"
        style={{
          position: 'absolute',
          top: 16,
          right: 20,
          background: 'transparent',
          border: 'none',
          color: 'var(--text-muted)',
          fontSize: 28,
          cursor: 'pointer',
          padding: 8,
          lineHeight: 1,
        }}
      >
        ✕
      </button>

      <div style={{ textAlign: 'center', marginBottom: 40 }}>
        <h1 className="page-title" style={{ fontSize: 28, marginBottom: 8 }}>Choose your plan</h1>
        <p style={{ color: 'var(--text-secondary)', fontSize: 15 }}>
          {heroSummary}
        </p>
        {!canManageBilling && (
          <div className="card" style={{ maxWidth: 640, margin: '16px auto 0', textAlign: 'left', background: 'rgba(59,130,246,0.08)', border: '1px solid rgba(59,130,246,0.25)' }}>
            <strong style={{ display: 'block', marginBottom: 6, color: '#93c5fd' }}>Read-only workspace billing</strong>
            <span style={{ color: 'var(--text-secondary)' }}>
              You can view the current workspace plan here, but only the workspace owner can start the trial, purchase Pro, or upgrade to Max.
            </span>
          </div>
        )}
        {msg && <div className="success-box" style={{ maxWidth: 520, margin: '16px auto 0', textAlign: 'left' }}>✅ {msg}</div>}
        {error && <div className="error-box" style={{ maxWidth: 520, margin: '16px auto 0', textAlign: 'left' }}>⚠️ {error}</div>}
        {!paymentConfigured && subStatus?.paymentMessage && (
          <div className="card" style={{ maxWidth: 640, margin: '16px auto 0', textAlign: 'left', background: 'rgba(245,158,11,0.08)', border: '1px solid rgba(245,158,11,0.25)' }}>
            <strong style={{ display: 'block', marginBottom: 6, color: 'var(--amber)' }}>Payments unavailable</strong>
            <span style={{ color: 'var(--text-secondary)' }}>{subStatus.paymentMessage}</span>
          </div>
        )}
      </div>

      <div className="plans-grid" style={{ display: 'flex', flexDirection: 'row', justifyContent: 'center', gap: 24, flexWrap: 'nowrap', overflowX: 'auto', paddingBottom: 16 }}>
        <div className={`plan-card ${isFree ? 'featured' : ''}`} style={{ flex: 1, minWidth: 260 }}>
          {isFree && <div className="plan-badge">Current Plan</div>}
          <div className="plan-name">Free</div>
          <div className="plan-price">₹0</div>
          <div className="plan-period">forever</div>
          <ul className="plan-features">
            {FREE_FEATURES.map(feature => (
              <li key={feature} className="plan-feature"><span className="feat-icon">✓</span> {feature}</li>
            ))}
          </ul>
          <button className="btn-secondary" style={{ width: '100%' }} disabled>
            {isFree ? 'Current Plan' : 'Reference Only'}
          </button>
        </div>

        {showTrialPlan && (
          <div className={`plan-card ${isTrial ? 'featured' : ''}`} style={{ flex: 1, minWidth: 260 }}>
            {isTrial && <div className="plan-badge">Active Trial</div>}
            {!isTrial && trialEligible && (
              <div className="plan-badge" style={{ background: 'rgba(245,158,11,0.9)' }}>Try Free</div>
            )}
            <div className="plan-name" style={{ color: 'var(--amber)' }}>Premium Trial</div>
            <div className="plan-price" style={{ color: 'var(--amber)' }}>₹0</div>
            <div className="plan-period">for 3 days · Free tier only</div>
            <ul className="plan-features">
              {TRIAL_FEATURES.map(feature => (
                <li key={feature} className="plan-feature"><span className="feat-icon" style={{ color: 'var(--amber)' }}>✓</span> {feature}</li>
              ))}
            </ul>
            {isTrial ? (
              <button className="btn-secondary" style={{ width: '100%' }} disabled>
                Trial Active · {subStatus?.trialDaysRemaining ?? 0}d left
              </button>
            ) : trialEligible ? (
              <button className="btn-gradient" style={{ width: '100%' }} onClick={handleStartTrial} disabled={trialLoading || loading}>
                {trialLoading ? '⏳ Starting…' : '🚀 Start 3-Day Trial'}
              </button>
            ) : (
              <button className="btn-secondary" style={{ width: '100%' }} disabled>
                {trialStatusKnown ? (subStatus?.trialAlreadyUsed ? 'Trial Already Used' : 'Free Tier Only') : 'Checking Eligibility…'}
              </button>
            )}
          </div>
        )}

        <div className={`plan-card ${isActive ? 'featured' : ''}`} style={{ flex: 1, minWidth: 260, borderColor: isActive ? 'rgba(59,130,246,0.5)' : undefined }}>
          {isActive && <div className="plan-badge">Current Plan</div>}
          {!isActive && !isMax && <div className="plan-badge">Recommended</div>}
          <div className="plan-name" style={{ color: 'var(--blue)' }}>Pro</div>
          <div className="plan-price" style={{ color: 'var(--blue)' }}>₹399</div>
          <div className="plan-period">per month</div>
          <ul className="plan-features">
            {PRO_FEATURES.map(feature => (
              <li key={feature} className="plan-feature"><span className="feat-icon" style={{ color: 'var(--blue)' }}>✓</span> {feature}</li>
            ))}
          </ul>
          <button
            className="btn-gradient"
            style={{ width: '100%', opacity: paymentConfigured ? 1 : 0.6 }}
            onClick={() => { void handleSubscribe(39900, 'ACTIVE', 'Pro') }}
            disabled={loading || isActive || isMax || !paymentConfigured || !canManageBilling}
          >
            {loading
              ? '⏳ Opening payment…'
              : isActive
                ? '✅ Current Plan'
                : isMax
                  ? 'Included in Max'
                  : !canManageBilling
                    ? 'Owner manages billing'
                  : paymentConfigured
                    ? '💳 Subscribe — ₹399/mo'
                    : 'Payments unavailable'}
          </button>
        </div>

        <div className={`plan-card ${isMax ? 'featured' : ''}`} style={{ flex: 1, minWidth: 260, borderColor: isMax ? 'rgba(139,92,246,0.5)' : undefined }}>
          {isMax && <div className="plan-badge" style={{ background: '#4c1d95', color: '#c4b5fd' }}>Current Plan</div>}
          {!isMax && <div className="plan-badge" style={{ background: '#4c1d95', color: '#c4b5fd' }}>Ultimate</div>}
          <div className="plan-name" style={{ color: '#a78bfa' }}>Max</div>
          <div className="plan-price" style={{ color: '#a78bfa' }}>₹899</div>
          <div className="plan-period">per month</div>
          <ul className="plan-features">
            {MAX_FEATURES.map(feature => (
              <li key={feature} className="plan-feature"><span className="feat-icon" style={{ color: '#a78bfa' }}>✓</span> {feature}</li>
            ))}
          </ul>
          <button
            className="btn-gradient"
            style={{ width: '100%', background: 'linear-gradient(to right, #6d28d9, #4c1d95)', opacity: paymentConfigured ? 1 : 0.6 }}
            onClick={() => { void handleSubscribe(89900, 'MAX', 'Max') }}
            disabled={loading || isMax || !paymentConfigured || !canManageBilling}
          >
            {loading
              ? '⏳ Opening payment…'
              : isMax
                ? '✅ Current Plan'
                : !canManageBilling
                  ? 'Owner manages billing'
                : paymentConfigured
                  ? (isActive ? '⬆️ Upgrade to Max — ₹899/mo' : '💳 Subscribe — ₹899/mo')
                  : 'Payments unavailable'}
          </button>
        </div>
      </div>
    </div>
  )
}
