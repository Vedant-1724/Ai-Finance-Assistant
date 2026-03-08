// CHANGES:
//  - Shows 3 tiers: Free (current/permanent) / Trial (5-day) / Pro (₹499/mo)
//  - Start Trial button for FREE users
//  - Razorpay checkout for Pro

import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import api from '../api'
import { useAuth } from '../context/AuthContext'

declare global {
  interface Window {
    Razorpay: any
  }
}

interface SubStatus {
  tier: string
  status: string
  trialDaysRemaining: number
  aiChatsRemaining: number
  trialAlreadyUsed: boolean
}

const FREE_FEATURES = ['Up to 50 transactions stored', 'View latest 20 transactions', 'Basic dashboard (income / expense / net)', '1 statement import per month', 'Up to 3 budget entries per month', 'Auto-category tagging']
const FREE_LOCKED = ['Advanced charts', 'Cash flow forecast', 'Anomaly detection', 'Invoice OCR', 'P&L reports', 'Tax & GST tools', 'AI Chat Assistant']
const TRIAL_FEATURES = ['All premium features for 3 days', 'Unlimited transactions', 'Full charts & analytics', 'Cash flow forecast (30 days)', 'Anomaly detection', 'Invoice OCR (up to 10)', 'P&L reports · Tax & GST · Health Score', 'Audit log · Team (up to 2 members)', 'No AI chat (upgrade to Pro for AI)']
const PRO_FEATURES = ['Up to 1,000 transactions', 'AI Chat — 20 queries / day', 'All charts, forecasts & anomaly detection', 'Invoice OCR — 30 / month', 'Full P&L · Tax & GST · Health Score', 'Audit log — last 90 days', 'Team — up to 3 members', 'Email alerts (anomaly, budget, forecast)', 'Recurring transactions']
const MAX_FEATURES = ['Everything in Pro', 'Unlimited transactions & imports', 'AI Chat — 50 queries / day', 'Invoice OCR — unlimited', 'Audit log — full history', 'Team — up to 10 members', 'Weekly P&L digest email', 'Extended 60-day forecast', 'Export PDF / CSV (when shipped)', 'Bank sync via Plaid/Setu (when shipped)', 'Priority support']

