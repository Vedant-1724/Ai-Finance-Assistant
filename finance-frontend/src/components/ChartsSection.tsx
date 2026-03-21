import { useEffect, useState, useCallback } from 'react'
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer,
  PieChart, Pie, Cell, AreaChart, Area, LineChart, Line,
} from 'recharts'
import api from '../api'

interface MonthlyBar { month: string; income: number; expense: number; net: number }
interface CategoryPie { name: string; value: number; percent: number }
interface DailyBal { date: string; balance: number }
interface ChartData { monthly: MonthlyBar[]; categoryBreakdown: CategoryPie[]; dailyBalance: DailyBal[] }

const COLORS = ['#3b82f6', '#8b5cf6', '#ec4899', '#f59e0b', '#10b981', '#06b6d4', '#f97316', '#6366f1']
const GRID = '#1e293b'
const TEXT = '#94a3b8'
const fmt = (value: number) => '₹' + Math.abs(value).toLocaleString('en-IN', { maximumFractionDigits: 0 })

export default function ChartsSection({ companyId, embedded = false }: { companyId: number; embedded?: boolean }) {
  const [data, setData] = useState<ChartData | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [months, setMonths] = useState(6)

  const fetchData = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const res = await api.get<ChartData>(`/api/v1/${companyId}/charts?months=${months}`)
      const d = res.data
      setData({
        monthly: Array.isArray(d?.monthly) ? d.monthly : [],
        categoryBreakdown: Array.isArray(d?.categoryBreakdown) ? d.categoryBreakdown : [],
        dailyBalance: Array.isArray(d?.dailyBalance) ? d.dailyBalance : [],
      })
    } catch {
      setError('Failed to load chart data')
    } finally {
      setLoading(false)
    }
  }, [companyId, months])

  useEffect(() => { void fetchData() }, [fetchData])

  if (loading) return <div className="loading">⏳ Loading charts...</div>
  if (error || !data) return <div className="error">❌ {error}</div>

  const PeriodBtn = ({ v, label }: { v: number; label: string }) => (
    <button className={`period-btn ${months === v ? 'active' : ''}`} onClick={() => setMonths(v)}>
      {label}
    </button>
  )

  const monthly = data.monthly ?? []
  const categoryBreakdown = data.categoryBreakdown ?? []
  const dailyBalance = data.dailyBalance ?? []

  const totalIncome = monthly.reduce((sum, month) => sum + month.income, 0)
  const totalExpense = monthly.reduce((sum, month) => sum + month.expense, 0)
  const netProfit = totalIncome - totalExpense

  return (
    <div className="charts-page" style={embedded ? { padding: 0 } : undefined}>
      {!embedded && (
        <div className="page-header">
          <h1 className="page-title">📈 Financial Charts</h1>
          <div className="period-selector">
            <PeriodBtn v={3} label="3M" />
            <PeriodBtn v={6} label="6M" />
            <PeriodBtn v={12} label="1Y" />
          </div>
        </div>
      )}

      {embedded && (
        <div className="period-selector" style={{ marginBottom: 16 }}>
          <PeriodBtn v={3} label="3M" />
          <PeriodBtn v={6} label="6M" />
          <PeriodBtn v={12} label="1Y" />
        </div>
      )}

      <div className="metric-row" style={{ marginBottom: 24 }}>
        {[
          ['Total Income', '₹' + totalIncome.toLocaleString('en-IN', { maximumFractionDigits: 0 }), '#10b981'],
          ['Total Expense', '₹' + totalExpense.toLocaleString('en-IN', { maximumFractionDigits: 0 }), '#ef4444'],
          ['Net Profit', '₹' + netProfit.toLocaleString('en-IN', { maximumFractionDigits: 0 }), netProfit >= 0 ? '#10b981' : '#ef4444'],
        ].map(([label, value, color]) => (
          <div key={label} className="metric-card" style={{ flex: 1 }}>
            <div className="metric-label">{label}</div>
            <div className="metric-value" style={{ color: color as string, fontSize: 22 }}>{value}</div>
          </div>
        ))}
      </div>

      <div className="chart-card">
        <h3 className="chart-title">📊 Monthly Income vs Expense</h3>
        <ResponsiveContainer width="100%" height={280}>
          <BarChart data={monthly} margin={{ top: 10, right: 10, left: 0, bottom: 0 }}>
            <CartesianGrid strokeDasharray="3 3" stroke={GRID} />
            <XAxis dataKey="month" tick={{ fill: TEXT, fontSize: 12 }} />
            <YAxis tickFormatter={value => '₹' + (value / 1000).toFixed(0) + 'k'} tick={{ fill: TEXT, fontSize: 11 }} />
            <Tooltip formatter={(value: number) => fmt(value)} contentStyle={{ background: '#1e293b', border: 'none', color: '#e2e8f0', borderRadius: 8 }} />
            <Legend wrapperStyle={{ color: TEXT }} />
            <Bar dataKey="income" fill="#10b981" name="Income" radius={[4, 4, 0, 0]} />
            <Bar dataKey="expense" fill="#ef4444" name="Expense" radius={[4, 4, 0, 0]} />
          </BarChart>
        </ResponsiveContainer>
      </div>

      <div className="charts-row">
        <div className="chart-card" style={{ flex: 1, minWidth: 280 }}>
          <h3 className="chart-title">🥧 Expense by Category</h3>
          {categoryBreakdown.length === 0 ? (
            <div className="empty-state" style={{ padding: 40 }}>No expense categories yet</div>
          ) : (
            <ResponsiveContainer width="100%" height={260}>
              <PieChart>
                <Pie
                  data={categoryBreakdown}
                  dataKey="value"
                  nameKey="name"
                  cx="50%"
                  cy="50%"
                  outerRadius={90}
                  label={({ name, percent }) => `${name} ${percent.toFixed(0)}%`}
                  labelLine={{ stroke: TEXT }}
                >
                  {categoryBreakdown.map((_, index) => (
                    <Cell key={index} fill={COLORS[index % COLORS.length]} />
                  ))}
                </Pie>
                <Tooltip formatter={(value: number) => fmt(value)} contentStyle={{ background: '#1e293b', border: 'none', color: '#e2e8f0', borderRadius: 8 }} />
              </PieChart>
            </ResponsiveContainer>
          )}
        </div>

        <div className="chart-card" style={{ flex: 2, minWidth: 300 }}>
          <h3 className="chart-title">📉 Daily Balance (Last 60 Days)</h3>
          <ResponsiveContainer width="100%" height={260}>
            <AreaChart data={dailyBalance} margin={{ top: 10, right: 10, left: 0, bottom: 0 }}>
              <defs>
                <linearGradient id={embedded ? 'balGradEmbedded' : 'balGrad'} x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor="#3b82f6" stopOpacity={0.3} />
                  <stop offset="95%" stopColor="#3b82f6" stopOpacity={0} />
                </linearGradient>
              </defs>
              <CartesianGrid strokeDasharray="3 3" stroke={GRID} />
              <XAxis dataKey="date" tick={{ fill: TEXT, fontSize: 10 }} tickFormatter={date => date.slice(5)} interval={9} />
              <YAxis tickFormatter={value => '₹' + (value / 1000).toFixed(0) + 'k'} tick={{ fill: TEXT, fontSize: 11 }} />
              <Tooltip formatter={(value: number) => fmt(value)} labelFormatter={label => 'Date: ' + label} contentStyle={{ background: '#1e293b', border: 'none', color: '#e2e8f0', borderRadius: 8 }} />
              <Area type="monotone" dataKey="balance" stroke="#3b82f6" fill={embedded ? 'url(#balGradEmbedded)' : 'url(#balGrad)'} strokeWidth={2} name="Balance" />
            </AreaChart>
          </ResponsiveContainer>
        </div>
      </div>

      <div className="chart-card">
        <h3 className="chart-title">📈 Monthly Net Profit Trend</h3>
        <ResponsiveContainer width="100%" height={220}>
          <LineChart data={monthly} margin={{ top: 10, right: 10, left: 0, bottom: 0 }}>
            <CartesianGrid strokeDasharray="3 3" stroke={GRID} />
            <XAxis dataKey="month" tick={{ fill: TEXT, fontSize: 12 }} />
            <YAxis tickFormatter={value => '₹' + (value / 1000).toFixed(0) + 'k'} tick={{ fill: TEXT, fontSize: 11 }} />
            <Tooltip formatter={(value: number) => fmt(value)} contentStyle={{ background: '#1e293b', border: 'none', color: '#e2e8f0', borderRadius: 8 }} />
            <Line type="monotone" dataKey="net" stroke="#8b5cf6" strokeWidth={2.5} dot={{ fill: '#8b5cf6', r: 4 }} name="Net Profit" activeDot={{ r: 6, fill: '#a78bfa' }} />
          </LineChart>
        </ResponsiveContainer>
      </div>
    </div>
  )
}
