import { useState, useRef, useCallback, type DragEvent, type ChangeEvent } from 'react'
import axios from 'axios'                         // kept for reference
import api from '../api'                          // ← JWT-aware instance for Spring Boot calls
import { useAuth } from '../context/AuthContext'

// ─────────────────────────────────────────────────────────────────────────────
//  Interfaces
// ─────────────────────────────────────────────────────────────────────────────
interface InvoiceUploadProps {
  companyId: number
}

interface OcrResult {
  vendor: string | null
  date: string | null
  invoice_no: string | null
  total: number | null
  currency: string
  raw_text: string
  note?: string
}

interface EditForm {
  description: string
  date: string
  amount: string
  type: 'expense' | 'income'
}

type Stage = 'idle' | 'uploading' | 'reviewing' | 'saving' | 'saved' | 'error'

// ─────────────────────────────────────────────────────────────────────────────
//  Helpers
// ─────────────────────────────────────────────────────────────────────────────
const ACCEPTED = ['image/png', 'image/jpeg', 'image/jpg']
const TODAY = new Date().toISOString().split('T')[0]

function buildDescription(vendor: string | null, invoiceNo: string | null): string {
  if (vendor && invoiceNo) return `Invoice from ${vendor} #${invoiceNo}`
  if (vendor) return `Invoice from ${vendor}`
  if (invoiceNo) return `Invoice #${invoiceNo}`
  return 'Invoice payment'
}

function normaliseDate(raw: string | null): string {
  if (!raw) return TODAY
  if (/^\d{4}-\d{2}-\d{2}$/.test(raw)) return raw
  const dmy = raw.match(/^(\d{1,2})[/\-.](\d{1,2})[/\-.](\d{4})$/)
  if (dmy) return `${dmy[3]}-${dmy[2].padStart(2, '0')}-${dmy[1].padStart(2, '0')}`
  const ymd = raw.match(/^(\d{4})[/-](\d{2})[/-](\d{2})$/)
  if (ymd) return `${ymd[1]}-${ymd[2]}-${ymd[3]}`
  return TODAY
}

// ─────────────────────────────────────────────────────────────────────────────
//  Sub-components
// ─────────────────────────────────────────────────────────────────────────────
function DropZone({
  onFile, dragOver, onDragOver, onDragLeave, onDrop,
}: {
  onFile: (f: File) => void
  dragOver: boolean
  onDragOver: (e: DragEvent<HTMLDivElement>) => void
  onDragLeave: () => void
  onDrop: (e: DragEvent<HTMLDivElement>) => void
}) {
  const inputRef = useRef<HTMLInputElement>(null)
  const handleChange = (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (file) onFile(file)
  }
  return (
    <div
      className={`inv-dropzone ${dragOver ? 'inv-dropzone--over' : ''}`}
      onDragOver={onDragOver}
      onDragLeave={onDragLeave}
      onDrop={onDrop}
      onClick={() => inputRef.current?.click()}
      role="button"
      tabIndex={0}
      onKeyDown={e => e.key === 'Enter' && inputRef.current?.click()}
    >
      <input
        ref={inputRef}
        type="file"
        accept=".png,.jpg,.jpeg"
        style={{ display: 'none' }}
        onChange={handleChange}
      />
      <div className="inv-drop-icon">🧾</div>
      <p className="inv-drop-title">Drop invoice image here</p>
      <p className="inv-drop-sub">or click to browse</p>
      <p className="inv-drop-hint">PNG, JPG, JPEG supported</p>
    </div>
  )
}

function NoteBar({ note }: { note: string }) {
  return (
    <div className="inv-note">
      <span className="inv-note-icon">ℹ️</span>
      <p>{note}</p>
    </div>
  )
}

function FieldRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="inv-field-row">
      <span className="inv-field-label">{label}</span>
      <span className="inv-field-value">{value}</span>
    </div>
  )
}

