// PATH: finance-frontend/src/pages/TaxPage.tsx
// GST & Tax summary page.
// Tabs: GST Summary | TDS | Advance Tax | ITR Checklist
// Calls GET /api/v1/{companyId}/tax?year={year}&quarter={quarter}

import { useEffect, useState, useCallback } from 'react'
import api from '../api'
import { useAuth } from '../context/AuthContext'

interface GstEntry {
  categoryName: string
  gstRate:      number
  taxableAmount: number
  gstAmount:    number
  type:         'INCOME' | 'EXPENSE'
}

interface TaxSummary {
  financialYear:  string
  quarter:        string
  totalIncome:    number
  totalExpense:   number
  netProfit:      number
  gstCollected:   number   // from income (output tax)
  gstPaid:        number   // from expenses (input tax)
  gstPayable:     number   // collected - paid
  estimatedTDS:   number
  estimatedAdvTax: number
  breakdown:      GstEntry[]
}

const QUARTERS = ['Q1 (Apr–Jun)', 'Q2 (Jul–Sep)', 'Q3 (Oct–Dec)', 'Q4 (Jan–Mar)']
const YEARS    = ['2025-26', '2024-25', '2023-24']

const ITR_CHECKLIST = [
  { id: 1, text: 'Collect Form 16 / 16A from all deductors' },
  { id: 2, text: 'Download AIS & TIS from Income Tax portal (26AS)' },
  { id: 3, text: 'Reconcile TDS credits with books of accounts' },
  { id: 4, text: 'Verify GST returns filed (GSTR-1, GSTR-3B)' },
  { id: 5, text: 'Ensure advance tax paid before 15th Mar deadline' },
  { id: 6, text: 'Prepare Profit & Loss statement for the year' },
  { id: 7, text: 'Maintain all invoices above ₹10,000 (digital copies)' },
  { id: 8, text: 'File ITR before 31st July (non-audit) or 31st Oct (audit)' },
]