export default function SubscriptionPage() {
  const { user, updateSubscription } = useAuth()
  const navigate = useNavigate()
  const [subStatus, setSubStatus] = useState<SubStatus | null>(null)
  const [loading, setLoading] = useState(false)
  const [trialLoading, setTrialLoading] = useState(false)
  const [cancelLoading, setCancelLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [msg, setMsg] = useState<string | null>(null)

  useEffect(() => {
    if (!user) return
    api.get('/api/v1/subscription/status')
      .then(r => setSubStatus(r.data)).catch(() => { })
  }, [user])

  const handleStartTrial = async () => {
    if (!user) return
    setTrialLoading(true)
    setError(null)
    try {
      const res = await api.post('/api/v1/subscription/start-trial')
      const data = res.data as { tier: string; trialDaysRemaining: number; aiChatsRemaining: number }
      updateSubscription(data.tier, data.trialDaysRemaining, data.aiChatsRemaining)
      setMsg('🎉 Your 3-day free trial has started! Enjoy all premium features.')
      setSubStatus(prev => prev ? { ...prev, tier: 'TRIAL', trialDaysRemaining: 3, trialAlreadyUsed: true } : null)
    } catch (err: any) {
      const errCode = err?.response?.data?.error
      if (errCode === 'TRIAL_ALREADY_USED') {
        setError('Your free trial has already been used. Please subscribe to Pro to continue.')
      } else {
        setError('Failed to start trial. Please try again.')
      }
    } finally {
      setTrialLoading(false)
    }
  }

  const handleCancelSubscription = async () => {
    if (!user) return
    if (!window.confirm('Are you sure you want to cancel your subscription? You will be moved to the Free tier.')) return
    setCancelLoading(true)
    setError(null)
    try {
      await api.post('/api/v1/subscription/cancel')
      updateSubscription('FREE', 0, 3)
      setMsg('Your subscription has been cancelled. You are now on the Free tier.')
      setSubStatus(prev => prev ? { ...prev, tier: 'FREE', status: 'CANCELLED' } : null)
    } catch {
      setError('Failed to cancel subscription. Please try again.')
    } finally {
      setCancelLoading(false)
    }
  }

  const handleSubscribe = async (amount: number, planName: string) => {
    if (!user) return
    setLoading(true)
    setError(null)
    try {
      const orderRes = await api.post(
        '/api/v1/payment/create-order',
        { amount, currency: 'INR', receipt: `receipt_${Date.now()}` }
      )
      const order = orderRes.data as any
      const options = {
        key: order.keyId,
        amount: order.amount,
        currency: order.currency,
        name: 'FinanceAI',
        description: `${planName} Subscription — ₹${amount / 100}/month`,
        order_id: order.id,
        prefill: { email: user.email },
        theme: { color: planName === 'Max' ? '#8b5cf6' : '#3b82f6' },
        handler: async (res: any) => {
          const tier = planName === 'Max' ? 'MAX' : 'ACTIVE'
          await api.post('/api/v1/payment/verify', { ...res, plan: tier })
          updateSubscription(tier, 0, planName === 'Max' ? 50 : 20)
          setMsg(`🎉 Welcome to FinanceAI ${planName}! All features are now unlocked.`)
          navigate('/')
        }
      }
      if (!window.Razorpay) {
        const script = document.createElement('script')
        script.src = 'https://checkout.razorpay.com/v1/checkout.js'
        script.crossOrigin = 'anonymous'
        script.integrity = 'sha384-4NCTYeIch5r4uow7dwh6FYqbRlRFCPmldYDt6tK5365IBV+MCwUAVXq1I8EYefZT'
        script.onload = () => new window.Razorpay(options).open()
        document.body.appendChild(script)
      } else {
        new window.Razorpay(options).open()
      }
    } catch {
      setError('Failed to open payment. Please try again.')
    } finally {
      setLoading(false)
    }
  }

  const currentTier = subStatus?.tier ?? user?.subscriptionTier ?? 'FREE'
  const isActive = currentTier === 'ACTIVE'
  const isTrial = currentTier === 'TRIAL'
  const isMax = currentTier === 'MAX'
  const isFree = !isActive && !isTrial && !isMax

  return (
    <div style={{ maxWidth: 960, margin: '0 auto', padding: '32px 20px', position: 'relative' }}>
      {/* Close Button */}
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
          lineHeight: 1
        }}
      >
        ✕
      </button>

      {/* Header */}
      <div style={{ textAlign: 'center', marginBottom: 40 }}>
        <h1 className="page-title" style={{ fontSize: 28, marginBottom: 8 }}>Choose your plan</h1>
        <p style={{ color: 'var(--text-secondary)', fontSize: 15 }}>
          {isMax ? '👑 You are on the Max plan — ultimate power unlocked!'
            : isActive ? '✅ You are on the Pro plan — thank you for subscribing!'
              : isTrial ? `⏳ Free trial active — ${subStatus?.trialDaysRemaining ?? 0} day(s) remaining`
                : '🚀 Start 3-Day Trial, upgrade when you need more power'}
        </p>
        {msg && <div className="success-box" style={{ marginTop: 16, textAlign: 'left', maxWidth: 500, margin: '16px auto 0' }}>{msg}</div>}
        {error && <div className="error-box" style={{ marginTop: 16, textAlign: 'left', maxWidth: 500, margin: '16px auto 0' }}>⚠️ {error}</div>}
      </div>

      <div className="plans-grid" style={{ display: 'flex', flexDirection: 'row', justifyContent: 'center', gap: '24px', flexWrap: 'nowrap', overflowX: 'auto', paddingBottom: '16px' }}>

        {/* Free plan */}
        <div className={`plan-card ${isFree ? 'featured' : ''}`} style={{ flex: 1, minWidth: '260px' }}>
          {isFree && <div className="plan-badge">Current Plan</div>}
          <div className="plan-name">Free</div>
          <div className="plan-price">₹0</div>
          <div className="plan-period">forever</div>
          <ul className="plan-features">
            {FREE_FEATURES.map(f => (
              <li key={f} className="plan-feature">
                <span className="feat-icon">✓</span> {f}
              </li>
            ))}
            {FREE_LOCKED.map(f => (
              <li key={f} className="plan-feature feat-locked">
                <span className="feat-icon">🔒</span> {f}
              </li>
            ))}
          </ul>
          <button className="btn-secondary" style={{ width: '100%' }} disabled>
            {isFree ? 'Current Plan' : 'Downgrade'}
          </button>
        </div>

        {/* Trial plan */}
        <div className={`plan-card ${isTrial ? 'featured' : ''}`} style={{ flex: 1, minWidth: '260px' }}>
          {isTrial && <div className="plan-badge">Active Trial</div>}
          {!isTrial && !subStatus?.trialAlreadyUsed && <div className="plan-badge" style={{ background: 'rgba(245,158,11,0.9)' }}>Try Free</div>}
          <div className="plan-name" style={{ color: 'var(--amber)' }}>Premium Trial</div>
          <div className="plan-price" style={{ color: 'var(--amber)' }}>₹0</div>
          <div className="plan-period">for 3 days · one time</div>
          <ul className="plan-features">
            {TRIAL_FEATURES.map(f => (
              <li key={f} className="plan-feature">
                <span className="feat-icon" style={{ color: 'var(--amber)' }}>✓</span> {f}
              </li>
            ))}
          </ul>
          {isTrial ? (
            <button className="btn-secondary" style={{ width: '100%' }} disabled>
              Trial Active · {subStatus?.trialDaysRemaining}d left
            </button>
          ) : subStatus?.trialAlreadyUsed ? (
            <button className="btn-secondary" style={{ width: '100%' }} disabled>
              Trial Already Used
            </button>
          ) : (
            <button
              className="btn-gradient"
              style={{ width: '100%' }}
              onClick={handleStartTrial}
              disabled={trialLoading || isActive}
            >
              {trialLoading ? '⏳ Starting…' : '🚀 Start 5-Day Trial'}
            </button>
          )}
        </div>

        {/* Pro plan */}
        <div className={`plan-card ${isActive ? 'featured' : ''}`} style={{ flex: 1, minWidth: '260px', borderColor: isActive ? 'rgba(59,130,246,0.5)' : undefined }}>
          {isActive && <div className="plan-badge">Current Plan</div>}
          {!isActive && <div className="plan-badge">⭐ Recommended</div>}
          <div className="plan-name" style={{ color: 'var(--blue)' }}>Pro</div>
          <div className="plan-price" style={{ color: 'var(--blue)' }}>₹399</div>
          <div className="plan-period">per month · cancel anytime</div>
          <ul className="plan-features">
            {PRO_FEATURES.map(f => (
              <li key={f} className="plan-feature">
                <span className="feat-icon" style={{ color: 'var(--blue)' }}>✓</span> {f}
              </li>
            ))}
          </ul>
          <button
            className="btn-gradient"
            style={{ width: '100%' }}
            onClick={() => handleSubscribe(39900, 'Pro')}
            disabled={loading || isActive || isMax}
          >
            {loading ? '⏳ Opening payment…' : isActive ? '✅ Subscribed' : '💳 Subscribe — ₹399/mo'}
          </button>
          {isActive && (
            <button
              onClick={handleCancelSubscription}
              disabled={cancelLoading}
              style={{
                width: '100%', marginTop: 8, padding: '10px', borderRadius: 8,
                background: 'rgba(239,68,68,0.08)', border: '1px solid rgba(239,68,68,0.2)',
                color: '#f87171', cursor: 'pointer', fontSize: 13, fontWeight: 500,
                transition: 'all 0.15s',
              }}
            >
              {cancelLoading ? '⏳ Cancelling…' : '✕ Cancel Subscription'}
            </button>
          )}
          <p style={{ textAlign: 'center', fontSize: 11, color: 'var(--text-muted)', marginTop: 10 }}>
            🔒 Secured by Razorpay · No hidden charges
          </p>
        </div>

        {/* Max plan */}
        <div className={`plan-card ${isMax ? 'featured' : ''}`} style={{ flex: 1, minWidth: '260px', borderColor: isMax ? 'rgba(139,92,246,0.5)' : undefined }}>
          {isMax && <div className="plan-badge" style={{ background: 'var(--purple-dark)', color: '#c4b5fd' }}>Current Plan</div>}
          {!isMax && <div className="plan-badge" style={{ background: '#4c1d95', color: '#c4b5fd' }}>👑 Ultimate</div>}
          <div className="plan-name" style={{ color: '#a78bfa' }}>Max</div>
          <div className="plan-price" style={{ color: '#a78bfa' }}>₹899</div>
          <div className="plan-period">per month · cancel anytime</div>
          <ul className="plan-features">
            {MAX_FEATURES.map(f => (
              <li key={f} className="plan-feature">
                <span className="feat-icon" style={{ color: '#a78bfa' }}>✓</span> {f}
              </li>
            ))}
          </ul>
          <button
            className="btn-gradient"
            style={{ width: '100%', background: 'linear-gradient(to right, #6d28d9, #4c1d95)' }}
            onClick={() => handleSubscribe(89900, 'Max')}
            disabled={loading || isMax}
          >
            {loading ? '⏳ Opening payment…' : isMax ? '✅ Subscribed' : '💳 Subscribe — ₹899/mo'}
          </button>
          {(isMax || isTrial) && (
            <button
              onClick={handleCancelSubscription}
              disabled={cancelLoading}
              style={{
                width: '100%', marginTop: 8, padding: '10px', borderRadius: 8,
                background: 'rgba(239,68,68,0.08)', border: '1px solid rgba(239,68,68,0.2)',
                color: '#f87171', cursor: 'pointer', fontSize: 13, fontWeight: 500,
                transition: 'all 0.15s',
              }}
            >
              {cancelLoading ? '⏳ Cancelling…' : '✕ Cancel Subscription'}
            </button>
          )}
          <p style={{ textAlign: 'center', fontSize: 11, color: 'var(--text-muted)', marginTop: 10 }}>
            🔒 Secured by Razorpay · No hidden charges
          </p>
        </div>

      </div>

      {/* FAQ */}
      <div style={{ maxWidth: 600, margin: '48px auto 0' }}>
        <h3 style={{ fontSize: 16, fontWeight: 600, color: 'var(--text-primary)', marginBottom: 16 }}>FAQ</h3>
        {[
          ['Does the free plan expire?', 'No. The free plan is permanent with no time limit. You keep basic features forever.'],
          ['Can I use the trial more than once?', 'No. Each account gets one 3-day trial. After it ends, you can upgrade to Pro or Max.'],
          ['When will I be charged for Pro?', 'Only when you click Subscribe and complete payment. Trial is always free.'],
          ['What happens after trial ends?', 'Your account returns to the free tier. No charge, no auto-billing.'],
          ['Are my AI chats secure?', 'Yes. All chat goes through our backend which validates your identity before forwarding to the AI service. Prompt injection is filtered server-side.'],
        ].map(([q, a]) => (
          <details key={q} style={{ borderBottom: '1px solid var(--border)', paddingBottom: 12, marginBottom: 12 }}>
            <summary style={{ cursor: 'pointer', fontSize: 14, fontWeight: 500, color: 'var(--text-primary)', padding: '8px 0', listStyle: 'none' }}>
              ▸ {q}
            </summary>
            <p style={{ fontSize: 13, color: 'var(--text-secondary)', marginTop: 8, lineHeight: 1.6 }}>{a}</p>
          </details>
        ))}
      </div>
    </div>
  )
}