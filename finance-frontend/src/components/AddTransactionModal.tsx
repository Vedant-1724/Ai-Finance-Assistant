import { useState } from 'react'
import api from '../api'                          // ← JWT-aware instance

interface AddTransactionModalProps {
  companyId: number
  onClose:   () => void
  onSuccess: () => void
}

interface FormData {
  date:        string
  description: string
  amount:      string
  type:        'income' | 'expense'
}

interface FormErrors {
  date?:        string
  description?: string
  amount?:      string
}

function AddTransactionModal({ companyId, onClose, onSuccess }: AddTransactionModalProps) {
  const today = new Date().toISOString().split('T')[0]

  const [form, setForm] = useState<FormData>({
    date:        today,
    description: '',
    amount:      '',
    type:        'income',
  })

  const [errors,      setErrors]      = useState<FormErrors>({})
  const [loading,     setLoading]     = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)

  const validate = (): boolean => {
    const newErrors: FormErrors = {}
    if (!form.date) {
      newErrors.date = 'Date is required'
    }
    if (!form.description.trim()) {
      newErrors.description = 'Description is required'
    } else if (form.description.trim().length < 3) {
      newErrors.description = 'Description must be at least 3 characters'
    }
    if (!form.amount) {
      newErrors.amount = 'Amount is required'
    } else if (isNaN(Number(form.amount)) || Number(form.amount) <= 0) {
      newErrors.amount = 'Enter a valid positive amount'
    }
    setErrors(newErrors)
    return Object.keys(newErrors).length === 0
  }

  const handleSubmit = async () => {
    if (!validate()) return
    setLoading(true)
    setSubmitError(null)

    const finalAmount =
      form.type === 'expense'
        ? -Math.abs(Number(form.amount))
        :  Math.abs(Number(form.amount))

    try {
      await api.post(`/api/v1/${companyId}/transactions`, {
        date:        form.date,
        amount:      finalAmount,
        description: form.description.trim(),
      })
      onSuccess()
      onClose()
    } catch {
      setSubmitError('Failed to save transaction. Make sure Spring Boot is running on port 8080.')
    } finally {
      setLoading(false)
    }
  }

  const handleOverlayClick = (e: React.MouseEvent<HTMLDivElement>) => {
    if (e.target === e.currentTarget) onClose()
  }

  const handleChange = (field: keyof FormData, value: string) => {
    setForm(prev => ({ ...prev, [field]: value }))
    if (errors[field as keyof FormErrors]) {
      setErrors(prev => ({ ...prev, [field]: undefined }))
    }
  }

  return (
    <div className="modal-overlay" onClick={handleOverlayClick}>
      <div className="modal-box">

        <div className="modal-header">
          <h3>➕ Add Transaction</h3>
          <button className="modal-close" onClick={onClose} aria-label="Close">✕</button>
        </div>

        <div className="modal-type-toggle">
          <button
            className={`toggle-btn income-btn ${form.type === 'income' ? 'active' : ''}`}
            onClick={() => handleChange('type', 'income')}
          >
            📈 Income
          </button>
          <button
            className={`toggle-btn expense-btn ${form.type === 'expense' ? 'active' : ''}`}
            onClick={() => handleChange('type', 'expense')}
          >
            📉 Expense
          </button>
        </div>

        <div className="modal-body">

          <div className="form-field">
            <label>Date</label>
            <input
              type="date"
              value={form.date}
              onChange={e => handleChange('date', e.target.value)}
              className={errors.date ? 'input-error' : ''}
            />
            {errors.date && <span className="field-error">{errors.date}</span>}
          </div>

          <div className="form-field">
            <label>Description</label>
            <input
              type="text"
              value={form.description}
              onChange={e => handleChange('description', e.target.value)}
              placeholder="e.g. Client payment, Office supplies"
              className={errors.description ? 'input-error' : ''}
            />
            {errors.description && <span className="field-error">{errors.description}</span>}
          </div>

          <div className="form-field">
            <label>Amount (₹)</label>
            <input
              type="number"
              value={form.amount}
              onChange={e => handleChange('amount', e.target.value)}
              placeholder="0.00"
              min="0"
              step="0.01"
              className={errors.amount ? 'input-error' : ''}
            />
            {errors.amount && <span className="field-error">{errors.amount}</span>}
          </div>

          {submitError && (
            <div className="submit-error">⚠️ {submitError}</div>
          )}

        </div>

        <div className="modal-footer">
          <button className="btn-cancel" onClick={onClose} disabled={loading}>
            Cancel
          </button>
          <button className="btn-save" onClick={handleSubmit} disabled={loading}>
            {loading ? '⏳ Saving...' : '💾 Save Transaction'}
          </button>
        </div>

      </div>
    </div>
  )
}

export default AddTransactionModal
