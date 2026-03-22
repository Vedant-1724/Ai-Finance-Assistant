import { useState, useRef, useCallback, type DragEvent, type ChangeEvent } from 'react'
import axios from 'axios'
import api from '../api'
import { useAuth } from '../context/AuthContext'

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
  warnings?: string[]
  ocr_confidence?: number
  reviewRequired?: boolean
}

interface EditForm {
  description: string
  date: string
  amount: string
  type: 'expense' | 'income'
}

type Stage = 'idle' | 'uploading' | 'reviewing' | 'saving' | 'saved' | 'error'

const ACCEPTED_EXTENSIONS = new Set(['png', 'jpg', 'jpeg', 'pdf', 'webp', 'bmp', 'tiff', 'heic', 'heif'])
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
        accept=".pdf,.png,.jpg,.jpeg,.webp,.bmp,.tiff,.heic,.heif"
        style={{ display: 'none' }}
        onChange={handleChange}
      />
      <div className="inv-drop-icon">🧾</div>
      <p className="inv-drop-title">Drop invoice here</p>
      <p className="inv-drop-sub">or click to browse</p>
      <p className="inv-drop-hint">PDF, PNG, JPG, WEBP, TIFF, HEIC supported</p>
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

function InvoiceUpload({ companyId }: InvoiceUploadProps) {
  const { isFree, capabilities } = useAuth()
  const [stage, setStage] = useState<Stage>('idle')
  const [dragOver, setDragOver] = useState(false)
  const [ocr, setOcr] = useState<OcrResult | null>(null)
  const [fileName, setFileName] = useState('')
  const [errorMsg, setErrorMsg] = useState('')
  const [form, setForm] = useState<EditForm>({
    description: '', date: TODAY, amount: '', type: 'expense',
  })

  const uploadFile = useCallback(async (file: File) => {
    const ext = file.name.split('.').pop()?.toLowerCase() ?? ''
    if (!ACCEPTED_EXTENSIONS.has(ext)) {
      setErrorMsg(`Unsupported file type ".${ext}". Please upload a PDF or supported image format.`)
      setStage('error')
      return
    }
    if (file.size > 10 * 1024 * 1024) {
      setErrorMsg('File is too large (max 10 MB). Please compress the image or PDF.')
      setStage('error')
      return
    }
    setFileName(file.name)
    setStage('uploading')
    setErrorMsg('')

    try {
      const formData = new FormData()
      formData.append('file', file)

      const res = await api.post<OcrResult>(
        `/api/v1/ai/ocr`,
        formData,
        { headers: { 'Content-Type': 'multipart/form-data' } }
      )
      const result = res.data
      setOcr(result)
      setForm({
        description: buildDescription(result.vendor, result.invoice_no),
        date: result.date && /^\d{4}-\d{2}-\d{2}$/.test(result.date) ? result.date : normaliseDate(result.date),
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

  const handleDragOver = (e: DragEvent<HTMLDivElement>) => { e.preventDefault(); setDragOver(true) }
  const handleDragLeave = () => setDragOver(false)
  const handleDrop = (e: DragEvent<HTMLDivElement>) => {
    e.preventDefault()
    setDragOver(false)
    const file = e.dataTransfer.files[0]
    if (file) void uploadFile(file)
  }

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
        ? (e.response?.data as { message?: string; error?: string })?.message ?? (e.response?.data as { message?: string; error?: string })?.error ?? e.message
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

  if (!capabilities.canEditFinance) return (
    <div className="upgrade-gate">
      <div style={{ fontSize: 56 }}>🧾</div>
      <h2>Invoice Scanner is not available for viewer access</h2>
      <p>Editors and owners can scan invoices and save them as transactions.</p>
    </div>
  )

  if (isFree) return (
    <div className="upgrade-gate">
      <div style={{ fontSize: 56 }}>🧾</div>
      <h2>Invoice Scanner requires Trial or Pro</h2>
      <p>Access AI-powered invoice extraction and automated data entry.</p>
      <a href="/subscription" className="btn-liquid-glass" style={{ display: 'inline-block' }}><span>Upgrade Now &rarr;</span></a>
    </div>
  )

  return (
    <div className="inv-container premium-invoice-wrapper">
      <div className="inv-header">
        <div className="inv-header-text">
          <h2 className="inv-title">🧾 Invoice Scanner</h2>
          <p className="inv-subtitle">Upload an invoice image or PDF and FinanceAI extracts the details automatically</p>
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
        <div className="inv-review-grid">
          <div className="inv-card">
            <h3 className="inv-card-title">AI Extraction</h3>
            <FieldRow label="Vendor" value={ocr.vendor ?? 'Not detected'} />
            <FieldRow label="Invoice No." value={ocr.invoice_no ?? 'Not detected'} />
            <FieldRow label="Date" value={ocr.date ?? 'Not detected'} />
            <FieldRow label="Total" value={ocr.total != null ? `${ocr.currency} ${ocr.total.toLocaleString('en-IN')}` : 'Not detected'} />
            <FieldRow label="Confidence" value={`${Math.round((ocr.ocr_confidence ?? 0) * 100)}%`} />
            {ocr.reviewRequired ? <NoteBar note="This invoice needs manual review before saving." /> : null}
            {ocr.note && <NoteBar note={ocr.note} />}
            {ocr.warnings?.length ? (
              <div className="inv-note" style={{ marginTop: 12 }}>
                <span className="inv-note-icon">⚠️</span>
                <div>
                  {ocr.warnings.map(warning => <p key={warning} style={{ margin: 0 }}>{warning}</p>)}
                </div>
              </div>
            ) : null}
          </div>

          <div className="inv-card">
            <h3 className="inv-card-title">Review before saving</h3>
            <label className="inv-label">Description</label>
            <input className="inv-input" value={form.description} onChange={e => setField('description', e.target.value)} />

            <label className="inv-label">Date</label>
            <input className="inv-input" type="date" value={form.date} onChange={e => setField('date', e.target.value)} />

            <label className="inv-label">Amount</label>
            <input className="inv-input" value={form.amount} onChange={e => setField('amount', e.target.value)} />

            <label className="inv-label">Type</label>
            <select className="inv-input" value={form.type} onChange={e => setField('type', e.target.value as EditForm['type'])}>
              <option value="expense">Expense</option>
              <option value="income">Income</option>
            </select>

            <div className="inv-actions">
              <button className="inv-btn-ghost" onClick={reset}>Cancel</button>
              <button className="inv-btn-primary" onClick={() => void saveTransaction()}>Save Transaction</button>
            </div>
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
          <p className="inv-state-title">Saved successfully</p>
          <p className="inv-state-sub">The transaction has been added to your books.</p>
        </div>
      )}

      {stage === 'error' && (
        <div className="inv-state-card inv-state-card--error">
          <p className="inv-state-title">Something went wrong</p>
          <p className="inv-state-sub">{errorMsg}</p>
        </div>
      )}
    </div>
  )
}

export default InvoiceUpload

