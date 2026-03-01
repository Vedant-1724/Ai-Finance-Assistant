// CHANGES:
//  - Shows 3 tiers: Free (current/permanent) / Trial (5-day) / Pro (₹499/mo)
//  - Start Trial button for FREE users
//  - Razorpay checkout for Pro

import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import axios from 'axios'
import { useAuth } from '../context/AuthContext'

declare global {
  interface Window {
    Razorpay: any
  }
}

interface SubStatus {
  tier:             string
  status:           string
  trialDaysRemaining: number
  aiChatsRemaining: number
  trialAlreadyUsed: boolean
}

const FREE_FEATURES    = ['10 transactions visible', '3 AI chats / day', 'Basic dashboard', 'Add up to 20 transactions']
const FREE_LOCKED      = ['Cash flow forecast', 'Anomaly detection', 'Invoice OCR', 'P&L reports', 'Advanced charts']
const TRIAL_FEATURES   = ['All premium features for 5 days', '10 AI chats / day', 'Cash flow forecast (30 days)', 'Anomaly detection', 'Invoice OCR parsing', 'Full P&L reports', 'Unlimited transactions']
const PRO_FEATURES     = ['Everything in Trial', '50 AI chats / day', 'Priority support', 'Export to PDF/CSV (soon)', 'Email alerts (soon)', 'Bank sync via Plaid (soon)']

export default function SubscriptionPage() {
  const { user, updateSubscription } = useAuth()
  const navigate    = useNavigate()
  const [subStatus, setSubStatus] = useState<SubStatus | null>(null)
  const [loading, setLoading]     = useState(false)
  const [trialLoading, setTrialLoading] = useState(false)
  const [error, setError]         = useState<string | null>(null)
  const [msg, setMsg]             = useState<string | null>(null)

  useEffect(() => {
    if (!user) return
    axios.get('/api/v1/subscription/status', {
      headers: { Authorization: `Bearer ${user.token}` }
    }).then(r => setSubStatus(r.data)).catch(() => {})
  }, [user])

  const handleStartTrial = async () => {
    if (!user) return
    setTrialLoading(true)
    setError(null)
    try {
      const res = await axios.post('/api/v1/subscription/start-trial', {}, {
        headers: { Authorization: `Bearer ${user.token}` }
      })
      const data = res.data as { tier: string; trialDaysRemaining: number; aiChatsRemaining: number }
      updateSubscription(data.tier, data.trialDaysRemaining, data.aiChatsRemaining)
      setMsg('🎉 Your 5-day free trial has started! Enjoy all premium features.')
      setSubStatus(prev => prev ? { ...prev, tier: 'TRIAL', trialDaysRemaining: 5, trialAlreadyUsed: true } : null)
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

  const handleSubscribe = async () => {
    if (!user) return
    setLoading(true)
    setError(null)
    try {
      const orderRes = await axios.post(
        '/api/v1/payment/create-order',
        { amount: 49900, currency: 'INR', receipt: `receipt_${Date.now()}` },
        { headers: { Authorization: `Bearer ${user.token}` } }
      )
      const order = orderRes.data as any
      const options = {
        key:          order.keyId,
        amount:       order.amount,
        currency:     order.currency,
        name:         'FinanceAI',
        description:  'Pro Subscription — ₹499/month',
        order_id:     order.id,
        prefill:      { email: user.email },
        theme:        { color: '#3b82f6' },
        handler: async (res: any) => {
          await axios.post('/api/v1/payment/verify', res, {
            headers: { Authorization: `Bearer ${user.token}` }
          })
          updateSubscription('ACTIVE', 0, 50)
          setMsg('🎉 Welcome to FinanceAI Pro! All features are now unlocked.')
          navigate('/')
        }
      }
      if (!window.Razorpay) {
        const script = document.createElement('script')
        script.src = 'https://checkout.razorpay.com/v1/checkout.js'
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
  const isActive    = currentTier === 'ACTIVE'
  const isTrial     = currentTier === 'TRIAL'
  const isFree      = !isActive && !isTrial

  return (
    <div style={{ maxWidth: 960, margin: '0 auto', padding: '32px 20px' }}>

      {/* Header */}
      <div style={{ textAlign: 'center', marginBottom: 40 }}>
        <button
          onClick={() => navigate('/')}
          style={{ background: 'none', border: 'none', color: 'var(--text-muted)', cursor: 'pointer', fontSize: 13, marginBottom: 20, display: 'flex', alignItems: 'center', gap: 6, margin: '0 auto 20px' }}
        >
          ← Back to dashboard
        </button>
        <h1 className="page-title" style={{ fontSize: 28, marginBottom: 8 }}>Choose your plan</h1>
        <p style={{ color: 'var(--text-secondary)', fontSize: 15 }}>
          {isActive ? '✅ You are on the Pro plan — thank you for subscribing!'
          : isTrial ? `⏳ Free trial active — ${subStatus?.trialDaysRemaining ?? 0} day(s) remaining`
          : 'Start free, upgrade when you need more power'}
        </p>
        {msg   && <div className="success-box" style={{ marginTop: 16, textAlign: 'left', maxWidth: 500, margin: '16px auto 0' }}>{msg}</div>}
        {error && <div className="error-box"   style={{ marginTop: 16, textAlign: 'left', maxWidth: 500, margin: '16px auto 0' }}>⚠️ {error}</div>}
      </div>

      <div className="plans-grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(260px,1fr))' }}>

        {/* Free plan */}
        <div className={`plan-card ${isFree ? 'featured' : ''}`}>
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
        <div className={`plan-card ${isTrial ? 'featured' : ''}`}>
          {isTrial && <div className="plan-badge">Active Trial</div>}
          {!isTrial && !subStatus?.trialAlreadyUsed && <div className="plan-badge" style={{ background: 'rgba(245,158,11,0.9)' }}>Try Free</div>}
          <div className="plan-name" style={{ color: 'var(--amber)' }}>Premium Trial</div>
          <div className="plan-price" style={{ color: 'var(--amber)' }}>₹0</div>
          <div className="plan-period">for 5 days · one time</div>
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
        <div className={`plan-card ${isActive ? 'featured' : ''}`} style={{ borderColor: isActive ? 'rgba(59,130,246,0.5)' : undefined }}>
          {isActive && <div className="plan-badge">Current Plan</div>}
          {!isActive && <div className="plan-badge">⭐ Recommended</div>}
          <div className="plan-name" style={{ color: 'var(--blue)' }}>Pro</div>
          <div className="plan-price" style={{ color: 'var(--blue)' }}>₹499</div>
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
            onClick={handleSubscribe}
            disabled={loading || isActive}
          >
            {loading  ? '⏳ Opening payment…' : isActive ? '✅ Subscribed' : '💳 Subscribe — ₹499/mo'}
          </button>
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
          ['Can I use the trial more than once?', 'No. Each account gets one 5-day trial. After it ends, you can upgrade to Pro.'],
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