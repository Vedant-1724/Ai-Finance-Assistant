import { useState, useRef } from 'react'
import api from '../api'

// ── Types ─────────────────────────────────────────────────────────────────────
interface ParsedTransaction {
  date:        string
  description: string
  amount:      number
  source:      string
  selected:    boolean   // UI-only: user can deselect rows before importing
}

interface ParseResult {
  transactions: Omit<ParsedTransaction, 'selected'>[]
  total_found:  number
  skipped:      number
  source:       string
  warning?:     string
  error?:       string
}

interface StatementImportProps {
  companyId: number
  onImportSuccess: () => void
}

// ── Component ──────────────────────────────────────────────────────────────────
export default function StatementImport({ companyId, onImportSuccess }: StatementImportProps) {
  const fileRef                                       = useRef<HTMLInputElement>(null)
  const [step, setStep]                               = useState<'upload' | 'preview' | 'done'>('upload')
  const [parsing, setParsing]                         = useState(false)
  const [importing, setImporting]                     = useState(false)
  const [parseResult, setParseResult]                 = useState<ParseResult | null>(null)
  const [transactions, setTransactions]               = useState<ParsedTransaction[]>([])
  const [parseError, setParseError]                   = useState<string | null>(null)
  const [importResult, setImportResult]               = useState<{ imported: number } | null>(null)
  const [dragOver, setDragOver]                       = useState(false)

  // ── Upload & Parse ───────────────────────────────────────────────────────────
  const handleFile = async (file: File) => {
    setParseError(null)
    setParsing(true)
    setStep('upload')

    const formData = new FormData()
    formData.append('file', file)

    try {
      const res = await fetch('http://localhost:5000/parse-statement', {
        method: 'POST',
        body: formData,
      })
      const data: ParseResult = await res.json()

      if (data.error) {
        setParseError(data.error)
        return
      }

      const withSelection: ParsedTransaction[] = data.transactions.map(t => ({
        ...t,
        selected: true,
      }))

      setParseResult(data)
      setTransactions(withSelection)
      setStep('preview')
    } catch {
      setParseError('Could not connect to AI service. Make sure Python is running on port 5000.')
    } finally {
      setParsing(false)
    }
  }

  const handleDrop = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault()
    setDragOver(false)
    const file = e.dataTransfer.files[0]
    if (file) handleFile(file)
  }

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (file) handleFile(file)
  }

  // ── Selection helpers ────────────────────────────────────────────────────────
  const toggleRow     = (i: number) =>
    setTransactions(prev => prev.map((t, idx) => idx === i ? { ...t, selected: !t.selected } : t))

  const selectAll     = () => setTransactions(prev => prev.map(t => ({ ...t, selected: true  })))
  const deselectAll   = () => setTransactions(prev => prev.map(t => ({ ...t, selected: false })))
  const selectedCount = transactions.filter(t => t.selected).length

  // ── Import ───────────────────────────────────────────────────────────────────
  const handleImport = async () => {
    const toImport = transactions.filter(t => t.selected)
    if (toImport.length === 0) return

    setImporting(true)
    try {
      const res = await api.post(`/api/v1/${companyId}/transactions/import`, {
        transactions: toImport.map(({ date, description, amount, source }) => ({
          date, description, amount, source,
        })),
      })
      setImportResult({ imported: res.data.imported })
      setStep('done')
      onImportSuccess()
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })
        ?.response?.data?.message ?? 'Import failed. Please try again.'
      setParseError(msg)
    } finally {
      setImporting(false)
    }
  }

  const reset = () => {
    setStep('upload')
    setParseResult(null)
    setTransactions([])
    setParseError(null)
    setImportResult(null)
    if (fileRef.current) fileRef.current.value = ''
  }

  // ─────────────────────────────────────────────────────────────────────────────
  //  RENDER
  // ─────────────────────────────────────────────────────────────────────────────
  return (
    <div className="statement-import">

      {/* ── Header ── */}
      <div className="import-header">
        <h2>📥 Import Bank Statement</h2>
        <p className="import-subtitle">
          Upload your UPI history, bank statement CSV, or screenshot.
          <strong> Sensitive information (account numbers, IFSC, card numbers) is
          automatically removed — only transaction details are kept.</strong>
        </p>

        {/* Privacy Badge */}
        <div className="privacy-badge">
          <span className="privacy-icon">🔒</span>
          <span>Privacy Protected: Account numbers, IFSC codes, and card numbers
          are redacted before any data is processed or stored.</span>
        </div>
      </div>

      {/* ── Step 1: Upload ── */}
      {step === 'upload' && (
        <div className="upload-section">
          <div
            className={`dropzone ${dragOver ? 'drag-over' : ''}`}
            onDragOver={e => { e.preventDefault(); setDragOver(true)  }}
            onDragLeave={() => setDragOver(false)}
            onDrop={handleDrop}
            onClick={() => fileRef.current?.click()}
          >
            {parsing ? (
              <div className="parsing-indicator">
                <div className="spinner" />
                <p>Parsing & redacting sensitive data…</p>
              </div>
            ) : (
              <>
                <div className="dropzone-icon">📂</div>
                <p className="dropzone-title">Drop your statement here, or click to browse</p>
                <p className="dropzone-hint">Supports CSV, PDF, PNG, JPG · Max 10MB</p>
                <div className="supported-sources">
                  <span className="source-tag">🏦 HDFC</span>
                  <span className="source-tag">🏦 SBI</span>
                  <span className="source-tag">🏦 ICICI</span>
                  <span className="source-tag">🏦 Axis</span>
                  <span className="source-tag">📱 PhonePe</span>
                  <span className="source-tag">📱 GPay</span>
                  <span className="source-tag">📱 Paytm</span>
                  <span className="source-tag">📸 Screenshots</span>
                </div>
              </>
            )}
          </div>
          <input
            ref={fileRef}
            type="file"
            accept=".csv,.pdf,.png,.jpg,.jpeg,.webp,.bmp,.tiff"
            style={{ display: 'none' }}
            onChange={handleInputChange}
          />

          {parseError && (
            <div className="parse-error">❌ {parseError}</div>
          )}

          {/* What gets redacted info box */}
          <div className="redaction-info">
            <h4>🛡️ What We Automatically Remove</h4>
            <div className="redaction-grid">
              <div className="redaction-item">
                <span className="redact-icon">🚫</span>
                <div>
                  <strong>Account Numbers</strong>
                  <p>Shown as XXXX1234</p>
                </div>
              </div>
              <div className="redaction-item">
                <span className="redact-icon">🚫</span>
                <div>
                  <strong>Card Numbers</strong>
                  <p>Shown as XXXX XXXX XXXX 5678</p>
                </div>
              </div>
              <div className="redaction-item">
                <span className="redact-icon">🚫</span>
                <div>
                  <strong>IFSC Codes</strong>
                  <p>Completely removed</p>
                </div>
              </div>
              <div className="redaction-item">
                <span className="redact-icon">🚫</span>
                <div>
                  <strong>Personal UPI IDs</strong>
                  <p>Phone-based UPIs redacted</p>
                </div>
              </div>
              <div className="redaction-item">
                <span className="redact-icon">🚫</span>
                <div>
                  <strong>Mobile Numbers</strong>
                  <p>Shown as XXXXXX1234</p>
                </div>
              </div>
              <div className="redaction-item">
                <span className="redact-icon">🚫</span>
                <div>
                  <strong>PAN Numbers</strong>
                  <p>Completely removed</p>
                </div>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* ── Step 2: Preview ── */}
      {step === 'preview' && parseResult && (
        <div className="preview-section">
          {/* Parse summary */}
          <div className="parse-summary">
            <div className="summary-stat">
              <span className="stat-num">{parseResult.total_found}</span>
              <span className="stat-label">Transactions Found</span>
            </div>
            <div className="summary-stat">
              <span className="stat-num">{parseResult.skipped}</span>
              <span className="stat-label">Rows Skipped</span>
            </div>
            <div className="summary-stat">
              <span className="stat-num source-badge">{parseResult.source}</span>
              <span className="stat-label">Source Type</span>
            </div>
            <div className="summary-stat">
              <span className="stat-num selected-count">{selectedCount}</span>
              <span className="stat-label">Selected to Import</span>
            </div>
          </div>

          {parseResult.warning && (
            <div className="parse-warning">⚠️ {parseResult.warning}</div>
          )}

          <div className="privacy-confirm-badge">
            ✅ All sensitive data has been redacted. Only transaction details shown below.
          </div>

          {/* Bulk selection controls */}
          <div className="preview-controls">
            <div className="select-controls">
              <button className="btn-select-all"   onClick={selectAll}>   ☑ Select All   </button>
              <button className="btn-deselect-all" onClick={deselectAll}> ☐ Deselect All </button>
            </div>
            <div className="import-actions">
              <button className="btn-cancel-import" onClick={reset}>← Back</button>
              <button
                className="btn-confirm-import"
                onClick={handleImport}
                disabled={importing || selectedCount === 0}
              >
                {importing
                  ? '⏳ Importing…'
                  : `✅ Import ${selectedCount} Transaction${selectedCount !== 1 ? 's' : ''}`}
              </button>
            </div>
          </div>

          {/* Transactions table */}
          <div className="preview-table-wrapper">
            <table className="preview-table">
              <thead>
                <tr>
                  <th style={{ width: 40 }}>✓</th>
                  <th>Date</th>
                  <th>Description</th>
                  <th>Amount</th>
                  <th>Type</th>
                </tr>
              </thead>
              <tbody>
                {transactions.map((txn, i) => (
                  <tr
                    key={i}
                    className={txn.selected ? '' : 'row-deselected'}
                    onClick={() => toggleRow(i)}
                  >
                    <td>
                      <input
                        type="checkbox"
                        checked={txn.selected}
                        onChange={() => toggleRow(i)}
                        onClick={e => e.stopPropagation()}
                      />
                    </td>
                    <td className="date-cell">{txn.date}</td>
                    <td className="desc-cell">{txn.description}</td>
                    <td className={txn.amount >= 0 ? 'positive' : 'negative'}>
                      {txn.amount >= 0 ? '+' : '−'}₹{Math.abs(txn.amount).toLocaleString('en-IN')}
                    </td>
                    <td>
                      <span className={`type-badge ${txn.amount >= 0 ? 'income' : 'expense'}`}>
                        {txn.amount >= 0 ? '📈 Income' : '📉 Expense'}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {parseError && (
            <div className="parse-error">❌ {parseError}</div>
          )}
        </div>
      )}

      {/* ── Step 3: Done ── */}
      {step === 'done' && importResult && (
        <div className="done-section">
          <div className="success-graphic">
            <div className="success-circle">✅</div>
          </div>
          <h3>Import Complete!</h3>
          <p className="done-count">
            <strong>{importResult.imported}</strong> transactions imported successfully.
          </p>
          <p className="done-note">
            Your dashboard and P&L report have been updated. Anomaly detection
            will analyze the new transactions in the background.
          </p>
          <div className="done-actions">
            <button className="btn-import-more" onClick={reset}>
              📥 Import Another Statement
            </button>
          </div>
        </div>
      )}
    </div>
  )
}