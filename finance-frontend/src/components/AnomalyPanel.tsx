// PATH: finance-frontend/src/components/AnomalyPanel.tsx
// Displays ML-detected anomalous transactions as dismissible alert cards.
// Shown at the top of the Dashboard when anomalies exist.

interface AnomalyAlert {
  id:            number
  companyId:     number
  transactionId: number | null
  amount:        number
  detectedAt:    string
}

interface Props {
  companyId: number
  anomalies: AnomalyAlert[]
  canDismiss?: boolean
  onDismiss?: (id: number) => void
}

export default function AnomalyPanel({ anomalies, canDismiss = false, onDismiss }: Props) {
  if (anomalies.length === 0) return null

  const formatDate = (iso: string) => {
    try {
      return new Date(iso).toLocaleDateString('en-IN', {
        day: '2-digit', month: 'short', year: 'numeric',
        hour: '2-digit', minute: '2-digit'
      })
    } catch { return iso }
  }

  return (
    <div className="anomaly-panel">
      <div className="anomaly-title">
        <span>🚨</span>
        <span>
          {anomalies.length} Anomal{anomalies.length === 1 ? 'y' : 'ies'} Detected
        </span>
        <span style={{ fontSize: 12, fontWeight: 400, color: '#fcd34d', marginLeft: 4 }}>
          — Unusual transactions flagged by AI
        </span>
      </div>

      {anomalies.map(a => (
        <div key={a.id} className="anomaly-item">
          <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
            <span className="anomaly-amount">
              {a.amount >= 0 ? '+' : '−'}₹{Math.abs(a.amount).toLocaleString('en-IN')}
            </span>
            {a.transactionId && (
              <span style={{ fontSize: 11, color: '#94a3b8' }}>
                Transaction #{a.transactionId}
              </span>
            )}
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginLeft: 'auto' }}>
            <span className="anomaly-date">{formatDate(a.detectedAt)}</span>
            <span style={{
              padding: '2px 8px', borderRadius: 5, background: 'rgba(245,158,11,0.12)',
              color: '#fcd34d', fontSize: 10, fontWeight: 700, border: '1px solid rgba(245,158,11,0.3)'
            }}>
              ANOMALY
            </span>
            {canDismiss && onDismiss ? (
              <button
                className="btn-dismiss"
                onClick={() => onDismiss(a.id)}
                title="Dismiss alert"
                aria-label="Dismiss anomaly alert"
              >
                ✕
              </button>
            ) : null}
          </div>
        </div>
      ))}

      <p style={{
        fontSize: 11, color: '#78716c', marginTop: 10, marginBottom: 0,
        display: 'flex', alignItems: 'center', gap: 6
      }}>
        <span>ℹ️</span>
        These transactions were flagged by the Isolation Forest ML model as statistical outliers.
        Review them manually — dismiss if expected.
      </p>
    </div>
  )
}
