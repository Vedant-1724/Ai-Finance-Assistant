import { Suspense, lazy, useEffect, useState, useCallback } from 'react'
import api from '../api'
import AddTransactionModal from './AddTransactionModal'
import AnomalyPanel from './AnomalyPanel'
import { useAuth } from '../context/AuthContext'

interface Transaction {
  id: number
  date: string
  amount: number
  description: string
  categoryName: string | null
}

interface CategoryBreakdown {
  categoryName: string | null
  amount: number
  type: 'INCOME' | 'EXPENSE' | null
}

interface PnLReport {
  period: string
  startDate: string
  endDate: string
  totalIncome: number
  totalExpense: number
  netProfit: number
  breakdown: CategoryBreakdown[]
}

interface AnomalyAlert {
  id: number
  companyId: number
  transactionId: number | null
  amount: number
  detectedAt: string
}

interface DashboardProps {
  companyId: number
  onOpenCharts?: () => void
}

const ChartsSection = lazy(() => import('./ChartsSection'))

type Period = 'month' | 'quarter' | 'year'

function Dashboard({ companyId, onOpenCharts }: DashboardProps) {
  const { capabilities } = useAuth()
  const [transactions, setTransactions] = useState<Transaction[]>([])
  const [txnLoading, setTxnLoading] = useState(true)
  const [txnError, setTxnError] = useState<string | null>(null)

  const [pnl, setPnl] = useState<PnLReport | null>(null)
  const [pnlLoading, setPnlLoading] = useState(true)
  const [pnlError, setPnlError] = useState<string | null>(null)
  const [activePeriod, setActivePeriod] = useState<Period>('month')

  const [showModal, setShowModal] = useState(false)
  const [editingTxn, setEditingTxn] = useState<Transaction | null>(null)
  const [successMsg, setSuccessMsg] = useState<string | null>(null)
  const [anomalies, setAnomalies] = useState<AnomalyAlert[]>([])
  const [deleteConfirm, setDeleteConfirm] = useState<Transaction | null>(null)

  const fetchTransactions = useCallback(async () => {
    setTxnLoading(true)
    setTxnError(null)
    try {
      const res = await api.get<Transaction[]>(`/api/v1/${companyId}/transactions`)
      setTransactions(Array.isArray(res.data) ? res.data : [])
    } catch {
      setTxnError('Cannot connect to backend. Make sure Spring Boot is running on port 8080.')
    } finally {
      setTxnLoading(false)
    }
  }, [companyId])

  const fetchPnL = useCallback(async (period: Period) => {
    setPnlLoading(true)
    setPnlError(null)
    try {
      const res = await api.get<PnLReport>(
        `/api/v1/${companyId}/reports/pnl?period=${period}`
      )
      setPnl(res.data)
    } catch (err: any) {
      if (err.response?.status === 402) {
        setPnlError('Upgrade to Pro to view Profit & Loss reports.')
      } else {
        setPnlError('Could not load P&L report. Make sure Spring Boot is running.')
      }
    } finally {
      setPnlLoading(false)
    }
  }, [companyId])

  const fetchAnomalies = useCallback(async () => {
    try {
      const res = await api.get<AnomalyAlert[]>(`/api/v1/${companyId}/anomalies`)
      setAnomalies(Array.isArray(res.data) ? res.data : [])
    } catch {
      setAnomalies([])
    }
  }, [companyId])

  useEffect(() => { void fetchTransactions() }, [fetchTransactions])
  useEffect(() => { void fetchPnL(activePeriod) }, [fetchPnL, activePeriod])
  useEffect(() => {
    void fetchAnomalies()
    const id = setInterval(() => { void fetchAnomalies() }, 30_000)
    return () => clearInterval(id)
  }, [fetchAnomalies])

  const handleTransactionAdded = () => {
    void fetchTransactions()
    void fetchPnL(activePeriod)
    setTimeout(() => { void fetchAnomalies() }, 5000)
    setSuccessMsg(editingTxn ? 'Transaction updated successfully!' : 'Transaction added successfully!')
    setEditingTxn(null)
    setTimeout(() => setSuccessMsg(null), 3500)
  }

  const handleDelete = async (tx: Transaction) => {
    try {
      await api.delete(`/api/v1/${companyId}/transactions/${tx.id}`)
      setDeleteConfirm(null)
      setSuccessMsg('Transaction deleted successfully!')
      setTimeout(() => setSuccessMsg(null), 3500)
      void fetchTransactions()
      void fetchPnL(activePeriod)
    } catch {
      setSuccessMsg(null)
    }
  }

  const handleEdit = (tx: Transaction) => {
    setEditingTxn(tx)
    setShowModal(true)
  }

  const handleDismissAnomaly = async (anomalyId: number) => {
    if (!capabilities.canEditFinance) {
      return
    }

    try {
      await api.delete(`/api/v1/${companyId}/anomalies/${anomalyId}`)
      setAnomalies(prev => prev.filter(anomaly => anomaly.id !== anomalyId))
    } catch {
      setSuccessMsg(null)
    }
  }

  const safeTransactions = Array.isArray(transactions) ? transactions : []
  const liveIncome = safeTransactions.filter(t => t.amount > 0).reduce((s, t) => s + t.amount, 0)
  const liveExpense = safeTransactions.filter(t => t.amount < 0).reduce((s, t) => s + Math.abs(t.amount), 0)
  const liveNet = liveIncome - liveExpense

  const periodLabel: Record<Period, string> = {
    month: 'This Month', quarter: 'This Quarter', year: 'This Year',
  }

  return (
    <div className="dashboard">
      {successMsg && (
        <div className="success-toast">
          <span>✅</span> {successMsg}
        </div>
      )}

      <AnomalyPanel
        companyId={companyId}
        anomalies={anomalies}
        canDismiss={capabilities.canEditFinance}
        onDismiss={id => { void handleDismissAnomaly(id) }}
      />

      {txnError ? (
        <div className="error">❌ {txnError}</div>
      ) : txnLoading ? (
        <div className="loading">⏳ Loading financial data...</div>
      ) : (
        <>
          <div className="premium-metric-container">
            <div className="premium-metric-card income">
              <div className="premium-metric-header">
                <div className="premium-metric-icon">📈</div>
                <label className="premium-metric-label">Total Income</label>
              </div>
              <div className="premium-metric-value">₹{liveIncome.toLocaleString('en-IN')}</div>
              <span className="premium-metric-change positive">▲ 12.5%</span>
            </div>
            <div className="premium-metric-card expense">
              <div className="premium-metric-header">
                <div className="premium-metric-icon">📉</div>
                <label className="premium-metric-label">Total Expenses</label>
              </div>
              <div className="premium-metric-value">₹{liveExpense.toLocaleString('en-IN')}</div>
              <span className="premium-metric-change negative">▼ 3.2%</span>
            </div>
            <div className="premium-metric-card net">
              <div className="premium-metric-header">
                <div className="premium-metric-icon">💰</div>
                <label className="premium-metric-label">Net Cash Flow</label>
              </div>
              <div
                className="premium-metric-value"
                style={{ color: liveNet >= 0 ? 'var(--accent-bright)' : 'var(--red)' }}
              >
                ₹{liveNet.toLocaleString('en-IN')}
              </div>
              <span className={`premium-metric-change ${liveNet >= 0 ? 'positive' : 'negative'}`}>
                {liveNet >= 0 ? '▲' : '▼'} {liveIncome > 0 ? Math.abs(Math.round((liveNet / liveIncome) * 100)) : 0}%
              </span>
            </div>
            <div className="premium-metric-card savings">
              <div className="premium-metric-header">
                <div className="premium-metric-icon">🏦</div>
                <label className="premium-metric-label">Savings Rate</label>
              </div>
              <div
                className="premium-metric-value"
                style={{ color: liveIncome > 0 && liveNet >= 0 ? '#c084fc' : 'var(--text-muted)' }}
              >
                {liveIncome > 0 ? Math.round(((liveIncome - liveExpense) / liveIncome) * 100) : 0}%
              </div>
              <span className="premium-metric-change positive">Healthy</span>
            </div>
          </div>

          <div className="premium-pnl-container" style={{ marginBottom: 24 }}>
            <div className="pnl-header">
              <h3>📈 Interactive Charts</h3>
              {onOpenCharts && (
                <button className="glass-btn glass-btn-primary" onClick={onOpenCharts}>
                  <span>Open Charts Tab &rarr;</span>
                </button>
              )}
            </div>
            <div className="pnl-date-range">
              Live charts are active below. Use the full Charts tab for a dedicated expanded view.
            </div>
            <Suspense fallback={<div className="loading">⏳ Loading charts...</div>}>
              <ChartsSection companyId={companyId} embedded />
            </Suspense>
          </div>
        </>
      )}

      <div className="premium-pnl-container pnl-section">
        <div className="pnl-header">
          <h3>📊 Profit & Loss Report</h3>
          <div className="period-tabs premium-period-tabs">
            {(['month', 'quarter', 'year'] as Period[]).map(p => (
                <button
                key={p}
                className={`period-tab premium-period-tab glass-btn ${activePeriod === p ? 'glass-btn-primary' : ''}`}
                style={{ borderRadius: 'var(--glass-radius-pill)', padding: '6px 16px', border: activePeriod === p ? undefined : '1px solid transparent', background: activePeriod === p ? undefined : 'transparent' }}
                onClick={() => setActivePeriod(p)}
              >
                {periodLabel[p]}
              </button>
            ))}
          </div>
        </div>

        {pnlLoading ? (
          <div className="pnl-loading">⏳ Loading report...</div>
        ) : pnlError ? (
          <div className="pnl-error">⚠️ {pnlError}</div>
        ) : pnl ? (
          <>
            <div className="pnl-date-range">
              {pnl.startDate} → {pnl.endDate}
            </div>
            <div className="pnl-cards">
              <div className="pnl-card income-card">
                <span className="pnl-label">Income</span>
                <span className="pnl-value income-val">
                  +₹{Number(pnl.totalIncome ?? 0).toLocaleString('en-IN')}
                </span>
              </div>
              <div className="pnl-card expense-card">
                <span className="pnl-label">Expenses</span>
                <span className="pnl-value expense-val">
                  −₹{Number(pnl.totalExpense ?? 0).toLocaleString('en-IN')}
                </span>
              </div>
              <div className={`pnl-card net-card ${Number(pnl.netProfit ?? 0) >= 0 ? 'profit' : 'loss'}`}>
                <span className="pnl-label">Net Profit</span>
                <span className={`pnl-value ${Number(pnl.netProfit ?? 0) >= 0 ? 'profit-val' : 'loss-val'}`}>
                  {Number(pnl.netProfit ?? 0) >= 0 ? '+' : ''}₹{Number(pnl.netProfit ?? 0).toLocaleString('en-IN')}
                </span>
              </div>
            </div>

            {pnl.breakdown && pnl.breakdown.length > 0 ? (
              <>
                <div className="breakdown-title">Category Breakdown</div>
                <table className="breakdown-table">
                  <thead>
                    <tr>
                      <th>Category</th>
                      <th>Type</th>
                      <th>Amount</th>
                    </tr>
                  </thead>
                  <tbody>
                    {pnl.breakdown.map((row: any, i: number) => (
                      <tr key={i}>
                        <td>{row.categoryName ?? '—'}</td>
                        <td>
                          <span className={`type-badge ${row.type?.toLowerCase() ?? 'unknown'}`}>
                            {row.type === 'INCOME' ? '📈 Income' : '📉 Expense'}
                          </span>
                        </td>
                        <td className={row.type === 'INCOME' ? 'positive' : 'negative'}>
                          {row.type === 'INCOME' ? '+' : '−'}₹{Number(row.amount ?? 0).toLocaleString('en-IN')}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </>
            ) : (
              <div className="breakdown-empty">No transactions in this period yet.</div>
            )}
          </>
        ) : null}
      </div>

      <div className="transactions">
        <div className="transactions-header">
          <h3>
            Recent Transactions
            <span className="txn-count">{transactions.length}</span>
          </h3>
          <div className="header-actions">
            <button className="glass-btn" onClick={() => { void fetchTransactions() }} style={{ padding: '8px 16px' }}>
              <span>↻ Refresh</span>
            </button>
            {capabilities.canEditFinance ? (
              <button className="glass-btn glass-btn-primary" onClick={() => { setEditingTxn(null); setShowModal(true) }} style={{ padding: '8px 16px' }}>
                <span>＋ Add Transaction</span>
              </button>
            ) : null}
          </div>
        </div>

        {txnLoading ? (
          <div className="loading">⏳ Loading transactions...</div>
        ) : transactions.length === 0 ? (
          <div className="empty-state">
            <div className="empty-icon">📂</div>
            <p className="empty-title">No transactions yet</p>
            <p className="empty-sub">
              {capabilities.canEditFinance
                ? 'Click "Add Transaction" to record your first entry'
                : 'No transactions have been recorded in this workspace yet.'}
            </p>
            {capabilities.canEditFinance ? (
              <button className="glass-btn glass-btn-primary" onClick={() => { setEditingTxn(null); setShowModal(true) }}>
                ＋ Add Your First Transaction
              </button>
            ) : null}
          </div>
        ) : (
          <table>
            <thead>
              <tr>
                <th>Date</th>
                <th>Description</th>
                <th>Category</th>
                <th>Amount</th>
                {capabilities.canEditFinance ? <th style={{ width: 100, textAlign: 'center' }}>Actions</th> : null}
              </tr>
            </thead>
            <tbody>
              {transactions.map(tx => (
                <tr key={tx.id}>
                  <td className="date-cell">{tx.date}</td>
                  <td>{tx.description}</td>
                  <td style={{ color: 'var(--text-muted)', fontSize: '12px' }}>
                    {tx.categoryName ?? '—'}
                  </td>
                  <td className={tx.amount >= 0 ? 'positive' : 'negative'}>
                    {tx.amount >= 0 ? '+' : '−'}₹{Math.abs(tx.amount).toLocaleString('en-IN')}
                  </td>
                  {capabilities.canEditFinance ? (
                    <td style={{ textAlign: 'center' }}>
                      <div style={{ display: 'flex', gap: 6, justifyContent: 'center' }}>
                        <button
                          className="glass-btn"
                          title="Edit"
                          onClick={() => handleEdit(tx)}
                          style={{
                            padding: '6px 10px',
                            cursor: 'pointer',
                            fontSize: 13,
                          }}
                        >
                          ✏️
                        </button>
                        <button
                          className="glass-btn"
                          title="Delete"
                          onClick={() => setDeleteConfirm(tx)}
                          style={{
                            padding: '6px 10px',
                            cursor: 'pointer',
                            fontSize: 13,
                          }}
                        >
                          🗑️
                        </button>
                      </div>
                    </td>
                  ) : null}
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {showModal && capabilities.canEditFinance && (
        <AddTransactionModal
          companyId={companyId}
          onClose={() => { setShowModal(false); setEditingTxn(null) }}
          onSuccess={handleTransactionAdded}
          editingTxn={editingTxn}
        />
      )}

      {deleteConfirm && (
        <div className="modal-overlay" onClick={e => { if (e.target === e.currentTarget) setDeleteConfirm(null) }}>
          <div className="modal-box glass-container" style={{ maxWidth: 400 }}>
            <div className="modal-header">
              <h2 className="modal-title">🗑️ Delete Transaction</h2>
              <button className="modal-close" onClick={() => setDeleteConfirm(null)}>×</button>
            </div>
            <p style={{ margin: '16px 0', color: 'var(--text-secondary)', fontSize: 14, lineHeight: 1.6 }}>
              Are you sure you want to delete "<strong>{deleteConfirm.description}</strong>"
              ({deleteConfirm.amount >= 0 ? '+' : '−'}₹{Math.abs(deleteConfirm.amount).toLocaleString('en-IN')})?
              <br /><br />
              <span style={{ color: '#f87171', fontSize: 12 }}>⚠️ This action cannot be undone.</span>
            </p>
            <div className="modal-footer" style={{ display: 'flex', gap: 12, justifyContent: 'flex-end' }}>
              <button className="glass-btn" onClick={() => setDeleteConfirm(null)}>
                Cancel
              </button>
              <button
                className="glass-btn glass-btn-danger"
                onClick={() => void handleDelete(deleteConfirm)}
              >
                🗑️ Delete
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

export default Dashboard
