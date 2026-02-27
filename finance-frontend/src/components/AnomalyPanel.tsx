import axios from 'axios'

// Defined locally — no cross-file type export needed.
// TypeScript structural typing makes this compatible with Dashboard's local definition.
interface AnomalyAlert {
  id:            number
  companyId:     number
  transactionId: number | null
  amount:        number
  detectedAt:    string
}

interface AnomalyPanelProps {
  companyId: number
  anomalies: AnomalyAlert[]
  onDismiss: (id: number) => void
}

function formatDateTime(iso: string): string {
  try {
    return new Date(iso).toLocaleString('en-IN', {
      day: '2-digit', month: 'short', year: 'numeric',
      hour: '2-digit', minute: '2-digit',
    })
  } catch {
    return iso
  }
}

function AnomalyPanel({ companyId, anomalies, onDismiss }: AnomalyPanelProps) {
  if (anomalies.length === 0) return null

  const handleDismiss = async (id: number): Promise<void> => {
    try {
      await axios.delete(`http://localhost:8080/api/v1/${companyId}/anomalies/${id}`)
      onDismiss(id)
    } catch {
      // silent — alert reappears on next 30s poll if delete failed
    }
  }

  return (
    <div className="anomaly-section">
      <div className="anomaly-header">
        <h3>
          ⚠️ AI Anomaly Alerts
          <span className="anomaly-badge">{anomalies.length}</span>
        </h3>
        <span className="anomaly-subtitle">
          Unusual transactions detected by AI — review and dismiss when resolved
        </span>
      </div>

      <div className="anomaly-list">
        {anomalies.map(a => (
          <div key={a.id} className="anomaly-card">
            <div className="anomaly-icon">🚨</div>
            <div className="anomaly-details">
              <div className="anomaly-amount">
                ₹{Math.abs(a.amount).toLocaleString('en-IN')}
                <span className="anomaly-type">
                  {a.amount >= 0 ? ' income' : ' expense'}
                </span>
              </div>
              <div className="anomaly-meta">
                {a.transactionId !== null && (
                  <span>Txn #{a.transactionId} · </span>
                )}
                <span>Detected {formatDateTime(a.detectedAt)}</span>
              </div>
            </div>
            <button
              className="anomaly-dismiss"
              onClick={() => { void handleDismiss(a.id) }}
              title="Dismiss this alert"
            >
              ✕
            </button>
          </div>
        ))}
      </div>
    </div>
  )
}

export default AnomalyPanel
