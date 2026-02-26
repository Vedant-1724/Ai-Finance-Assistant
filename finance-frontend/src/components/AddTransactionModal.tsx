import { useState } from 'react'
import axios from 'axios'

interface AddTransactionModalProps {
  companyId: number
  onClose: () => void
  onSuccess: () => void
}

interface FormData {
  date: string
  description: string
  amount: string
  type: 'income' | 'expense'
}

interface FormErrors {
  date?: string
  description?: string
  amount?: string
}

function AddTransactionModal({ companyId, onClose, onSuccess }: AddTransactionModalProps) {
  const today = new Date().toISOString().split('T')[0]

  const [form, setForm] = useState<FormData>({
    date: today,
    description: '',
    amount: '',
    type: 'income',
  })

  const [errors, setErrors] = useState<FormErrors>({})
  const [loading, setLoading] = useState(false)
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
        : Math.abs(Number(form.amount))

    try {
      await axios.post(
        `http://localhost:8080/api/v1/${companyId}/transactions`,
        {
          date: form.date,
          amount: finalAmount,
          description: form.description.trim(),
        }
      )
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

        {/* Header */}
        <div className="modal-header">
          <h3>➕ Add Transaction</h3>
          <button className="modal-close" onClick={onClose} aria-label="Close">✕</button>
        </div>

        {/* Type Toggle */}
        <div className="modal-type-toggle">
          <button
            className={`toggle-btn income-btn ${form.type === 'income' ? 'active' : ''}`}
            onClick={() => handleChange('type', 'income')}
            type="button"
          >
            📈 Income
          </button>
          <button
            className={`toggle-btn expense-btn ${form.type === 'expense' ? 'active' : ''}`}
            onClick={() => handleChange('type', 'expense')}
            type="button"
          >
            📉 Expense
          </button>
        </div>

        {/* Form Fields */}
        <div className="modal-form">

          {/* Date */}
          <div className="form-group">
            <label htmlFor="txn-date">Date</label>
            <input
              id="txn-date"
              type="date"
              value={form.date}
              onChange={e => handleChange('date', e.target.value)}
              className={errors.date ? 'input-error' : ''}
            />
            {errors.date && <span className="error-msg">{errors.date}</span>}
          </div>

          {/* Description */}
          <div className="form-group">
            <label htmlFor="txn-desc">Description</label>
            <input
              id="txn-desc"
              type="text"
              value={form.description}
              onChange={e => handleChange('description', e.target.value)}
              placeholder="e.g. Client Payment, Office Rent..."
              className={errors.description ? 'input-error' : ''}
              maxLength={512}
            />
            {errors.description && <span className="error-msg">{errors.description}</span>}
          </div>

          {/* Amount */}
          <div className="form-group">
            <label htmlFor="txn-amount">
              Amount (₹)
              <span className={`amount-hint ${form.type}`}>
                {form.type === 'income' ? '  will be saved as +positive' : '  will be saved as −negative'}
              </span>
            </label>
            <div className="amount-input-wrapper">
              <span className={`amount-prefix ${form.type}`}>
                {form.type === 'income' ? '+₹' : '−₹'}
              </span>
              <input
                id="txn-amount"
                type="number"
                value={form.amount}
                onChange={e => handleChange('amount', e.target.value)}
                placeholder="0.00"
                min="0"
                step="0.01"
                className={`amount-input ${errors.amount ? 'input-error' : ''}`}
              />
            </div>
            {errors.amount && <span className="error-msg">{errors.amount}</span>}
          </div>

          {/* Preview */}
          {form.amount && !isNaN(Number(form.amount)) && Number(form.amount) > 0 && (
            <div className="txn-preview">
              <span>Preview:</span>
              <span className={form.type === 'income' ? 'positive' : 'negative'}>
                {form.type === 'income' ? '+' : '−'}₹{Number(form.amount).toLocaleString('en-IN')}
              </span>
              <span className="preview-desc">
                {form.description || '—'} &nbsp;·&nbsp; {form.date || 'no date'}
              </span>
            </div>
          )}

          {/* Submit Error */}
          {submitError && (
            <div className="submit-error">⚠️ {submitError}</div>
          )}

          {/* Actions */}
          <div className="modal-actions">
            <button
              className="btn-cancel"
              onClick={onClose}
              disabled={loading}
              type="button"
            >
              Cancel
            </button>
            <button
              className={`btn-submit ${form.type}`}
              onClick={handleSubmit}
              disabled={loading}
              type="button"
            >
              {loading
                ? '⏳ Saving...'
                : form.type === 'income'
                  ? '✓ Add Income'
                  : '✓ Add Expense'}
            </button>
          </div>
        </div>

      </div>
    </div>
  )
}

export default AddTransactionModal
