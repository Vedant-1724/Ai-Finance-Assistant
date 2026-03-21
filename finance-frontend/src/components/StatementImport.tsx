import { useRef, useState } from 'react'
import api from '../api'
import { useAuth } from '../context/AuthContext'

interface ParsedTransaction {
  date: string
  description: string
  amount: number
  source: string
  confidence?: number
  needsReview?: boolean
  normalizedSource?: string
  warnings?: string[]
  selected: boolean
}

interface ParseResult {
  transactions: Omit<ParsedTransaction, 'selected'>[]
  total_found: number
  skipped: number
  source: string
  parseMode?: 'csv' | 'pdf_text' | 'pdf_ocr' | 'image_ocr' | 'llm_fallback' | 'unknown'
  documentConfidence?: number
  warnings?: string[]
  warning?: string
  error?: string
}

interface ImportResult {
  imported: number
  duplicates: number
  skipped?: number
  transactions?: Array<{ id: number; date: string; amount: number; description: string; categoryName: string | null }>
  warnings?: string[]
  errors?: string[]
  message?: string
  mode?: 'statement' | 'bankSync'
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

interface BankSyncResult {
  imported: number
  duplicates: number
  transactions: Array<{ id: number; date: string; amount: number; description: string; categoryName: string | null }>
  message?: string
}

const ALLOWED_EXTENSIONS = new Set(['csv', 'pdf', 'png', 'jpg', 'jpeg', 'webp', 'bmp', 'tiff', 'heic', 'heif'])
const MAX_FILE_SIZE_MB = 10
const MAX_FILE_SIZE = MAX_FILE_SIZE_MB * 1024 * 1024

export default function StatementImport({ companyId, onImportSuccess }: StatementImportProps) {
  const { capabilities } = useAuth()
  const fileRef = useRef<HTMLInputElement>(null)
  const [step, setStep] = useState<'upload' | 'preview' | 'done'>('upload')
  const [parsing, setParsing] = useState(false)
  const [importing, setImporting] = useState(false)
  const [parseResult, setParseResult] = useState<ParseResult | null>(null)
  const [transactions, setTransactions] = useState<ParsedTransaction[]>([])
  const [parseError, setParseError] = useState<string | null>(null)
  const [importResult, setImportResult] = useState<ImportResult | null>(null)
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
      return `Unsupported file type ".${ext}". Please use CSV, PDF, PNG, JPG, TIFF, or HEIC.`
    }
    return null
  }

  const clearTransientState = () => {
    setParseError(null)
    setSetuError(null)
    setSetuNotice(null)
    setImportResult(null)
  }

  const handleFile = async (file: File) => {
    clearTransientState()

    const validationError = validateFile(file)
    if (validationError) {
      setParseError(validationError)
      return
    }

    setParsing(true)
    setStep('upload')
    setParseResult(null)
    setTransactions([])

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
        const warningText = data.warnings?.join(' ') || data.warning
        setParseError(
          warningText
            ? `No transactions found. ${warningText}`
            : 'No transactions could be detected in this file. Please check the format or try a clearer export/scan.'
        )
        return
      }

      const withSelection: ParsedTransaction[] = data.transactions.map(t => ({
        ...t,
        confidence: typeof t.confidence === 'number' ? t.confidence : 0.5,
        needsReview: Boolean(t.needsReview),
        normalizedSource: t.normalizedSource ?? 'IMPORT',
        warnings: Array.isArray(t.warnings) ? t.warnings : [],
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
        const msg = (err as { response?: { data?: { error?: string; message?: string } } })?.response?.data?.error
          ?? (err as { response?: { data?: { error?: string; message?: string } } })?.response?.data?.message
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
  const reviewCount = transactions.filter(txn => txn.needsReview).length

  const handleImport = async () => {
    const toImport = transactions.filter(txn => txn.selected)
    if (toImport.length === 0) {
      return
    }

    setImporting(true)
    setParseError(null)
    try {
      const res = await api.post<ImportResult>(`/api/v1/${companyId}/transactions/import`, {
        transactions: toImport.map(({ date, description, amount, normalizedSource }) => ({
          date,
          description,
          amount,
          source: normalizedSource ?? 'IMPORT',
        })),
      })
      const result = { ...res.data, mode: 'statement' as const }
      setImportResult(result)
      setStep('done')
      if ((result.imported ?? 0) > 0) {
        onImportSuccess()
      }
    } catch (err: unknown) {
      const response = (err as { response?: { data?: { error?: string; message?: string; errors?: string[] } } })?.response?.data
      const msg = response?.message ?? response?.error ?? response?.errors?.join(' ') ?? 'Import failed. Please try again.'
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
    clearTransientState()
    setSetuSyncing(true)
    setParseResult(null)
    setTransactions([])

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

      const syncRes = await api.post<BankSyncResult>(`/api/v1/${companyId}/setu/sync`)
      const result: ImportResult = {
        ...syncRes.data,
        skipped: 0,
        warnings: syncRes.data.imported === 0 && syncRes.data.duplicates > 0 ? ['All synced rows were already present, so nothing new was imported.'] : [],
        errors: [],
        mode: 'bankSync',
      }
      setImportResult(result)
      setStep('done')
      if ((result.imported ?? 0) > 0) {
        onImportSuccess()
      }
    } catch (err: any) {
      setSetuError(err?.response?.data?.error || err?.response?.data?.message || 'Failed to sync with Setu AA.')
    } finally {
      setSetuSyncing(false)
    }
  }

  const outcomeTitle = importResult?.mode === 'bankSync' ? 'Bank Sync Complete' : 'Import Complete'

  if (!capabilities.canEditFinance) {
    return (
      <div className="upgrade-gate">
        <div style={{ fontSize: 56 }}>📥</div>
        <h2>Statement import is not available for viewer access</h2>
        <p>Editors and owners can upload statements, review extracted rows, and import transactions into the workspace.</p>
      </div>
    )
  }

  return (
    <div className="statement-import">
      <div className="import-header">
        <h2>📥 Import Bank Statement</h2>
        <p className="import-subtitle">
          Upload your UPI history, bank statement export, PDF, or screenshot. <strong>Sensitive information is automatically removed before transactions are shown for review.</strong>
        </p>
        <div className="privacy-badge">
          <span className="privacy-icon">🔒</span>
          <span>Privacy Protected: account numbers, IFSC codes, PANs, card numbers, and personal UPI/mobile identifiers are redacted.</span>
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
                <p>Parsing statement and redacting sensitive data…</p>
              </div>
            ) : (
              <>
                <div className="dropzone-icon">📂</div>
                <p className="dropzone-title">Drop your statement here, or click to browse</p>
                <p className="dropzone-hint">Supports CSV, PDF, PNG, JPG, TIFF, HEIC · Max {MAX_FILE_SIZE_MB}MB</p>
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
            accept=".csv,.pdf,.png,.jpg,.jpeg,.webp,.bmp,.tiff,.heic,.heif"
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

          {capabilities.canUseBankSync ? (
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
          ) : (
            <div className="card" style={{ marginTop: 24, background: 'rgba(59,130,246,0.08)', border: '1px solid rgba(59,130,246,0.22)' }}>
              <div className="card-title" style={{ marginBottom: 8 }}>Bank sync is owner-only</div>
              <p style={{ margin: 0, color: 'var(--text-secondary)', lineHeight: 1.6 }}>
                You can still upload CSV, PDF, and screenshot statements here. Only the workspace owner can connect live bank sync through Setu.
              </p>
            </div>
          )}
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

          <div style={{ display: 'grid', gap: 12, gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', marginBottom: 16 }}>
            <div className="privacy-confirm-badge">🧠 Parse mode: <strong>{parseResult.parseMode ?? 'unknown'}</strong></div>
            <div className="privacy-confirm-badge">📊 Document confidence: <strong>{Math.round((parseResult.documentConfidence ?? 0) * 100)}%</strong></div>
            <div className="privacy-confirm-badge">📝 Rows needing review: <strong>{reviewCount}</strong></div>
          </div>

          {(parseResult.warning || parseResult.warnings?.length) && (
            <div className="parse-warning">
              ⚠️ {parseResult.warning ?? 'Review flagged rows before importing.'}
              {parseResult.warnings?.length ? (
                <ul style={{ marginTop: 8, marginBottom: 0 }}>
                  {parseResult.warnings.map(warning => <li key={warning}>{warning}</li>)}
                </ul>
              ) : null}
            </div>
          )}

          <div className="privacy-confirm-badge">
            ✅ Sensitive data has been redacted. Highlighted rows have lower confidence and should be reviewed before import.
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
                  <th>Confidence</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {transactions.map((txn, index) => (
                  <tr
                    key={`${txn.date}-${txn.description}-${txn.amount}-${index}`}
                    className={`${txn.selected ? '' : 'row-deselected'} ${txn.needsReview ? 'row-review' : ''}`.trim()}
                    onClick={() => toggleRow(index)}
                    style={txn.needsReview ? { background: 'rgba(245, 158, 11, 0.08)' } : undefined}
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
                    <td className="desc-cell">
                      <div>{txn.description}</div>
                      <div style={{ marginTop: 4, fontSize: 12, color: 'var(--text-muted)' }}>{txn.source}</div>
                      {txn.warnings?.length ? (
                        <div style={{ marginTop: 4, fontSize: 12, color: '#fbbf24' }}>{txn.warnings.join(' ')}</div>
                      ) : null}
                    </td>
                    <td className={txn.amount >= 0 ? 'positive' : 'negative'}>
                      {txn.amount >= 0 ? '+' : '−'}₹{Math.abs(txn.amount).toLocaleString('en-IN')}
                    </td>
                    <td>
                      {Math.round((txn.confidence ?? 0) * 100)}%
                    </td>
                    <td>
                      <span className={`type-badge ${txn.needsReview ? 'expense' : txn.amount >= 0 ? 'income' : 'expense'}`}>
                        {txn.needsReview ? '🔎 Review' : txn.amount >= 0 ? '📈 Income' : '📉 Expense'}
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
          <h3>{outcomeTitle}</h3>
          <p className="done-count">
            <strong>{importResult.imported}</strong> new transaction{importResult.imported === 1 ? '' : 's'} saved.
          </p>
          {typeof importResult.duplicates === 'number' && (
            <p className="done-note">
              <strong>{importResult.duplicates}</strong> duplicate transaction{importResult.duplicates === 1 ? '' : 's'} skipped.
              {typeof importResult.skipped === 'number' ? ` ${importResult.skipped} invalid row${importResult.skipped === 1 ? '' : 's'} skipped.` : ''}
            </p>
          )}
          {importResult.message && <p className="done-note">{importResult.message}</p>}
          {importResult.warnings?.length ? (
            <div className="parse-warning" style={{ marginTop: 16, textAlign: 'left' }}>
              ⚠️ Review notes:
              <ul style={{ marginTop: 8, marginBottom: 0 }}>
                {importResult.warnings.map(warning => <li key={warning}>{warning}</li>)}
              </ul>
            </div>
          ) : null}
          {importResult.errors?.length ? (
            <div className="parse-error" style={{ marginTop: 16, textAlign: 'left' }}>
              ❌ Some rows could not be imported:
              <ul style={{ marginTop: 8, marginBottom: 0 }}>
                {importResult.errors.map(error => <li key={error}>{error}</li>)}
              </ul>
            </div>
          ) : null}
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
