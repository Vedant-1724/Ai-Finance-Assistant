// PATH: finance-frontend/src/pages/HealthScorePage.tsx
// Financial Health Score page with animated gauge, breakdown bars, and AI recommendations.
// Calls GET /api/v1/{companyId}/health-score?month=YYYY-MM

import { useEffect, useState, useCallback } from 'react'
import api from '../api'
import { useAuth } from '../context/AuthContext'

interface BreakdownItem {
  label: string
  score: number
  weight: number
  detail: string
}

interface HealthScore {
  score: number   // 0–100
  grade: string   // A, B, C, D, F
  month: string   // YYYY-MM
  breakdown: BreakdownItem[]
  recommendations: string   // AI-generated bullet points
  previousScore: number | null
  change: number | null
}

const MONTHS: { value: string; label: string }[] = (() => {
  const arr = []
  const now = new Date()
  for (let i = 0; i < 6; i++) {
    const d = new Date(now.getFullYear(), now.getMonth() - i, 1)
    arr.push({
      value: d.toISOString().slice(0, 7),
      label: d.toLocaleDateString('en-IN', { month: 'long', year: 'numeric' }),
    })
  }
  return arr
})()

function gradeColor(grade: string) {
  return { A: '#22c55e', B: '#84cc16', C: '#f59e0b', D: '#f97316', F: '#ef4444' }[grade] ?? '#94a3b8'
}
function scoreColor(score: number) {
  if (score >= 80) return '#22c55e'
  if (score >= 60) return '#84cc16'
  if (score >= 40) return '#f59e0b'
  if (score >= 20) return '#f97316'
  return '#ef4444'
}