export default function TaxPage({ companyId }: { companyId: number }) {
  const { user, isFree } = useAuth()
  const [activeTab,   setActiveTab]  = useState<'gst' | 'tds' | 'adv' | 'itr'>('gst')
  const [year,        setYear]       = useState(YEARS[0])
  const [quarter,     setQuarter]    = useState('Q1')
  const [data,        setData]       = useState<TaxSummary | null>(null)
  const [loading,     setLoading]    = useState(true)
  const [error,       setError]      = useState<string | null>(null)
  const [checked,     setChecked]    = useState<Set<number>>(new Set())

  const load = useCallback(async () => {
    setLoading(true); setError(null)
    try {
      const res = await api.get<TaxSummary>(
        `/api/v1/${companyId}/tax?year=${encodeURIComponent(year)}&quarter=${quarter.slice(0,2)}`,
        { headers }
      )
      setData(res.data)
    } catch (e: unknown) {
      const status = (e as { response?: { status?: number } })?.response?.status
      if (status === 402) setError('UPGRADE_REQUIRED')
      else                setError('Failed to load tax data.')
    } finally { setLoading(false) }
  }, [companyId, year, quarter])

  useEffect(() => { void load() }, [load])

  const toggleCheck = (id: number) =>
    setChecked(prev => {
      const s = new Set(prev)
      s.has(id) ? s.delete(id) : s.add(id)
      return s
    })

  if (isFree) return (
    <div className="upgrade-gate">
      <div style={{ fontSize: 56 }}>🧾</div>
      <h2>Tax & GST requires Trial or Pro</h2>
      <p>Access GST summaries, TDS estimates, advance tax calculations, and ITR checklists.</p>
      <a href="/subscription" className="btn-primary">Upgrade Now →</a>
    </div>
  )

  if (loading) return <div className="loading">⏳ Loading tax data…</div>
  if (error === 'UPGRADE_REQUIRED') return (
    <div className="upgrade-gate">
      <div style={{ fontSize: 56 }}>🧾</div>
      <h2>Tax & GST requires Trial or Pro</h2>
      <a href="/subscription" className="btn-primary">Upgrade Now →</a>
    </div>
  )
  if (error) return <div className="error">❌ {error}</div>

  const fmt = (n: number) =>
    '₹' + Math.abs(n).toLocaleString('en-IN', { maximumFractionDigits: 2 })

  return (
    <div className="tax-page">
      {/* Header */}
      <div className="page-header">
        <h1 className="page-title">🧾 Tax &amp; GST</h1>
        <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
          <select className="select-sm" value={year}    onChange={e => setYear(e.target.value)}>
            {YEARS.map(y => <option key={y}>{y}</option>)}
          </select>
          <select className="select-sm" value={quarter} onChange={e => setQuarter(e.target.value)}>
            {QUARTERS.map(q => <option key={q} value={q.slice(0,2)}>{q}</option>)}
          </select>
        </div>
      </div>

      {/* Summary cards */}
      {data && (
        <div className="tax-summary-grid">
          {[
            { label: 'Total Income',      value: fmt(data.totalIncome),     color: '#4ade80' },
            { label: 'Total Expense',     value: fmt(data.totalExpense),     color: '#f87171' },
            { label: 'Net Profit',        value: fmt(data.netProfit),        color: data.netProfit >= 0 ? '#4ade80' : '#f87171' },
            { label: 'GST Collected',     value: fmt(data.gstCollected),     color: '#60a5fa' },
            { label: 'GST Input Credit',  value: fmt(data.gstPaid),          color: '#a78bfa' },
            { label: 'GST Payable',       value: fmt(data.gstPayable),       color: data.gstPayable > 0 ? '#fcd34d' : '#4ade80' },
          ].map(({ label, value, color }) => (
            <div key={label} className="tax-summary-card">
              <div className="tax-label">{label}</div>
              <div className="tax-value" style={{ color }}>{value}</div>
            </div>
          ))}
        </div>
      )}

      {/* Tabs */}
      <div className="tab-row">
        {(['gst', 'tds', 'adv', 'itr'] as const).map(t => (
          <button
            key={t}
            className={`tab-btn ${activeTab === t ? 'active' : ''}`}
            onClick={() => setActiveTab(t)}
          >
            {{ gst: '📊 GST Breakdown', tds: '📋 TDS Estimate',
               adv: '📅 Advance Tax',  itr: '✅ ITR Checklist' }[t]}
          </button>
        ))}
      </div>

      {/* ── GST Breakdown ───────────────────────────────────────────────────── */}
      {activeTab === 'gst' && data && (
        <div className="card">
          <div className="card-header">
            <span className="card-title">GST Breakdown by Category</span>
            <span style={{ fontSize: 12, color: '#94a3b8' }}>
              Payable = Output Tax − Input Tax Credit
            </span>
          </div>
          {data.breakdown.length === 0 ? (
            <div className="empty-state">
              <div className="empty-icon">📂</div>
              <p className="empty-title">No data for this period</p>
              <p className="empty-sub">Add transactions with categories to see GST breakdown</p>
            </div>
          ) : (
            <div style={{ overflowX: 'auto' }}>
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Category</th>
                    <th>Type</th>
                    <th>GST Rate</th>
                    <th>Taxable Amount</th>
                    <th>GST Amount</th>
                  </tr>
                </thead>
                <tbody>
                  {data.breakdown.map((row, i) => (
                    <tr key={i}>
                      <td>{row.categoryName}</td>
                      <td>
                        <span className={`type-badge ${row.type.toLowerCase()}`}>
                          {row.type === 'INCOME' ? '📈 Output' : '📉 Input'}
                        </span>
                      </td>
                      <td style={{ color: '#fcd34d', fontWeight: 600 }}>{row.gstRate}%</td>
                      <td style={{ color: '#e2e8f0' }}>{fmt(row.taxableAmount)}</td>
                      <td className={row.type === 'INCOME' ? 'amount-pos' : 'amount-neg'}>
                        {fmt(row.gstAmount)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* ── TDS Estimate ────────────────────────────────────────────────────── */}
      {activeTab === 'tds' && data && (
        <div className="card">
          <div className="card-header">
            <span className="card-title">TDS Estimate</span>
            <span style={{ fontSize: 12, color: '#94a3b8' }}>Based on current period income</span>
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            <InfoRow label="Total Income (this period)" value={fmt(data.totalIncome)} color="#4ade80" />
            <InfoRow label="Estimated TDS @ 10%"        value={fmt(data.estimatedTDS)} color="#60a5fa" />
            <InfoRow label="Net After TDS"              value={fmt(data.totalIncome - data.estimatedTDS)} color="#e2e8f0" />
            <div style={{
              background: 'rgba(59,130,246,0.08)', border: '1px solid rgba(59,130,246,0.2)',
              borderRadius: 8, padding: '12px 16px', fontSize: 13, color: '#93c5fd', lineHeight: 1.6
            }}>
              ℹ️ <strong>Disclaimer:</strong> TDS rates vary by payment type (194C, 194J, 194H, etc.).
              Consult a CA or tax consultant for exact rates applicable to your business.
            </div>
          </div>
        </div>
      )}

      {/* ── Advance Tax ─────────────────────────────────────────────────────── */}
      {activeTab === 'adv' && data && (
        <div className="card">
          <div className="card-header">
            <span className="card-title">Advance Tax Estimate</span>
            <span style={{ fontSize: 12, color: '#94a3b8' }}>Section 208 — payable if annual tax &gt; ₹10,000</span>
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            <InfoRow label="Annualised Net Profit (projected)" value={fmt(data.netProfit * 4)} color="#e2e8f0" />
            <InfoRow label="Estimated Annual Tax (30% slab)"   value={fmt(data.estimatedAdvTax)} color="#fcd34d" />
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              {[
                { installment: '1st — 15 Jun', percent: 15 },
                { installment: '2nd — 15 Sep', percent: 45 },
                { installment: '3rd — 15 Dec', percent: 75 },
                { installment: '4th — 15 Mar', percent: 100 },
              ].map(({ installment, percent }) => (
                <div key={installment} style={{
                  display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                  padding: '10px 14px', background: 'rgba(255,255,255,0.03)',
                  borderRadius: 8, border: '1px solid rgba(255,255,255,0.06)'
                }}>
                  <span style={{ color: '#94a3b8', fontSize: 13 }}>{installment}</span>
                  <span style={{ color: '#e2e8f0', fontWeight: 600 }}>
                    {percent}% = {fmt(data.estimatedAdvTax * percent / 100)}
                  </span>
                </div>
              ))}
            </div>
            <div style={{
              background: 'rgba(245,158,11,0.08)', border: '1px solid rgba(245,158,11,0.2)',
              borderRadius: 8, padding: '12px 16px', fontSize: 13, color: '#fcd34d', lineHeight: 1.6
            }}>
              ⚠️ Advance tax is due if estimated liability exceeds ₹10,000.
              Late payment attracts interest under Section 234B &amp; 234C.
            </div>
          </div>
        </div>
      )}

      {/* ── ITR Checklist ────────────────────────────────────────────────────── */}
      {activeTab === 'itr' && (
        <div className="card">
          <div className="card-header">
            <span className="card-title">ITR Filing Checklist</span>
            <span style={{ fontSize: 12, color: '#94a3b8' }}>
              {checked.size}/{ITR_CHECKLIST.length} completed
            </span>
          </div>
          {/* Progress bar */}
          <div style={{ marginBottom: 20 }}>
            <div style={{ background: 'rgba(255,255,255,0.05)', borderRadius: 6, height: 8, overflow: 'hidden' }}>
              <div style={{
                height: '100%', borderRadius: 6, transition: 'width 0.4s ease',
                width: `${(checked.size / ITR_CHECKLIST.length) * 100}%`,
                background: checked.size === ITR_CHECKLIST.length
                  ? 'linear-gradient(90deg,#22c55e,#16a34a)'
                  : 'linear-gradient(90deg,#3b82f6,#8b5cf6)',
              }} />
            </div>
            <span style={{ fontSize: 11, color: '#94a3b8', marginTop: 4, display: 'block' }}>
              {checked.size === ITR_CHECKLIST.length
                ? '🎉 All tasks completed! Ready to file ITR.'
                : `${ITR_CHECKLIST.length - checked.size} tasks remaining`}
            </span>
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
            {ITR_CHECKLIST.map(item => (
              <label key={item.id} style={{
                display: 'flex', alignItems: 'center', gap: 12,
                padding: '12px 14px', borderRadius: 8, cursor: 'pointer', transition: 'background 0.15s',
                background: checked.has(item.id) ? 'rgba(34,197,94,0.06)' : 'rgba(255,255,255,0.02)',
                border: `1px solid ${checked.has(item.id) ? 'rgba(34,197,94,0.2)' : 'rgba(255,255,255,0.05)'}`,
              }}>
                <input
                  type="checkbox"
                  checked={checked.has(item.id)}
                  onChange={() => toggleCheck(item.id)}
                  style={{ width: 16, height: 16, accentColor: '#22c55e', cursor: 'pointer' }}
                />
                <span style={{
                  fontSize: 13.5, color: checked.has(item.id) ? '#4ade80' : '#94a3b8',
                  textDecoration: checked.has(item.id) ? 'line-through' : 'none',
                  transition: 'all 0.2s',
                }}>
                  {item.text}
                </span>
              </label>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}

// ── Small helper ──────────────────────────────────────────────────────────────
function InfoRow({ label, value, color }: { label: string; value: string; color: string }) {
  return (
    <div style={{
      display: 'flex', justifyContent: 'space-between', alignItems: 'center',
      padding: '10px 0', borderBottom: '1px solid rgba(255,255,255,0.05)'
    }}>
      <span style={{ color: '#94a3b8', fontSize: 14 }}>{label}</span>
      <span style={{ color, fontWeight: 700, fontSize: 15 }}>{value}</span>
    </div>
  )
}