// ─────────────────────────────────────────────────────────────────────────────
//  Main component
// ─────────────────────────────────────────────────────────────────────────────
function InvoiceUpload({ companyId }: InvoiceUploadProps) {
  const { isFree } = useAuth()
  const [stage, setStage] = useState<Stage>('idle')
  const [dragOver, setDragOver] = useState(false)
  const [ocr, setOcr] = useState<OcrResult | null>(null)
  const [fileName, setFileName] = useState('')
  const [errorMsg, setErrorMsg] = useState('')
  const [form, setForm] = useState<EditForm>({
    description: '', date: TODAY, amount: '', type: 'expense',
  })

  // ── Upload & OCR — calls Python (port 5000), no JWT needed ────────────────
  const uploadFile = useCallback(async (file: File) => {
    if (!ACCEPTED.includes(file.type)) {
      setErrorMsg(`Unsupported file type "${file.type}". Please upload PNG or JPG.`)
      setStage('error')
      return
    }
    if (file.size > 10 * 1024 * 1024) {
      setErrorMsg('File is too large (max 10 MB). Please compress the image.')
      setStage('error')
      return
    }
    setFileName(file.name)
    setStage('uploading')
    setErrorMsg('')

    try {
      const formData = new FormData()
      formData.append('file', file)

      // Use the JWT-aware 'api' instance to call the new backend proxy
      const res = await api.post<OcrResult>(
        `/api/v1/ai/ocr`,
        formData,
        { headers: { 'Content-Type': 'multipart/form-data' } }
      )
      const result = res.data
      setOcr(result)
      setForm({
        description: buildDescription(result.vendor, result.invoice_no),
        date: normaliseDate(result.date),
        amount: result.total != null ? String(result.total) : '',
        type: 'expense',
      })
      setStage('reviewing')
    } catch (e) {
      const msg = axios.isAxiosError(e)
        ? (e.response?.data as { message?: string })?.message ??
        (e.response?.data as { error?: string })?.error ?? e.message
        : String(e)
      setErrorMsg(`OCR failed: ${msg}. Make sure the Python AI server is running on port 5001.`)
      setStage('error')
    }
  }, [])

  // ── Drag-drop handlers ────────────────────────────────────────────────────
  const handleDragOver = (e: DragEvent<HTMLDivElement>) => { e.preventDefault(); setDragOver(true) }
  const handleDragLeave = () => setDragOver(false)
  const handleDrop = (e: DragEvent<HTMLDivElement>) => {
    e.preventDefault()
    setDragOver(false)
    const file = e.dataTransfer.files[0]
    if (file) void uploadFile(file)
  }

  // ── Save as transaction — calls Spring Boot via JWT-aware api instance ─────
  const saveTransaction = async () => {
    if (!form.description.trim()) return
    const raw = parseFloat(form.amount)
    if (isNaN(raw) || raw <= 0) {
      setErrorMsg('Please enter a valid positive amount.')
      return
    }
    const amount = form.type === 'expense' ? -raw : raw
    setStage('saving')
    try {
      await api.post(`/api/v1/${companyId}/transactions`, {
        date: form.date,
        description: form.description,
        amount,
      })
      setStage('saved')
    } catch (e) {
      const msg = axios.isAxiosError(e)
        ? (e.response?.data as { message?: string })?.message ?? e.message
        : String(e)
      setErrorMsg(`Save failed: ${msg}. Is Spring Boot running on port 8080?`)
      setStage('error')
    }
  }

  const reset = () => {
    setStage('idle')
    setOcr(null)
    setFileName('')
    setErrorMsg('')
    setForm({ description: '', date: TODAY, amount: '', type: 'expense' })
  }

  const setField = <K extends keyof EditForm>(key: K, val: EditForm[K]) =>
    setForm(prev => ({ ...prev, [key]: val }))

  // ── Render ────────────────────────────────────────────────────────────────
  if (isFree) return (
    <div className="upgrade-gate">
      <div style={{ fontSize: 56 }}>🧾</div>
      <h2>Invoice Scanner requires Trial or Pro</h2>
      <p>Access AI-powered invoice extraction and automated data entry.</p>
      <a href="/subscription" className="btn-primary">Upgrade Now →</a>
    </div>
  )

  return (
    <div className="inv-container premium-invoice-wrapper">

      <div className="inv-header">
        <div className="inv-header-text">
          <h2 className="inv-title">🧾 Invoice Scanner</h2>
          <p className="inv-subtitle">Upload an invoice image — AI extracts the details automatically</p>
        </div>
        {stage !== 'idle' && (
          <button className="inv-btn-ghost" onClick={reset}>← Upload another</button>
        )}
      </div>

      {stage === 'idle' && (
        <DropZone
          onFile={f => void uploadFile(f)}
          dragOver={dragOver}
          onDragOver={handleDragOver}
          onDragLeave={handleDragLeave}
          onDrop={handleDrop}
        />
      )}

      {stage === 'uploading' && (
        <div className="inv-state-card">
          <div className="inv-spinner" />
          <p className="inv-state-title">Scanning invoice…</p>
          <p className="inv-state-sub">{fileName}</p>
        </div>
      )}

      {stage === 'reviewing' && ocr && (
        <div className="premium-inv-review">
          {/* Left Pane: Extracted Info */}
          <div className="premium-inv-extracted">
            <div className="premium-section-header">
              <span className="premium-icon">📷</span> Extracted Details
            </div>
            <div className="premium-inv-fields">
              <FieldRow label="Vendor" value={ocr.vendor ?? '—'} />
              <FieldRow label="Invoice #" value={ocr.invoice_no ?? '—'} />
              <FieldRow label="Date found" value={ocr.date ?? '—'} />
              <FieldRow label="Amount"
                value={ocr.total != null ? `${ocr.currency} ${ocr.total.toLocaleString('en-IN')}` : '—'} />
            </div>
            {ocr.note && <NoteBar note={ocr.note} />}
          </div>

          {/* Right Pane: Review & Save */}
          <div className="premium-inv-form">
            <div className="premium-section-header">
              <span className="premium-icon">✏️</span> Review & Save
            </div>

            <div className="premium-form-group">
              <label className="premium-label">Description</label>
              <input
                className="premium-input"
                value={form.description}
                onChange={e => setField('description', e.target.value)}
                placeholder="Invoice description"
              />
            </div>

            <div className="premium-form-row">
              <div className="premium-form-group">
                <label className="premium-label">Date</label>
                <input
                  className="premium-input"
                  type="date"
                  value={form.date}
                  onChange={e => setField('date', e.target.value)}
                />
              </div>
              <div className="premium-form-group">
                <label className="premium-label">Amount (₹)</label>
                <input
                  className="premium-input"
                  type="number"
                  min="0"
                  step="0.01"
                  value={form.amount}
                  onChange={e => setField('amount', e.target.value)}
                  placeholder="0.00"
                />
              </div>
            </div>

            <div className="premium-form-group">
              <label className="premium-label">Type</label>
              <div className="premium-type-toggle">
                <button
                  className={`premium-type-btn ${form.type === 'expense' ? 'expense-active' : ''}`}
                  onClick={() => setField('type', 'expense')}
                >
                  📉 Expense
                </button>
                <button
                  className={`premium-type-btn ${form.type === 'income' ? 'income-active' : ''}`}
                  onClick={() => setField('type', 'income')}
                >
                  📈 Income
                </button>
              </div>
            </div>

            {errorMsg && <div className="premium-error">{errorMsg}</div>}

            <button
              className="premium-btn-save"
              onClick={() => void saveTransaction()}
              disabled={!form.description.trim() || !form.amount}
            >
              💾 Save Transaction
            </button>
          </div>
        </div>
      )}

      {stage === 'saving' && (
        <div className="inv-state-card">
          <div className="inv-spinner" />
          <p className="inv-state-title">Saving transaction…</p>
        </div>
      )}

      {stage === 'saved' && (
        <div className="inv-state-card inv-state-card--success">
          <div className="inv-success-icon">✅</div>
          <p className="inv-state-title">Transaction saved!</p>
          <p className="inv-state-sub">
            {form.type === 'expense' ? '−' : '+'}₹{parseFloat(form.amount).toLocaleString('en-IN')}
            {' · '}{form.description}
          </p>
          <button className="inv-btn-primary" onClick={reset}>
            Upload Another Invoice
          </button>
        </div>
      )}

      {stage === 'error' && (
        <div className="inv-state-card inv-state-card--error">
          <div className="inv-error-icon">❌</div>
          <p className="inv-state-title">Something went wrong</p>
          <p className="inv-state-sub">{errorMsg}</p>
          <button className="inv-btn-primary" onClick={reset}>
            Try Again
          </button>
        </div>
      )}

    </div>
  )
}

export default InvoiceUpload
