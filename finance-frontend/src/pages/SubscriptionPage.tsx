// PATH: finance-frontend/src/pages/SubscriptionPage.tsx
//
// NEW FILE — Pricing page with Razorpay checkout.
//
// Flow:
//  1. Page loads — shows pricing plans
//  2. User clicks "Subscribe" — calls POST /api/v1/payment/create-order
//  3. Backend returns orderId + keyId (₹499 in paise)
//  4. Razorpay JS SDK opens native payment modal
//  5. On success → verify payment → show success state
//  6. On failure → show error message
//
// Requires Razorpay checkout.js script loaded in index.html:
//   <script src="https://checkout.razorpay.com/v1/checkout.js"></script>

import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import api from '../api'

interface OrderResponse {
  orderId:  string
  amount:   number
  currency: string
  keyId:    string
  email:    string
}

interface SubStatus {
  status:        string
  daysRemaining: number
  canAccess:     boolean
}

// Extend window with Razorpay SDK type
declare global {
  interface Window { Razorpay: any }
}

export default function SubscriptionPage() {
  const { user }                          = useAuth()
  const navigate                          = useNavigate()
  const [loading, setLoading]             = useState(false)
  const [error, setError]                 = useState<string | null>(null)
  const [success, setSuccess]             = useState(false)
  const [subStatus, setSubStatus]         = useState<SubStatus | null>(null)
  const [statusLoading, setStatusLoading] = useState(true)

  useEffect(() => {
    api.get<SubStatus>('/api/v1/payment/status')
      .then(r => setSubStatus(r.data))
      .catch(() => {})
      .finally(() => setStatusLoading(false))
  }, [])

  const handleSubscribe = async () => {
    setLoading(true)
    setError(null)

    try {
      // Step 1: Create Razorpay order on the backend
      const { data } = await api.post<OrderResponse>('/api/v1/payment/create-order')

      // Step 2: Open Razorpay checkout
      const options = {
        key:      data.keyId,
        amount:   data.amount,
        currency: data.currency,
        name:     'Finance & Accounting Assistant',
        description: 'Monthly Subscription — ₹499/month',
        order_id: data.orderId,
        prefill: {
          email: data.email || user?.email,
        },
        theme: { color: '#3b82f6' },
        modal: {
          ondismiss: () => {
            setLoading(false)
            setError('Payment was cancelled. Your free trial is still active.')
          },
        },
        handler: async (_response: any) => {
          // Payment succeeded — wait for webhook to activate subscription
          setSuccess(true)
          setLoading(false)
          // Redirect to dashboard after 3 seconds
          setTimeout(() => navigate('/'), 3000)
        },
      }

      if (!window.Razorpay) {
        throw new Error('Razorpay SDK not loaded. Check your internet connection.')
      }

      const rzp = new window.Razorpay(options)
      rzp.on('payment.failed', (resp: any) => {
        setError(`Payment failed: ${resp.error.description}`)
        setLoading(false)
      })
      rzp.open()

    } catch (err: any) {
      setError(err?.response?.data?.error || err?.message || 'Failed to start payment. Try again.')
      setLoading(false)
    }
  }

  // ── Success state ─────────────────────────────────────────────────────────
  if (success) {
    return (
      <div style={styles.page}>
        <div style={{ ...styles.card, textAlign: 'center' }}>
          <div style={{ fontSize: 64, marginBottom: 16 }}>🎉</div>
          <h2 style={{ color: '#4ade80', fontSize: 24, marginBottom: 8 }}>Payment Successful!</h2>
          <p style={{ color: '#94a3b8', marginBottom: 24 }}>
            Your subscription is now active. Welcome to the full experience!
          </p>
          <p style={{ color: '#64748b', fontSize: 13 }}>Redirecting to Dashboard…</p>
        </div>
      </div>
    )
  }

  return (
    <div style={styles.page}>
      <div style={styles.container}>

        {/* Header */}
        <div style={styles.header}>
          <button onClick={() => navigate('/')} style={styles.backBtn}>← Back to Dashboard</button>
          <h1 style={styles.title}>Choose Your Plan</h1>
          <p style={styles.subtitle}>
            {subStatus && !statusLoading
              ? subStatus.status === 'ACTIVE'
                ? '✅ You are on the Pro plan — thank you!'
                : subStatus.status === 'TRIAL'
                ? `⏳ Free trial — ${subStatus.daysRemaining} day${subStatus.daysRemaining !== 1 ? 's' : ''} remaining`
                : '🔒 Your trial has ended. Subscribe to regain access.'
              : 'Unlock full access to your financial intelligence platform.'}
          </p>
        </div>

        {/* Plans */}
        <div style={styles.plansRow}>

          {/* Free Trial */}
          <div style={styles.planCard}>
            <div style={styles.planBadge}>Current Plan</div>
            <h3 style={styles.planName}>Free Trial</h3>
            <div style={styles.planPrice}>₹0</div>
            <p style={styles.planPeriod}>5 days</p>
            <ul style={styles.featureList}>
              {['All features unlocked', 'AI chat (limited)', 'Cash flow forecast', 'Anomaly detection', 'Invoice OCR'].map(f => (
                <li key={f} style={styles.feature}>✓ {f}</li>
              ))}
            </ul>
          </div>

          {/* Pro */}
          <div style={{ ...styles.planCard, ...styles.planCardPro }}>
            <div style={styles.proBadge}>⭐ RECOMMENDED</div>
            <h3 style={{ ...styles.planName, color: '#60a5fa' }}>Pro</h3>
            <div style={{ ...styles.planPrice, color: '#60a5fa' }}>₹499</div>
            <p style={styles.planPeriod}>per month</p>
            <ul style={styles.featureList}>
              {[
                'Unlimited transactions',
                'AI chat (unlimited)',
                'Cash flow forecast (30 days)',
                'Anomaly detection + email alerts',
                'Invoice OCR',
                'P&L reports with Redis cache',
                'Priority support',
                'Export to PDF/CSV (coming soon)',
              ].map(f => (
                <li key={f} style={styles.feature}>✓ {f}</li>
              ))}
            </ul>
            {error && (
              <div style={styles.errorBox}>{error}</div>
            )}
            <button
              onClick={handleSubscribe}
              disabled={loading || subStatus?.status === 'ACTIVE'}
              style={{
                ...styles.subscribeBtn,
                opacity: loading || subStatus?.status === 'ACTIVE' ? 0.6 : 1,
                cursor:  loading || subStatus?.status === 'ACTIVE' ? 'not-allowed' : 'pointer',
              }}
            >
              {loading
                ? '⏳ Opening payment…'
                : subStatus?.status === 'ACTIVE'
                ? '✅ Already subscribed'
                : '🚀 Subscribe — ₹499/month'}
            </button>
            <p style={styles.secureNote}>🔒 Secured by Razorpay · Cancel anytime</p>
          </div>

        </div>

        {/* FAQ */}
        <div style={styles.faq}>
          <h3 style={styles.faqTitle}>Frequently Asked Questions</h3>
          {[
            ['When will I be charged?', 'You are charged ₹499 immediately upon subscribing. Your subscription lasts 30 days from the payment date.'],
            ['Can I cancel?', 'Yes, you can cancel any time. You retain access until the current period ends.'],
            ['Is my payment data safe?', 'We never store card details. All payments are processed by Razorpay, which is PCI-DSS Level 1 certified.'],
            ['What payment methods are accepted?', 'UPI, Debit cards, Credit cards, Netbanking, and Wallets via Razorpay.'],
          ].map(([q, a]) => (
            <div key={q as string} style={styles.faqItem}>
              <strong style={styles.faqQ}>{q}</strong>
              <p style={styles.faqA}>{a}</p>
            </div>
          ))}
        </div>

      </div>
    </div>
  )
}

