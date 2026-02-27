import { useEffect, useState, useCallback } from 'react'
import axios from 'axios'
import AddTransactionModal from './AddTransactionModal'
import ChartsSection from './ChartsSection'
import AnomalyPanel from './AnomalyPanel'

// ── Interfaces ────────────────────────────────────────────────────────────────
interface Transaction {
  id:           number
  date:         string
  amount:       number
  description:  string
  categoryName: string
}

interface CategoryBreakdown {
  categoryName: string
  amount:       number
  type:         'INCOME' | 'EXPENSE'
}

interface PnLReport {
  period:       string
  startDate:    string
  endDate:      string
  totalIncome:  number
  totalExpense: number
  netProfit:    number
  breakdown:    CategoryBreakdown[]
}

// Defined locally — avoids any cross-file type import (verbatimModuleSyntax safe)
interface AnomalyAlert {
  id:            number
  companyId:     number
  transactionId: number | null
  amount:        number
  detectedAt:    string
}

interface DashboardProps {
  companyId: number
}

type Period = 'month' | 'quarter' | 'year'

// ── Component ─────────────────────────────────────────────────────────────────
function Dashboard({ companyId }: DashboardProps) {
  const [transactions, setTransactions] = useState<Transaction[]>([])
  const [txnLoading,   setTxnLoading]   = useState(true)
  const [txnError,     setTxnError]     = useState<string | null>(null)

  const [pnl,          setPnl]          = useState<PnLReport | null>(null)
  const [pnlLoading,   setPnlLoading]   = useState(true)
  const [pnlError,     setPnlError]     = useState<string | null>(null)
  const [activePeriod, setActivePeriod] = useState<Period>('month')

  const [showModal,  setShowModal]  = useState(false)
  const [successMsg, setSuccessMsg] = useState<string | null>(null)

  const [anomalies, setAnomalies] = useState<AnomalyAlert[]>([])

  // ── Fetch transactions ─────────────────────────────────────────────────────
  const fetchTransactions = useCallback(async () => {
    setTxnLoading(true)
    setTxnError(null)
    try {
      const res = await axios.get<Transaction[]>(
        `http://localhost:8080/api/v1/${companyId}/transactions`
      )
      setTransactions(res.data)
    } catch {
      setTxnError('Cannot connect to backend. Make sure Spring Boot is running on port 8080.')
    } finally {
      setTxnLoading(false)
    }
  }, [companyId])

  // ── Fetch P&L ──────────────────────────────────────────────────────────────
  const fetchPnL = useCallback(async (period: Period) => {
    setPnlLoading(true)
    setPnlError(null)
    try {
      const res = await axios.get<PnLReport>(
        `http://localhost:8080/api/v1/${companyId}/reports/pnl?period=${period}`
      )
      setPnl(res.data)
    } catch {
      setPnlError('Could not load P&L report. Make sure Spring Boot is running.')
    } finally {
      setPnlLoading(false)
    }
  }, [companyId])

  // ── Fetch anomalies (polls every 30s — RabbitMQ pipeline is async) ─────────
  const fetchAnomalies = useCallback(async () => {
    try {
      const res = await axios.get<AnomalyAlert[]>(
        `http://localhost:8080/api/v1/${companyId}/anomalies`
      )
      setAnomalies(res.data)
    } catch {
      setAnomalies([])
    }
  }, [companyId])

  useEffect(() => { void fetchTransactions() }, [fetchTransactions])
  useEffect(() => { void fetchPnL(activePeriod) }, [fetchPnL, activePeriod])
  useEffect(() => {
    void fetchAnomalies()
    const interval = setInterval(() => { void fetchAnomalies() }, 30_000)
    return () => clearInterval(interval)
  }, [fetchAnomalies])

  const handleTransactionAdded = () => {
    void fetchTransactions()
    void fetchPnL(activePeriod)
    setTimeout(() => { void fetchAnomalies() }, 5000)
    setSuccessMsg('Transaction added successfully!')
    setTimeout(() => setSuccessMsg(null), 3500)
  }

  // ── Derived metrics ────────────────────────────────────────────────────────
  const liveIncome  = transactions.filter(t => t.amount > 0).reduce((s, t) => s + t.amount, 0)
  const liveExpense = transactions.filter(t => t.amount < 0).reduce((s, t) => s + Math.abs(t.amount), 0)
  const liveNet     = liveIncome - liveExpense

  const periodLabel: Record<Period, string> = {
    month: 'This Month', quarter: 'This Quarter', year: 'This Year',
  }

  // ── Render ─────────────────────────────────────────────────────────────────
  return (
    <div className="dashboard">

      {successMsg && (
        <div className="success-toast">
          <span>✅</span> {successMsg}
        </div>
      )}

      {/* Anomaly panel — self-hides when anomalies array is empty */}
      <AnomalyPanel
        companyId={companyId}
        anomalies={anomalies}
        onDismiss={(id) => setAnomalies(prev => prev.filter(a => a.id !== id))}
      />

      {/* ── Metric Cards ── */}
      {txnError ? (
        <div className="error">❌ {txnError}</div>
      ) : txnLoading ? (
        <div className="loading">⏳ Loading financial data...</div>
      ) : (
        <div className="metric-grid">
          <div className="metric income">
            <div className="metric-icon">📈</div>
            <label>Total Income</label>
            <div className="metric-value">₹{liveIncome.toLocaleString('en-IN')}</div>
          </div>
          <div className="metric expense">
            <div className="metric-icon">📉</div>
            <label>Total Expenses</label>
            <div className="metric-value">₹{liveExpense.toLocaleString('en-IN')}</div>
          </div>
          <div className="metric net">
            <div className="metric-icon">💰</div>
            <label>Net Cash Flow</label>
            <div
              className="metric-value"
              style={{ color: liveNet >= 0 ? 'var(--accent-bright)' : 'var(--expense)' }}
            >
              ₹{liveNet.toLocaleString('en-IN')}
            </div>
          </div>
        </div>
      )}

      {/* ── Charts ── */}
      {!txnLoading && !txnError && (
        <ChartsSection
          transactions={transactions}
          breakdown={pnl?.breakdown ?? []}
        />
      )}

      {/* ── P&L Report ── */}
      <div className="pnl-section">
        <div className="pnl-header">
          <h3>📊 Profit & Loss Report</h3>
          <div className="period-tabs">
            {(['month', 'quarter', 'year'] as Period[]).map(p => (
              <button
                key={p}
                className={`period-tab ${activePeriod === p ? 'active' : ''}`}
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
                  +₹{Number(pnl.totalIncome).toLocaleString('en-IN')}
                </span>
              </div>
              <div className="pnl-card expense-card">
                <span className="pnl-label">Expenses</span>
                <span className="pnl-value expense-val">
                  −₹{Number(pnl.totalExpense).toLocaleString('en-IN')}
                </span>
              </div>
              <div className={`pnl-card net-card ${Number(pnl.netProfit) >= 0 ? 'profit' : 'loss'}`}>
                <span className="pnl-label">Net Profit</span>
                <span className={`pnl-value ${Number(pnl.netProfit) >= 0 ? 'profit-val' : 'loss-val'}`}>
                  {Number(pnl.netProfit) >= 0 ? '+' : ''}₹{Number(pnl.netProfit).toLocaleString('en-IN')}
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
                    {pnl.breakdown.map((row, i) => (
                      <tr key={i}>
                        <td>{row.categoryName}</td>
                        <td>
                          <span className={`type-badge ${row.type.toLowerCase()}`}>
                            {row.type === 'INCOME' ? '📈 Income' : '📉 Expense'}
                          </span>
                        </td>
                        <td className={row.type === 'INCOME' ? 'positive' : 'negative'}>
                          {row.type === 'INCOME' ? '+' : '−'}₹{Number(row.amount).toLocaleString('en-IN')}
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

      {/* ── Transactions Table ── */}
      <div className="transactions">
        <div className="transactions-header">
          <h3>
            Recent Transactions
            <span className="txn-count">{transactions.length}</span>
          </h3>
          <div className="header-actions">
            <button className="btn-refresh" onClick={() => { void fetchTransactions() }}>
              ↻ Refresh
            </button>
            <button className="btn-add-txn" onClick={() => setShowModal(true)}>
              ＋ Add Transaction
            </button>
          </div>
        </div>

        {txnLoading ? (
          <div className="loading">⏳ Loading transactions...</div>
        ) : transactions.length === 0 ? (
          <div className="empty-state">
            <div className="empty-icon">📂</div>
            <p className="empty-title">No transactions yet</p>
            <p className="empty-sub">Click "Add Transaction" to record your first entry</p>
            <button className="btn-add-txn-empty" onClick={() => setShowModal(true)}>
              ＋ Add Your First Transaction
            </button>
          </div>
        ) : (
          <table>
            <thead>
              <tr>
                <th>Date</th>
                <th>Description</th>
                <th>Category</th>
                <th>Amount</th>
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
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {showModal && (
        <AddTransactionModal
          companyId={companyId}
          onClose={() => setShowModal(false)}
          onSuccess={handleTransactionAdded}
        />
      )}
    </div>
  )
}

export default Dashboard
