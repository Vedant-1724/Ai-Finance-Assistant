import { useEffect, useState, type FormEvent } from 'react'
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
  editingTxn?: Transaction | null
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
  const [amount, setAmount] = useState(editingTxn ? String(Math.abs(editingTxn.amount)) : '')
  const [type, setType] = useState<'income' | 'expense'>(
    editingTxn ? (editingTxn.amount >= 0 ? 'income' : 'expense') : 'expense'
  )
  const [categoryId, setCategoryId] = useState<string>('')
  const [categories, setCategories] = useState<CategoryOption[]>([])
  const [loading, setLoading] = useState(false)
  const [catLoading, setCatLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [aiSuggested, setAiSuggested] = useState<string | null>(null)

  useEffect(() => {
    api.get<CategoryOption[]>(`/api/v1/${companyId}/categories`)
      .then(response => setCategories(response.data))
      .catch(() => setCategories([]))
  }, [companyId])

  useEffect(() => {
    if (!editingTxn?.categoryName || categories.length === 0) {
      return
    }

    const match = categories.find(category => category.name.toLowerCase() === editingTxn.categoryName?.toLowerCase())
    if (match) {
      setCategoryId(String(match.id))
    }
  }, [categories, editingTxn])

  useEffect(() => {
    if (description.trim().length < 5) {
      setAiSuggested(null)
      return
    }

    const timer = window.setTimeout(async () => {
      setCatLoading(true)
      try {
        const res = await api.post<{ category: string; confidence: number }>(
          `/api/v1/${companyId}/transactions/categorize`,
          { description, type }
        )
        if (res.data.category && res.data.confidence > 0.4) {
          setAiSuggested(res.data.category)
          const match = categories.find(
            category => category.name.toLowerCase() === res.data.category.toLowerCase()
          )
          if (match) {
            setCategoryId(String(match.id))
          }
        }
      } catch {
        // Categorization is optional. Leave manual entry available.
      } finally {
        setCatLoading(false)
      }
    }, 700)

    return () => window.clearTimeout(timer)
  }, [categories, companyId, description, type])

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setError(null)

    if (!date) {
      setError('Please select a date.')
      return
    }
    if (!description.trim()) {
      setError('Description is required.')
      return
    }
    if (!amount || Number.isNaN(parseFloat(amount)) || parseFloat(amount) <= 0) {
      setError('Enter a valid positive amount.')
      return
    }

    const finalAmount = type === 'expense'
      ? -Math.abs(parseFloat(amount))
      : Math.abs(parseFloat(amount))

    setLoading(true)
    try {
      const payload = {
        date,
        description: description.trim(),
        amount: finalAmount,
        categoryId: categoryId ? parseInt(categoryId, 10) : null,
      }

      if (isEdit) {
        await api.put(`/api/v1/${companyId}/transactions/${editingTxn!.id}`, payload)
      } else {
        await api.post(`/api/v1/${companyId}/transactions`, payload)
      }

      onSuccess()
      onClose()
    } catch (err: unknown) {
      const data = (err as { response?: { data?: { error?: string; message?: string } } })?.response?.data
      setError(data?.error ?? data?.message ?? `Failed to ${isEdit ? 'update' : 'save'} transaction. Please try again.`)
    } finally {
      setLoading(false)
    }
  }

  const filteredCategories = categories.filter(category =>
    type === 'income' ? category.type === 'INCOME' : category.type === 'EXPENSE'
  )

  return (
    <div className="modal-overlay" onClick={event => { if (event.target === event.currentTarget) onClose() }}>
      <div className="premium-modal-box" role="dialog" aria-modal="true" aria-label={isEdit ? 'Edit Transaction' : 'Add Transaction'}>
        <div className="modal-header">
          <h2 className="modal-title">{isEdit ? '✏️ Edit Transaction' : '➕ Add Transaction'}</h2>
          <button className="modal-close" onClick={onClose} aria-label="Close">×</button>
        </div>

        {error && <div className="auth-error" style={{ marginBottom: 16 }}>{error}</div>}

        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label>Type</label>
            <div style={{ display: 'flex', gap: 8 }}>
              {(['expense', 'income'] as const).map(nextType => (
                <button
                  key={nextType}
                  type="button"
                  onClick={() => {
                    setType(nextType)
                    setCategoryId('')
                    setAiSuggested(null)
                  }}
                  style={{
                    flex: 1,
                    padding: '10px',
                    borderRadius: 8,
                    border: '1px solid',
                    cursor: 'pointer',
                    fontWeight: 600,
                    fontSize: 14,
                    transition: 'all 0.15s',
                    background: type === nextType
                      ? nextType === 'expense' ? 'rgba(239,68,68,0.12)' : 'rgba(34,197,94,0.12)'
                      : 'rgba(255,255,255,0.04)',
                    borderColor: type === nextType
                      ? nextType === 'expense' ? 'rgba(239,68,68,0.5)' : 'rgba(34,197,94,0.5)'
                      : 'rgba(255,255,255,0.1)',
                    color: type === nextType
                      ? nextType === 'expense' ? '#f87171' : '#4ade80'
                      : '#94a3b8',
                  }}
                >
                  {nextType === 'expense' ? '📉 Expense' : '📈 Income'}
                </button>
              ))}
            </div>
          </div>

          <div className="form-group">
            <label htmlFor="txn-date">Date</label>
            <input
              id="txn-date"
              type="date"
              className="form-input"
              value={date}
              onChange={event => setDate(event.target.value)}
              max={new Date().toISOString().split('T')[0]}
              required
            />
          </div>

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
              onChange={event => setDescription(event.target.value)}
              maxLength={500}
              required
            />
          </div>

          <div className="form-group">
            <label htmlFor="txn-amount">Amount (₹)</label>
            <div className="input-wrapper">
              <span
                style={{
                  position: 'absolute',
                  left: 12,
                  top: '50%',
                  transform: 'translateY(-50%)',
                  color: '#475569',
                  fontWeight: 600,
                  pointerEvents: 'none',
                }}
              >
                ₹
              </span>
              <input
                id="txn-amount"
                type="number"
                className="form-input"
                style={{ paddingLeft: 28 }}
                placeholder="0.00"
                value={amount}
                onChange={event => setAmount(event.target.value)}
                min="0.01"
                step="0.01"
                required
              />
            </div>
          </div>

          <div className="form-group">
            <label htmlFor="txn-cat">Category (optional)</label>
            <select
              id="txn-cat"
              className="form-select"
              value={categoryId}
              onChange={event => setCategoryId(event.target.value)}
            >
              <option value="">— Select category —</option>
              {filteredCategories.map(category => (
                <option key={category.id} value={String(category.id)}>{category.name}</option>
              ))}
            </select>
          </div>

          <div className="modal-footer">
            <button type="button" className="btn-dismiss" onClick={onClose} style={{ padding: '8px 16px' }}>
              Cancel
            </button>
            <button type="submit" className="btn-liquid-glass" disabled={loading}>
              <span>{loading ? '⏳ Saving…' : isEdit ? '✏️ Update Transaction' : '💾 Save Transaction'}</span>
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

