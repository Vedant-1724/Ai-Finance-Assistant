// PATH: finance-frontend/src/pages/HealthScorePage.tsx
// NEW: Financial Health Score gauge + AI recommendations

import { useEffect, useState } from 'react'
import api from '../api'
import { useAuth } from '../context/AuthContext'

interface HealthScore {
  id: number; companyId: number; month: string; score: number; breakdown: string; recommendations: string; createdAt: string
}

export default function HealthScorePage({ companyId }: { companyId: number }) {
  const { user } = useAuth()
  const [score, setScore]     = useState<HealthScore | null>(null)
  const [history, setHistory] = useState<HealthScore[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError]     = useState<string | null>(null)

  const headers = { Authorization: `Bearer ${user?.token}` }

  useEffect(() => {
    const load = async () => {
      setLoading(true)
      try {
        const [s, h] = await Promise.all([
          api.get<HealthScore>(`/api/v1/${companyId}/health/score`, { headers }),
          api.get<HealthScore[]>(`/api/v1/${companyId}/health/history`, { headers })
        ])
        setScore(s.data); setHistory(h.data)
      } catch (e: any) {
        if (e?.response?.status === 402) setError('UPGRADE_REQUIRED')
        else setError('Failed to load health score')
      } finally { setLoading(false) }
    }
    void load()
  }, [companyId, user?.token])

  if (loading) return <div className="loading">⏳ Computing financial health...</div>
  if (error === 'UPGRADE_REQUIRED') return (
    <div className="upgrade-gate">
      <div style={{fontSize:48}}>💚</div>
      <h2>Health Score requires Trial or Pro</h2>
      <p>Get your monthly financial health score and AI-powered recommendations.</p>
      <a href="/subscription" className="btn-primary">Upgrade Now</a>
    </div>
  )
  if (error || !score) return <div className="error">❌ {error}</div>

  const s = score.score
  const color = s >= 70 ? '#10b981' : s >= 40 ? '#f59e0b' : '#ef4444'
  const grade = s >= 70 ? 'Healthy 💚' : s >= 40 ? 'Needs Attention 🟡' : 'Critical 🔴'
  const circumference = 2 * Math.PI * 54
  const dashOffset = circumference - (s / 100) * circumference

  return (
    <div className="health-page">
      <div className="page-header">
        <h1 className="page-title">💚 Financial Health Score</h1>
        <p className="page-subtitle">AI-computed monthly analysis of your financial wellbeing</p>
      </div>

      <div className="health-main">
        {/* Score gauge */}
        <div className="card health-gauge-card" style={{textAlign:'center', padding:32}}>
          <svg width="140" height="140" viewBox="0 0 140 140">
            <circle cx="70" cy="70" r="54" fill="none" stroke="#1e293b" strokeWidth="12" />
            <circle cx="70" cy="70" r="54" fill="none" stroke={color} strokeWidth="12"
                    strokeLinecap="round"
                    strokeDasharray={circumference}
                    strokeDashoffset={dashOffset}
                    transform="rotate(-90 70 70)"
                    style={{transition:'stroke-dashoffset 1s ease'}} />
            <text x="70" y="65" textAnchor="middle" fill={color} fontSize="28" fontWeight="bold">{s}</text>
            <text x="70" y="85" textAnchor="middle" fill="#94a3b8" fontSize="11">/ 100</text>
          </svg>
          <div style={{fontSize:18, fontWeight:'bold', color, marginTop:8}}>{grade}</div>
          <div style={{color:'#64748b', fontSize:13, marginTop:4}}>
            {new Date(score.month).toLocaleString('en-IN',{month:'long', year:'numeric'})}
          </div>
        </div>

        {/* Recommendations */}
        <div className="card" style={{flex:1}}>
          <h3 style={{marginTop:0, color:'#e2e8f0'}}>🤖 AI Recommendations</h3>
          <div style={{color:'#94a3b8', lineHeight:1.8, whiteSpace:'pre-line'}}>
            {score.recommendations || 'No recommendations available yet.'}
          </div>
        </div>
      </div>

      {/* History */}
      {history.length > 1 && (
        <div className="card" style={{marginTop:24}}>
          <h3 style={{marginTop:0, color:'#e2e8f0'}}>📅 Score History</h3>
          <div style={{display:'flex', gap:12, flexWrap:'wrap'}}>
            {history.map(h => {
              const c = h.score >= 70 ? '#10b981' : h.score >= 40 ? '#f59e0b' : '#ef4444'
              return (
                <div key={h.id} style={{background:'#1e293b', borderRadius:8, padding:'12px 16px', textAlign:'center', minWidth:80}}>
                  <div style={{fontSize:22, fontWeight:'bold', color:c}}>{h.score}</div>
                  <div style={{color:'#64748b', fontSize:11}}>
                    {new Date(h.month).toLocaleString('en-IN',{month:'short',year:'2-digit'})}
                  </div>
                </div>
              )
            })}
          </div>
        </div>
      )}
    </div>
  )
}
