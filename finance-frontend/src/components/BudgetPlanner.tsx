// PATH: finance-frontend/src/components/BudgetPlanner.tsx
// NEW: Budget vs actual variance tracking with visual progress bars

import { useEffect, useState, useCallback } from 'react'
import api from '../api'


interface BudgetVariance {
  id: number; categoryName: string; budgeted: number; actual: number;
  variance: number; percentage: number; status: 'OK' | 'WARNING' | 'OVER'
}
interface BudgetDTO {
  id: number; categoryName: string; categoryId: number | null; year: number; month: number; amount: number; spent: number
}
interface CategoryOption {
  id: number
  name: string
  type: 'INCOME' | 'EXPENSE'
}

export default function BudgetPlanner({ companyId }: { companyId: number }) {

  const [variances, setVariances] = useState<BudgetVariance[]>([])
  const [categories, setCategories] = useState<CategoryOption[]>([])
  const [loading, setLoading] = useState(true)
  const [showForm, setShowForm] = useState(false)
  const [form, setForm] = useState({ categoryId: '', amount: '', year: new Date().getFullYear(), month: new Date().getMonth() + 1 })
  const [saving, setSaving] = useState(false)
  const [msg, setMsg] = useState<string | null>(null)

  const fetchVariances = useCallback(async () => {
    setLoading(true)
    try {
      const res = await api.get<BudgetVariance[]>(
        `/api/v1/${companyId}/budgets/variance?year=${form.year}&month=${form.month}`)
      setVariances(Array.isArray(res.data) ? res.data : [])
    } catch { setVariances([]) } finally { setLoading(false) }
  }, [companyId, form.year, form.month])

  useEffect(() => { void fetchVariances() }, [fetchVariances])
  useEffect(() => {
    api.get<CategoryOption[]>(`/api/v1/${companyId}/categories`)
      .then(res => {
        const options = Array.isArray(res.data) ? res.data : []
        setCategories(options.filter(option => option.type === 'EXPENSE'))
      })
      .catch(() => setCategories([]))
  }, [companyId])

  const handleSave = async () => {
    if (!form.amount) return
    setSaving(true)
    try {
      await api.post<BudgetDTO>(`/api/v1/${companyId}/budgets`, {
        year: form.year, month: form.month,
        categoryId: form.categoryId ? parseInt(form.categoryId, 10) : null,
        amount: parseFloat(form.amount)
      })
      setMsg('Budget saved! ✅'); setShowForm(false); setForm({ ...form, amount: '', categoryId: '' })
      void fetchVariances()
    } catch { setMsg('Failed to save budget') } finally { setSaving(false) }
    setTimeout(() => setMsg(null), 3000)
  }

  const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']
  const currentYear = new Date().getFullYear()
  const yearOptions = [currentYear - 1, currentYear, currentYear + 1, currentYear + 2]

  return (
    <div className="budget-page">
      <div className="page-header">
        <div>
          <h1 className="page-title">🎯 Budget Planner</h1>
          <p className="page-subtitle">Set monthly budgets and track actual vs planned spending</p>
        </div>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
          <select value={form.month} onChange={e => setForm({ ...form, month: +e.target.value })}
            className="select-sm">
            {months.map((m, i) => <option key={m} value={i + 1}>{m}</option>)}
          </select>
          <select value={form.year} onChange={e => setForm({ ...form, year: +e.target.value })}
            className="select-sm">
            {yearOptions.map(y => <option key={y} value={y}>{y}</option>)}
          </select>
          <button className="btn-liquid-glass" onClick={() => setShowForm(!showForm)} style={{ padding: '8px 16px' }}>
            <span>+ Set Budget</span>
          </button>
        </div>
      </div>

      {msg && <div className="success-toast">{msg}</div>}

      {showForm && (
        <div className="modal-overlay" onClick={() => setShowForm(false)}>
          <div className="modal-box" onClick={e => e.stopPropagation()}>
            <h2>Set Budget</h2>
            <div className="form-group">
              <label>Month</label>
              <select value={form.month} onChange={e => setForm({ ...form, month: +e.target.value })} className="form-input">
                {months.map((m, i) => <option key={m} value={i + 1}>{m} {form.year}</option>)}
              </select>
            </div>
            <div className="form-group">
              <label>Category</label>
              <select
                className="form-input"
                value={form.categoryId}
                onChange={e => setForm({ ...form, categoryId: e.target.value })}
              >
                <option value="">Overall spending</option>
                {categories.map(category => (
                  <option key={category.id} value={String(category.id)}>{category.name}</option>
                ))}
              </select>
            </div>
            <div className="form-group">
              <label>Budget Amount (₹)</label>
              <input className="form-input" type="number" placeholder="e.g. 10000"
                value={form.amount} onChange={e => setForm({ ...form, amount: e.target.value })} />
            </div>
            <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
              <button className="btn-dismiss" onClick={() => setShowForm(false)} style={{ padding: '8px 16px' }}>Cancel</button>
              <button className="btn-liquid-glass" onClick={handleSave} disabled={saving}>
                <span>{saving ? 'Saving...' : 'Save Budget'}</span>
              </button>
            </div>
          </div>
        </div>
      )}

      {loading ? (
        <div className="loading">⏳ Loading budgets...</div>
      ) : variances.length === 0 ? (
        <div className="empty-state">
          <div style={{ fontSize: 48 }}>🎯</div>
          <h3>No budgets set for {months[form.month - 1]} {form.year}</h3>
          <p>Click "Set Budget" to create your first budget entry</p>
        </div>
      ) : (
        <div className="budget-list">
          {variances.map(v => {
            const pct = Math.min(v.percentage, 100)
            const barColor = v.status === 'OVER' ? '#ef4444' : v.status === 'WARNING' ? '#f59e0b' : '#10b981'
            const statusBg = v.status === 'OVER' ? '#450a0a' : v.status === 'WARNING' ? '#451a03' : '#052e16'
            return (
              <div key={v.id} className="budget-item">
                <div className="budget-item-header">
                  <span className="budget-cat">{v.categoryName}</span>
                  <span className="budget-status-badge" style={{ background: statusBg, color: barColor }}>
                    {v.status === 'OVER' ? '🔴 Over Budget' : v.status === 'WARNING' ? '🟡 Warning' : '🟢 OK'}
                  </span>
                </div>
                <div className="budget-bar-container">
                  <div className="budget-bar-bg">
                    <div className="budget-bar-fill" style={{ width: pct + '%', background: barColor }} />
                  </div>
                  <span className="budget-pct" style={{ color: barColor }}>{v.percentage}%</span>
                </div>
                <div className="budget-item-footer">
                  <span className="budget-spent">Spent: <strong style={{ color: '#e2e8f0' }}>
                    ₹{v.actual.toLocaleString('en-IN', { maximumFractionDigits: 0 })}</strong>
                  </span>
                  <span className="budget-limit">Budget: <strong style={{ color: '#e2e8f0' }}>
                    ₹{v.budgeted.toLocaleString('en-IN', { maximumFractionDigits: 0 })}</strong>
                  </span>
                  <span style={{ color: v.variance >= 0 ? '#10b981' : '#ef4444' }}>
                    {v.variance >= 0 ? '✅ ₹' + v.variance.toLocaleString('en-IN', { maximumFractionDigits: 0 }) + ' left'
                      : '⚠️ ₹' + Math.abs(v.variance).toLocaleString('en-IN', { maximumFractionDigits: 0 }) + ' over'}
                  </span>
                </div>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
