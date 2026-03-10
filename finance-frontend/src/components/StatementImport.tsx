import { useRef, useState } from 'react'
import api from '../api'

interface ParsedTransaction {
  date: string
  description: string
  amount: number
  source: string
  selected: boolean
}

interface ParseResult {
  transactions: Omit<ParsedTransaction, 'selected'>[]
  total_found: number
  skipped: number
  source: string
  warning?: string
  error?: string
}

interface StatementImportProps {
  companyId: number
  onImportSuccess: () => void
}

interface BankSyncStatus {
  provider: string
  status: 'NOT_STARTED' | 'CONSENT_REQUIRED' | 'CONSENT_GRANTED' | 'SYNCED' | 'FAILED' | 'EXPIRED'
  consentUrl?: string | null
  consentId?: string | null
  mockFallback: boolean
  message?: string | null
  expiresAt?: string | null
  lastSyncedAt?: string | null
}

const ALLOWED_EXTENSIONS = new Set(['csv', 'pdf', 'png', 'jpg', 'jpeg', 'webp', 'bmp', 'tiff'])
const MAX_FILE_SIZE_MB = 10
const MAX_FILE_SIZE = MAX_FILE_SIZE_MB * 1024 * 1024

export default function StatementImport({ companyId, onImportSuccess }: StatementImportProps) {
  const fileRef = useRef<HTMLInputElement>(null)
  const [step, setStep] = useState<'upload' | 'preview' | 'done'>('upload')
  const [parsing, setParsing] = useState(false)
  const [importing, setImporting] = useState(false)
  const [parseResult, setParseResult] = useState<ParseResult | null>(null)
  const [transactions, setTransactions] = useState<ParsedTransaction[]>([])
  const [parseError, setParseError] = useState<string | null>(null)
  const [importResult, setImportResult] = useState<{ imported: number } | null>(null)
  const [dragOver, setDragOver] = useState(false)
  const [setuSyncing, setSetuSyncing] = useState(false)
  const [setuError, setSetuError] = useState<string | null>(null)
  const [setuNotice, setSetuNotice] = useState<string | null>(null)

  const validateFile = (file: File): string | null => {
    if (file.size > MAX_FILE_SIZE) {
      return `File too large. Maximum ${MAX_FILE_SIZE_MB}MB allowed.`
    }
    const ext = file.name.split('.').pop()?.toLowerCase() ?? ''
    if (!ALLOWED_EXTENSIONS.has(ext)) {
      return `Unsupported file type ".${ext}". Please use CSV, PDF, PNG, or JPG.`
    }
    return null
  }

  const handleFile = async (file: File) => {
    setParseError(null)

    const validationError = validateFile(file)
    if (validationError) {
      setParseError(validationError)
      return
    }

    setParsing(true)
    setStep('upload')

    const formData = new FormData()
    formData.append('file', file)

    try {
      const res = await api.post(
        `/api/v1/${companyId}/parse-statement`,
        formData,
        { headers: { 'Content-Type': 'multipart/form-data' } }
      )
      const data: ParseResult = res.data

      if (data.error) {
        setParseError(data.error)
        return
      }

      if (!data.transactions || data.transactions.length === 0) {
        setParseError(
          data.warning
            ? `No transactions found. ${data.warning}`
            : 'No transactions could be detected in this file. Please check the format or try a different file.'
        )
        return
      }

      const withSelection: ParsedTransaction[] = data.transactions.map(t => ({
        ...t,
        selected: true,
      }))

      setParseResult(data)
      setTransactions(withSelection)
      setStep('preview')
    } catch (err: unknown) {
      const status = (err as { response?: { status?: number } })?.response?.status
      if (status === 401 || status === 403) {
        setParseError('Authentication error. Please log in again.')
      } else if (status === 503) {
        setParseError('AI service is unavailable. Please ensure the Python service is running.')
      } else {
        const msg = (err as { response?: { data?: { error?: string } } })?.response?.data?.error
        setParseError(msg ?? 'Failed to parse statement. Please try a different file.')
      }
    } finally {
      setParsing(false)
    }
  }

  const handleDrop = (event: React.DragEvent<HTMLDivElement>) => {
    event.preventDefault()
    setDragOver(false)
    const file = event.dataTransfer.files[0]
    if (file) {
      void handleFile(file)
    }
  }

  const handleInputChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    if (file) {
      void handleFile(file)
    }
  }

  const toggleRow = (index: number) => {
    setTransactions(prev => prev.map((txn, idx) => idx === index ? { ...txn, selected: !txn.selected } : txn))
  }

  const selectAll = () => setTransactions(prev => prev.map(txn => ({ ...txn, selected: true })))
  const deselectAll = () => setTransactions(prev => prev.map(txn => ({ ...txn, selected: false })))
  const selectedCount = transactions.filter(txn => txn.selected).length

  const handleImport = async () => {
    const toImport = transactions.filter(txn => txn.selected)
    if (toImport.length === 0) {
      return
    }

    setImporting(true)
    try {
      const res = await api.post(`/api/v1/${companyId}/transactions/import`, {
        transactions: toImport.map(({ date, description, amount, source }) => ({
          date,
          description,
          amount,
          source,
        })),
      })
      setImportResult({ imported: res.data.imported })
      setStep('done')
      onImportSuccess()
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message
        ?? 'Import failed. Please try again.'
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
    setSetuError(null)
    setSetuNotice(null)
    if (fileRef.current) {
      fileRef.current.value = ''
    }
  }

  const handleSetuSync = async () => {
    setSetuError(null)
    setSetuNotice(null)
    setSetuSyncing(true)

    try {
      const consentRes = await api.post<BankSyncStatus>(`/api/v1/${companyId}/setu/consent`)
      const consent = consentRes.data

      if (consent.status === 'CONSENT_REQUIRED') {
        if (consent.consentUrl) {
          const popup = window.open(consent.consentUrl, '_blank', 'noopener,noreferrer')
          if (!popup) {
            setSetuError('The bank consent window was blocked. Please allow pop-ups and try again.')
          } else {
            setSetuNotice(consent.message ?? 'Consent window opened. After approval, return here and click Connect Bank again to sync.')
          }
        } else {
          setSetuError(consent.message ?? 'Bank consent is required before syncing.')
        }
        return
      }

      if (consent.message) {
        setSetuNotice(consent.message)
      }

      const syncRes = await api.post(`/api/v1/${companyId}/setu/sync`)
      const rawTransactions = Array.isArray(syncRes.data)
        ? syncRes.data
        : (syncRes.data?.transactions ?? [])

      const fetched: ParsedTransaction[] = rawTransactions.map((txn: any) => ({
        date: String(txn.date),
        description: String(txn.description ?? 'Bank synced transaction'),
        amount: Number(txn.amount),
        source: String(txn.source ?? (consent.mockFallback ? 'Setu AA (Mock)' : 'Setu AA')),
        selected: true,
      }))

      if (fetched.length === 0) {
        setSetuError('No transactions found from the bank sync provider.')
        return
      }

      setTransactions(fetched)
      setParseResult({
        transactions: fetched,
        total_found: fetched.length,
        skipped: 0,
        source: consent.mockFallback ? 'Setu AA (Mock)' : 'Setu AA',
      })
      setStep('preview')
    } catch (err: any) {
      setSetuError(err?.response?.data?.error || err?.response?.data?.message || 'Failed to sync with Setu AA.')
    } finally {
      setSetuSyncing(false)
    }
  }

  return (
    <div className="statement-import">
      <div className="import-header">
        <h2>📥 Import Bank Statement</h2>
        <p className="import-subtitle">
          Upload your UPI history, bank statement CSV, or screenshot. <strong>Sensitive information (account numbers, IFSC, card numbers) is automatically removed — only transaction details are kept.</strong>
        </p>
        <div className="privacy-badge">
          <span className="privacy-icon">🔒</span>
          <span>Privacy Protected: Account numbers, IFSC codes, and card numbers are redacted before any data is processed or stored.</span>
        </div>
      </div>

      {step === 'upload' && (
        <div className="upload-section">
          <div
            className={`dropzone ${dragOver ? 'drag-over' : ''}`}
            onDragOver={event => { event.preventDefault(); setDragOver(true) }}
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
                <p className="dropzone-hint">Supports CSV, PDF, PNG, JPG · Max {MAX_FILE_SIZE_MB}MB</p>
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

          {parseError && <div className="parse-error">❌ {parseError}</div>}

          <div className="redaction-info">
            <h4>🛡️ What We Automatically Remove</h4>
            <div className="redaction-grid">
              {[
                ['Account Numbers', 'Shown as XXXX1234'],
                ['Card Numbers', 'Shown as XXXX XXXX XXXX 5678'],
                ['IFSC Codes', 'Completely removed'],
                ['Personal UPI IDs', 'Phone-based UPIs redacted'],
                ['Mobile Numbers', 'Shown as XXXXXX1234'],
                ['PAN Numbers', 'Completely removed'],
              ].map(([title, desc]) => (
                <div className="redaction-item" key={title}>
                  <span className="redact-icon">🚫</span>
                  <div>
                    <strong>{title}</strong>
                    <p>{desc}</p>
                  </div>
                </div>
              ))}
            </div>
          </div>

          <div className="setu-sync-section" style={{ marginTop: 24, padding: 24, borderRadius: 12, border: '1px solid #1e293b', background: '#0f172a' }}>
            <h3 style={{ marginBottom: 8, fontSize: 18, color: '#f8fafc' }}>
              🇮🇳 Auto-Sync with Bank (Account Aggregator)
            </h3>
            <p style={{ color: '#94a3b8', marginBottom: 12, fontSize: 14 }}>
              Securely connect your bank account via the RBI-regulated Account Aggregator framework (powered by Setu). No login credentials are shared.
            </p>
            <p style={{ color: '#64748b', marginBottom: 16, fontSize: 13 }}>
              If live Setu credentials are not configured in this environment, FinanceAI falls back to safe demo bank data so you can still test the full workflow locally.
            </p>
            <button
              className="btn-primary"
              onClick={() => { void handleSetuSync() }}
              disabled={setuSyncing || parsing}
              style={{ padding: '12px 24px', fontSize: 16, width: '100%', display: 'flex', justifyContent: 'center', gap: 8, alignItems: 'center' }}
            >
              {setuSyncing ? <div className="spinner" style={{ width: 16, height: 16 }} /> : '🔗'}
              {setuSyncing ? 'Connecting to Bank...' : 'Connect Bank via Setu AA'}
            </button>
            {setuNotice && (
              <div style={{ marginTop: 12, padding: 12, borderRadius: 8, background: 'rgba(59,130,246,0.12)', border: '1px solid rgba(59,130,246,0.25)', color: '#bfdbfe', fontSize: 13 }}>
                ℹ️ {setuNotice}
              </div>
            )}
            {setuError && <div className="parse-error" style={{ marginTop: 12 }}>❌ {setuError}</div>}
          </div>
        </div>
      )}

      {step === 'preview' && parseResult && (
        <div className="preview-section">
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

          {parseResult.warning && <div className="parse-warning">⚠️ {parseResult.warning}</div>}

          <div className="privacy-confirm-badge">
            ✅ All sensitive data has been redacted. Only transaction details shown below.
          </div>

          <div className="preview-controls">
            <div className="select-controls">
              <button className="btn-select-all" onClick={selectAll}>☑ Select All</button>
              <button className="btn-deselect-all" onClick={deselectAll}>☐ Deselect All</button>
            </div>
            <div className="import-actions">
              <button className="btn-cancel-import" onClick={reset}>← Back</button>
              <button
                className="btn-confirm-import"
                onClick={() => { void handleImport() }}
                disabled={importing || selectedCount === 0}
              >
                {importing ? '⏳ Importing…' : `✅ Import ${selectedCount} Transaction${selectedCount !== 1 ? 's' : ''}`}
              </button>
            </div>
          </div>

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
                {transactions.map((txn, index) => (
                  <tr
                    key={index}
                    className={txn.selected ? '' : 'row-deselected'}
                    onClick={() => toggleRow(index)}
                  >
                    <td>
                      <input
                        type="checkbox"
                        checked={txn.selected}
                        onChange={() => toggleRow(index)}
                        onClick={event => event.stopPropagation()}
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

          {parseError && <div className="parse-error">❌ {parseError}</div>}
        </div>
      )}

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
            Your dashboard and P&L report have been updated. Anomaly detection will analyze the new transactions in the background.
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
