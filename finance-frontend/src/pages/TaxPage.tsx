// PATH: finance-frontend/src/pages/TaxPage.tsx
// NEW: GST summary + Income Tax estimation for Indian SMBs

import { useEffect, useState } from 'react'
import api from '../api'
import { useAuth } from '../context/AuthContext'

interface GstSlab { rate: number; taxableAmount: number; cgst: number; sgst: number }
interface GstSummary { year: number; quarter: number; startDate: string; endDate: string;
  totalTaxable: number; totalGst: number; totalCgst: number; totalSgst: number; slabs: GstSlab[] }
interface Instalment { dueDate: string; cumulative: string; amount: number }
interface TaxEstimate { financialYear: number; totalIncome: number; totalExpense: number; netProfit: number;
  incomeTax: number; cess: number; totalTax: number; effectiveRatePercent: number; advanceTaxSchedule: Instalment[] }

export default function TaxPage({ companyId }: { companyId: number }) {
  const { user } = useAuth()
  const [gst, setGst]       = useState<GstSummary | null>(null)
  const [tax, setTax]       = useState<TaxEstimate | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError]   = useState<string | null>(null)
  const [activeTab, setTab] = useState<'gst'|'income'>('gst')

  const headers = { Authorization: `Bearer ${user?.token}` }
  const curr = new Date()
  const fy = curr.getMonth() >= 3 ? curr.getFullYear() : curr.getFullYear() - 1

  useEffect(() => {
    const load = async () => {
      setLoading(true); setError(null)
      try {
        const [g, t] = await Promise.all([
          api.get<GstSummary>(`/api/v1/${companyId}/tax/gst`, { headers }),
          api.get<TaxEstimate>(`/api/v1/${companyId}/tax/income`, { headers })
        ])
        setGst(g.data); setTax(t.data)
      } catch (e: any) {
        if (e?.response?.status === 402) setError('UPGRADE_REQUIRED')
        else setError('Failed to load tax data')
      } finally { setLoading(false) }
    }
    void load()
  }, [companyId, user?.token])

  if (loading) return <div className="loading">⏳ Loading tax data...</div>
  if (error === 'UPGRADE_REQUIRED') return (
    <div className="upgrade-gate">
      <div style={{fontSize:48}}>🧾</div>
      <h2>Tax & GST requires Trial or Pro</h2>
      <p>Upgrade to get automatic GST liability calculation and income tax estimates.</p>
      <a href="/subscription" className="btn-primary">Upgrade Now</a>
    </div>
  )
  if (error) return <div className="error">❌ {error}</div>

  const fmt = (n: number) => '₹' + n.toLocaleString('en-IN', {maximumFractionDigits:2})

  return (
    <div className="tax-page">
      <div className="page-header">
        <div>
          <h1 className="page-title">🧾 Tax & GST Estimator</h1>
          <p className="page-subtitle">⚠️ Estimates only — consult a CA for official filings</p>
        </div>
      </div>

      <div className="tab-row" style={{marginBottom:24}}>
        <button className={`tab-btn ${activeTab==='gst'?'active':''}`} onClick={() => setTab('gst')}>GST Summary</button>
        <button className={`tab-btn ${activeTab==='income'?'active':''}`} onClick={() => setTab('income')}>Income Tax FY{fy}-{fy+1}</button>
      </div>

      {activeTab === 'gst' && gst && (
        <div className="tax-section">
          <div className="metric-row">
            {[['Taxable Turnover', fmt(gst.totalTaxable), '#3b82f6'],
              ['Total GST', fmt(gst.totalGst), '#f59e0b'],
              ['CGST', fmt(gst.totalCgst), '#8b5cf6'],
              ['SGST', fmt(gst.totalSgst), '#ec4899']
            ].map(([l,v,c]) => (
              <div key={l as string} className="metric-card" style={{flex:1}}>
                <div className="metric-label">{l as string}</div>
                <div className="metric-value" style={{color: c as string, fontSize:20}}>{v as string}</div>
              </div>
            ))}
          </div>

          <div className="card" style={{marginTop:24}}>
            <h3 style={{marginTop:0, color:'#e2e8f0'}}>GSTR-3B Slab Breakdown</h3>
            <table className="data-table">
              <thead><tr><th>GST Rate</th><th>Taxable Amount</th><th>CGST</th><th>SGST</th><th>Total GST</th></tr></thead>
              <tbody>
                {gst.slabs.filter(s => s.taxableAmount > 0).map(s => (
                  <tr key={s.rate}>
                    <td><span className="badge">{s.rate}%</span></td>
                    <td>{fmt(s.taxableAmount)}</td>
                    <td>{fmt(s.cgst)}</td>
                    <td>{fmt(s.sgst)}</td>
                    <td style={{fontWeight:'bold'}}>{fmt(s.cgst + s.sgst)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {activeTab === 'income' && tax && (
        <div className="tax-section">
          <div className="metric-row">
            {[['Net Profit', fmt(tax.netProfit), '#10b981'],
              ['Income Tax', fmt(tax.incomeTax), '#ef4444'],
              ['Health & Ed Cess (4%)', fmt(tax.cess), '#f59e0b'],
              ['Total Tax', fmt(tax.totalTax), '#ef4444']
            ].map(([l,v,c]) => (
              <div key={l as string} className="metric-card" style={{flex:1}}>
                <div className="metric-label">{l as string}</div>
                <div className="metric-value" style={{color: c as string, fontSize:20}}>{v as string}</div>
              </div>
            ))}
          </div>

          <div className="card" style={{marginTop:24}}>
            <h3 style={{marginTop:0, color:'#e2e8f0'}}>Advance Tax Schedule (New Tax Regime)</h3>
            <table className="data-table">
              <thead><tr><th>Instalment Due</th><th>Cumulative %</th><th>Amount</th></tr></thead>
              <tbody>
                {tax.advanceTaxSchedule.map(i => (
                  <tr key={i.dueDate}>
                    <td>{i.dueDate}</td>
                    <td>{i.cumulative}</td>
                    <td style={{fontWeight:'bold', color:'#f59e0b'}}>{fmt(i.amount)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            <p style={{color:'#64748b', fontSize:12, marginTop:12}}>
              * These are estimates under the New Tax Regime for FY{tax.financialYear}-{tax.financialYear+1}.
              Consult a Chartered Accountant for actual filing. Effective tax rate: {Number(tax.effectiveRatePercent).toFixed(1)}%
            </p>
          </div>
        </div>
      )}
    </div>
  )
}
