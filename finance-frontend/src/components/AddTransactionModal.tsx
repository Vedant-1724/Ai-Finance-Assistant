// PATH: finance-frontend/src/components/AddTransactionModal.tsx
// Supports both creating new transactions and editing existing ones.

import { useState, useEffect, type FormEvent } from 'react'
import api from '../api'

interface Transaction {
  id: number
  date: string
  amount: number
  description: string
  categoryName: string | null
}

interface Props {
  companyId: number
  onClose: () => void
  onSuccess: () => void
  editingTxn?: Transaction | null  // If provided, modal is in edit mode
}

interface CategoryOption {
  id: number
  name: string
  type: string
}

export default function AddTransactionModal({ companyId, onClose, onSuccess, editingTxn }: Props) {
  const isEdit = !!editingTxn

  const [date, setDate] = useState(
    editingTxn ? editingTxn.date : new Date().toISOString().split('T')[0]
  )
  const [description, setDescription] = useState(editingTxn?.description ?? '')
  const [amount, setAmount] = useState(
    editingTxn ? String(Math.abs(editingTxn.amount)) : ''
  )
  const [type, setType] = useState<'income' | 'expense'>(
    editingTxn ? (editingTxn.amount >= 0 ? 'income' : 'expense') : 'expense'
  )
  const [categoryId, setCategoryId] = useState<string>('')
  const [categories, setCategories] = useState<CategoryOption[]>([])
  const [loading, setLoading] = useState(false)
  const [catLoading, setCatLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [aiSuggested, setAiSuggested] = useState<string | null>(null)

  // ── Load categories ────────────────────────────────────────────────────────
  useEffect(() => {
    api.get<CategoryOption[]>(`/api/v1/${companyId}/categories`)
      .then(r => setCategories(r.data))
      .catch(() => setCategories([]))
  }, [companyId])

  // ── AI auto-categorise after user finishes typing description ─────────────
  useEffect(() => {
    if (description.length < 5) return
    const timer = setTimeout(async () => {
      setCatLoading(true)
      try {
        const res = await api.post<{ category: string; confidence: number }>(
          `/api/v1/${companyId}/transactions/categorize`,
          { description }
        )
        if (res.data?.category && res.data.confidence > 0.4) {
          setAiSuggested(res.data.category)
          const match = categories.find(
            c => c.name.toLowerCase() === res.data.category.toLowerCase()
          )
          if (match) setCategoryId(String(match.id))
        }
      } catch {
        // AI categorize is optional — fail silently
      } finally {
        setCatLoading(false)
      }
    }, 700)
    return () => clearTimeout(timer)
  }, [description, categories, companyId])

  // ── Submit ─────────────────────────────────────────────────────────────────
  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)

    if (!date) { setError('Please select a date.'); return }
    if (!description) { setError('Description is required.'); return }
    if (!amount || isNaN(parseFloat(amount)) || parseFloat(amount) <= 0) {
      setError('Enter a valid positive amount.'); return
    }

    const finalAmount = type === 'expense'
      ? -Math.abs(parseFloat(amount))
      : Math.abs(parseFloat(amount))

    setLoading(true)
    try {
      if (isEdit) {
        // UPDATE existing transaction
        await api.put(`/api/v1/${companyId}/transactions/${editingTxn!.id}`, {
          date,
          description: description.trim(),
          amount: finalAmount,
          categoryId: categoryId ? parseInt(categoryId) : null,
        })
      } else {
        // CREATE new transaction
        await api.post(`/api/v1/${companyId}/transactions`, {
          date,
          description: description.trim(),
          amount: finalAmount,
          categoryId: categoryId ? parseInt(categoryId) : null,
        })
      }
      onSuccess()
      onClose()
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { error?: string } } })
        ?.response?.data?.error
      setError(msg ?? `Failed to ${isEdit ? 'update' : 'save'} transaction. Please try again.`)
    } finally {
      setLoading(false)
    }
  }

  const filteredCategories = categories.filter(c =>
    type === 'income' ? c.type === 'INCOME' : c.type === 'EXPENSE'
  )

  return (
    <div className="modal-overlay" onClick={e => { if (e.target === e.currentTarget) onClose() }}>
      <div className="modal-box" role="dialog" aria-modal="true" aria-label={isEdit ? 'Edit Transaction' : 'Add Transaction'}>

        {/* Header */}
        <div className="modal-header">
          <h2 className="modal-title">{isEdit ? '✏️ Edit Transaction' : '➕ Add Transaction'}</h2>
          <button className="modal-close" onClick={onClose} aria-label="Close">×</button>
        </div>

        {error && <div className="auth-error" style={{ marginBottom: 16 }}>{error}</div>}

        <form onSubmit={handleSubmit}>

          {/* Type toggle */}
          <div className="form-group">
            <label>Type</label>
            <div style={{ display: 'flex', gap: 8 }}>
              {(['expense', 'income'] as const).map(t => (
                <button
                  key={t}
                  type="button"
                  onClick={() => { setType(t); setCategoryId('') }}
                  style={{
                    flex: 1, padding: '10px', borderRadius: 8, border: '1px solid',
                    cursor: 'pointer', fontWeight: 600, fontSize: 14, transition: 'all 0.15s',
                    background: type === t
                      ? t === 'expense' ? 'rgba(239,68,68,0.12)' : 'rgba(34,197,94,0.12)'
                      : 'rgba(255,255,255,0.04)',
                    borderColor: type === t
                      ? t === 'expense' ? 'rgba(239,68,68,0.5)' : 'rgba(34,197,94,0.5)'
                      : 'rgba(255,255,255,0.1)',
                    color: type === t
                      ? t === 'expense' ? '#f87171' : '#4ade80'
                      : '#94a3b8',
                  }}
                >
                  {t === 'expense' ? '📉 Expense' : '📈 Income'}
                </button>
              ))}
            </div>
          </div>

          {/* Date */}
          <div className="form-group">
            <label htmlFor="txn-date">Date</label>
            <input
              id="txn-date"
              type="date"
              className="form-input"
              value={date}
              onChange={e => setDate(e.target.value)}
              max={new Date().toISOString().split('T')[0]}
              required
            />
          </div>

          {/* Description */}
          <div className="form-group">
            <label htmlFor="txn-desc">
              Description
              {catLoading && (
                <span style={{ color: '#60a5fa', fontSize: 11, marginLeft: 8 }}>
                  🤖 AI categorising…
                </span>
              )}
              {aiSuggested && !catLoading && (
                <span style={{ color: '#4ade80', fontSize: 11, marginLeft: 8 }}>
                  ✓ AI: {aiSuggested}
                </span>
              )}
            </label>
            <input
              id="txn-desc"
              type="text"
              className="form-input"
              placeholder="e.g. Office supplies from Staples"
              value={description}
              onChange={e => setDescription(e.target.value)}
              maxLength={512}
              required
            />
          </div>

          {/* Amount */}
          <div className="form-group">
            <label htmlFor="txn-amount">Amount (₹)</label>
            <div className="input-wrapper">
              <span style={{
                position: 'absolute', left: 12, top: '50%', transform: 'translateY(-50%)',
                color: '#475569', fontWeight: 600, pointerEvents: 'none'
              }}>₹</span>
              <input
                id="txn-amount"
                type="number"
                className="form-input"
                style={{ paddingLeft: 28 }}
                placeholder="0.00"
                value={amount}
                onChange={e => setAmount(e.target.value)}
                min="0.01"
                step="0.01"
                required
              />
            </div>
          </div>

          {/* Category */}
          <div className="form-group">
            <label htmlFor="txn-cat">Category (optional)</label>
            <select
              id="txn-cat"
              className="form-select"
              value={categoryId}
              onChange={e => setCategoryId(e.target.value)}
            >
              <option value="">— Select category —</option>
              {filteredCategories.map(c => (
                <option key={c.id} value={String(c.id)}>{c.name}</option>
              ))}
            </select>
          </div>

          {/* Footer */}
          <div className="modal-footer">
            <button type="button" className="btn-secondary" onClick={onClose}>
              Cancel
            </button>
            <button type="submit" className="btn-primary" disabled={loading}>
              {loading
                ? '⏳ Saving…'
                : isEdit ? '✏️ Update Transaction' : '💾 Save Transaction'}
            </button>
          </div>

        </form>
      </div>
    </div>
  )
}