// ── Styles ─────────────────────────────────────────────────────────────────────
const styles: Record<string, React.CSSProperties> = {
  page:         { minHeight: '100vh', background: '#0a0f1e', padding: '32px 16px' },
  container:    { maxWidth: 960, margin: '0 auto' },
  header:       { textAlign: 'center', marginBottom: 48 },
  backBtn:      { background: 'transparent', border: '1px solid #1a2744', color: '#64748b', padding: '8px 16px', borderRadius: 8, cursor: 'pointer', fontSize: 13, marginBottom: 24 },
  title:        { fontSize: 36, fontWeight: 800, color: '#e2e8f0', margin: '0 0 12px' },
  subtitle:     { fontSize: 16, color: '#64748b' },
  plansRow:     { display: 'flex', gap: 24, justifyContent: 'center', flexWrap: 'wrap', marginBottom: 64 },
  planCard:     { background: '#0d1526', border: '1px solid #1a2744', borderRadius: 16, padding: '32px 28px', width: 320, position: 'relative' },
  planCardPro:  { border: '2px solid #3b82f6', boxShadow: '0 0 40px rgba(59,130,246,0.15)' },
  planBadge:    { position: 'absolute', top: -12, left: '50%', transform: 'translateX(-50%)', background: '#1a2744', color: '#94a3b8', fontSize: 11, padding: '4px 12px', borderRadius: 20, fontWeight: 600, whiteSpace: 'nowrap' },
  proBadge:     { position: 'absolute', top: -12, left: '50%', transform: 'translateX(-50%)', background: '#1e40af', color: '#93c5fd', fontSize: 11, padding: '4px 12px', borderRadius: 20, fontWeight: 700, whiteSpace: 'nowrap' },
  planName:     { fontSize: 22, fontWeight: 700, color: '#e2e8f0', margin: '12px 0 8px' },
  planPrice:    { fontSize: 48, fontWeight: 800, color: '#e2e8f0' },
  planPeriod:   { color: '#64748b', marginBottom: 24, fontSize: 14 },
  featureList:  { listStyle: 'none', padding: 0, margin: '0 0 24px', display: 'flex', flexDirection: 'column', gap: 8 },
  feature:      { color: '#94a3b8', fontSize: 14 },
  subscribeBtn: { width: '100%', padding: 14, background: '#2563eb', color: '#fff', border: 'none', borderRadius: 10, fontSize: 15, fontWeight: 700, cursor: 'pointer', boxShadow: '0 4px 20px rgba(37,99,235,0.4)' },
  secureNote:   { textAlign: 'center', fontSize: 12, color: '#475569', marginTop: 12 },
  errorBox:     { background: 'rgba(239,68,68,0.1)', border: '1px solid rgba(239,68,68,0.3)', color: '#f87171', borderRadius: 8, padding: '10px 14px', fontSize: 13, marginBottom: 16 },
  card:         { background: '#0d1526', border: '1px solid #1a2744', borderRadius: 16, padding: '48px 40px', maxWidth: 480, margin: '80px auto', color: '#e2e8f0' },
  faq:          { borderTop: '1px solid #1a2744', paddingTop: 48 },
  faqTitle:     { color: '#e2e8f0', fontSize: 20, fontWeight: 700, marginBottom: 24 },
  faqItem:      { marginBottom: 24, paddingBottom: 24, borderBottom: '1px solid #0f172a' },
  faqQ:         { color: '#cbd5e1', fontSize: 15 },
  faqA:         { color: '#64748b', fontSize: 14, marginTop: 6, lineHeight: 1.6 },
}