export default function HealthScorePage({ companyId }: { companyId: number }) {
  const { isFree } = useAuth()
  const [month, setMonth] = useState(MONTHS[0].value)
  const [data, setData] = useState<HealthScore | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true); setError(null)
    try {
      const res = await api.get<HealthScore>(
        `/api/v1/${companyId}/health/score?month=${month}`
      )
      const raw = res.data
      setData(raw ? { ...raw, breakdown: Array.isArray(raw.breakdown) ? raw.breakdown : [] } : null)
    } catch (e: unknown) {
      const status = (e as { response?: { status?: number } })?.response?.status
      if (status === 402) setError('UPGRADE_REQUIRED')
      else setError('Failed to load health score.')
    } finally { setLoading(false) }
  }, [companyId, month])

  useEffect(() => { void load() }, [load])

  if (isFree) return (
    <div className="upgrade-gate">
      <div style={{ fontSize: 56 }}>💚</div>
      <h2>Health Score requires Trial or Pro</h2>
      <p>Get an AI-powered monthly financial health score with actionable recommendations.</p>
      <a href="/subscription" className="btn-primary">Upgrade Now →</a>
    </div>
  )

  if (loading) return <div className="loading">⏳ Computing financial health score…</div>
  if (error === 'UPGRADE_REQUIRED') return (
    <div className="upgrade-gate">
      <div style={{ fontSize: 56 }}>💚</div>
      <h2>Health Score requires Trial or Pro</h2>
      <a href="/subscription" className="btn-primary">Upgrade Now →</a>
    </div>
  )
  if (error) return <div className="error">❌ {error}</div>

  return (
    <div className="health-page">
      {/* Header */}
      <div className="page-header">
        <h1 className="page-title">💚 Financial Health Score</h1>
        <select
          className="select-sm"
          value={month}
          onChange={e => setMonth(e.target.value)}
        >
          {MONTHS.map(m => <option key={m.value} value={m.value}>{m.label}</option>)}
        </select>
      </div>

      {data && (
        <>
          <div className="health-main">
            {/* ── Gauge card ───────────────────────────────────────────────── */}
            <div className="health-gauge-card">
              {/* Animated circular gauge */}
              <div style={{ position: 'relative', width: 160, height: 160, marginBottom: 16 }}>
                <svg width="160" height="160" viewBox="0 0 160 160">
                  {/* Background circle */}
                  <circle cx="80" cy="80" r="66" fill="none"
                    stroke="rgba(255,255,255,0.06)" strokeWidth="12" />
                  {/* Score arc */}
                  <circle cx="80" cy="80" r="66" fill="none"
                    stroke={scoreColor(data.score)} strokeWidth="12"
                    strokeLinecap="round"
                    strokeDasharray={`${(data.score / 100) * 415} 415`}
                    strokeDashoffset="103"   /* starts from top */
                    transform="rotate(-90 80 80)"
                    style={{ transition: 'stroke-dasharray 1s ease' }}
                  />
                </svg>
                {/* Score text overlay */}
                <div style={{
                  position: 'absolute', inset: 0,
                  display: 'flex', flexDirection: 'column',
                  alignItems: 'center', justifyContent: 'center'
                }}>
                  <span style={{ fontSize: 36, fontWeight: 800, color: scoreColor(data.score), lineHeight: 1 }}>
                    {data.score}
                  </span>
                  <span style={{ fontSize: 11, color: '#94a3b8', marginTop: 2 }}>out of 100</span>
                </div>
              </div>

              {/* Grade badge */}
              <div style={{
                width: 56, height: 56, borderRadius: '50%',
                background: `${gradeColor(data.grade)}22`,
                border: `2px solid ${gradeColor(data.grade)}`,
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                fontSize: 22, fontWeight: 800, color: gradeColor(data.grade),
                marginBottom: 8
              }}>
                {data.grade}
              </div>

              <span className="health-score-label">
                {MONTHS.find(m => m.value === data.month)?.label ?? data.month}
              </span>

              {/* Change from previous month */}
              {data.change !== null && (
                <div style={{
                  marginTop: 10, fontSize: 13, fontWeight: 600,
                  color: data.change >= 0 ? '#4ade80' : '#f87171',
                  display: 'flex', alignItems: 'center', gap: 4
                }}>
                  {data.change >= 0 ? '▲' : '▼'} {Math.abs(data.change)} pts vs last month
                </div>
              )}

              {/* Score legend */}
              <div style={{ marginTop: 16, display: 'flex', flexDirection: 'column', gap: 4, width: '100%' }}>
                {[
                  { range: '80–100', label: 'Excellent', color: '#22c55e' },
                  { range: '60–79', label: 'Good', color: '#84cc16' },
                  { range: '40–59', label: 'Fair', color: '#f59e0b' },
                  { range: '20–39', label: 'Poor', color: '#f97316' },
                  { range: '0–19', label: 'Critical', color: '#ef4444' },
                ].map(({ range, label, color }) => (
                  <div key={range} style={{ display: 'flex', justifyContent: 'space-between', fontSize: 11 }}>
                    <span style={{ color: '#475569' }}>{range}</span>
                    <span style={{ color, fontWeight: 600 }}>{label}</span>
                  </div>
                ))}
              </div>
            </div>

            {/* ── Breakdown bars ──────────────────────────────────────────── */}
            <div className="health-breakdown-card" style={{ flex: 1 }}>
              <div className="card-title" style={{ marginBottom: 20 }}>Score Breakdown</div>
              {(data.breakdown ?? []).map(item => (
                <div key={item.label} className="health-bar-item">
                  <div className="health-bar-label">
                    <span>{item.label}</span>
                    <span style={{ color: scoreColor(item.score), fontWeight: 700 }}>
                      {item.score}/100
                    </span>
                  </div>
                  <div className="health-bar-bg">
                    <div
                      className="health-bar-fill"
                      style={{
                        width: `${item.score}%`,
                        background: scoreColor(item.score),
                      }}
                    />
                  </div>
                  <div style={{ fontSize: 11, color: '#475569', marginTop: 3 }}>
                    {item.detail} · Weight: {item.weight}%
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* ── AI Recommendations ─────────────────────────────────────────── */}
          <div className="health-recs-card">
            <div className="card-header">
              <span className="card-title">🤖 AI Recommendations</span>
              <span style={{ fontSize: 11, color: '#475569' }}>Powered by GPT-4o-mini</span>
            </div>
            {data.recommendations
              ? data.recommendations
                .split('\n')
                .filter(l => l.trim())
                .map((line, i) => (
                  <div key={i} className="health-rec-item">
                    <span className="health-rec-bullet">•</span>
                    <span>{line.replace(/^[•\-*]\s*/, '')}</span>
                  </div>
                ))
              : (
                <div style={{ color: '#475569', fontSize: 13, padding: '8px 0' }}>
                  No recommendations available. Add more transactions to generate insights.
                </div>
              )}
          </div>
        </>
      )}
    </div>
  )
}
