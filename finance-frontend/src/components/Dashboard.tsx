import { useEffect, useState, useCallback } from 'react'
import axios from 'axios'

interface Transaction {
  id: number
  date: string
  amount: number
  description: string
  categoryName: string
}

interface DashboardProps {
  companyId: number
  token: string
}

function Dashboard({ companyId }: DashboardProps) {
  const [transactions, setTransactions] = useState<Transaction[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const fetchData = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const res = await axios.get<Transaction[]>(
        `http://localhost:8080/api/v1/${companyId}/transactions`
      )
      setTransactions(res.data)
    } catch {
      setError('Cannot connect to backend. Make sure Spring Boot is running on port 8080.')
    } finally {
      setLoading(false)
    }
  }, [companyId])

  useEffect(() => { fetchData() }, [fetchData])

  const income = transactions.filter(t => t.amount > 0).reduce((s, t) => s + t.amount, 0)
  const expense = transactions.filter(t => t.amount < 0).reduce((s, t) => s + Math.abs(t.amount), 0)
  const net = income - expense

  if (loading) return <div className="loading">⏳ Loading financial data...</div>
  if (error) return <div className="error">❌ {error}</div>

  return (
    <div className="dashboard">
      <h2>Financial Overview</h2>

      <div className="metric-grid">
        <div className="metric income">
          <label>Total Income</label>
          <span>₹{income.toLocaleString()}</span>
        </div>
        <div className="metric expense">
          <label>Total Expenses</label>
          <span>₹{expense.toLocaleString()}</span>
        </div>
        <div className="metric forecast">
          <label>Net Cash Flow</label>
          <span style={{ color: net >= 0 ? '#4ade80' : '#f87171' }}>
            ₹{net.toLocaleString()}
          </span>
        </div>
      </div>

      <div className="transactions">
        <div className="transactions-header">
          <h3>Recent Transactions ({transactions.length})</h3>
          <button onClick={fetchData}>↻ Refresh</button>
        </div>

        {transactions.length === 0 ? (
          <p className="empty">No transactions yet. Add some via Postman!</p>
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
                  <td>{tx.date}</td>
                  <td>{tx.description}</td>
                  <td>{tx.categoryName ?? '—'}</td>
                  <td className={tx.amount >= 0 ? 'positive' : 'negative'}>
                    {tx.amount >= 0 ? '+' : '-'}₹{Math.abs(tx.amount).toLocaleString()}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  )
}

export default Dashboard