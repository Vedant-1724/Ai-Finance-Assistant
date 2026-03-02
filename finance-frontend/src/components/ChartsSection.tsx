// PATH: finance-frontend/src/components/ChartsSection.tsx
// NEW: Full live Recharts dashboard — BarChart, PieChart, LineChart, AreaChart

import { useEffect, useState, useCallback } from 'react'
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer,
  PieChart, Pie, Cell, AreaChart, Area, LineChart, Line
} from 'recharts'
import api from '../api'
import { useAuth } from '../context/AuthContext'

interface MonthlyBar  { month: string; income: number; expense: number; net: number }
interface CategoryPie { name: string;  value: number;  percent: number }
interface DailyBal    { date: string;  balance: number }
interface ChartData   { monthly: MonthlyBar[]; categoryBreakdown: CategoryPie[]; dailyBalance: DailyBal[] }

const COLORS = ['#3b82f6','#8b5cf6','#ec4899','#f59e0b','#10b981','#06b6d4','#f97316','#6366f1']
const DARK = '#0f172a'
const GRID = '#1e293b'
const TEXT = '#94a3b8'

const fmt = (v: number) => '₹' + Math.abs(v).toLocaleString('en-IN', {maximumFractionDigits: 0})

export default function ChartsSection({ companyId }: { companyId: number }) {
  const { user } = useAuth()
  const [data, setData]       = useState<ChartData | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError]     = useState<string | null>(null)
  const [months, setMonths]   = useState(6)

  const fetchData = useCallback(async () => {
    setLoading(true); setError(null)
    try {
      const res = await api.get<ChartData>(`/api/v1/${companyId}/charts?months=${months}`,
        { headers: { Authorization: `Bearer ${user?.token}` } })
      setData(res.data)
    } catch (e: any) {
      if (e?.response?.status === 402) setError('UPGRADE_REQUIRED')
      else setError('Failed to load chart data')
    } finally { setLoading(false) }
  }, [companyId, months, user?.token])

  useEffect(() => { void fetchData() }, [fetchData])

  if (loading) return <div className="loading">⏳ Loading charts...</div>
  if (error === 'UPGRADE_REQUIRED') return (
    <div className="upgrade-gate">
      <div style={{fontSize:48}}>📈</div>
      <h2>Live Charts require Trial or Pro</h2>
      <p>Upgrade to see income vs expense trends, category breakdowns, and daily balance charts.</p>
      <a href="/subscription" className="btn-primary">Upgrade Now</a>
    </div>
  )
  if (error || !data) return <div className="error">❌ {error}</div>

  const PeriodBtn = ({ v, label }: { v: number; label: string }) => (
    <button className={`period-btn ${months === v ? 'active' : ''}`} onClick={() => setMonths(v)}>
      {label}
    </button>
  )

  const totalIncome  = data.monthly.reduce((s, m) => s + m.income,  0)
  const totalExpense = data.monthly.reduce((s, m) => s + m.expense, 0)
  const netProfit    = totalIncome - totalExpense

  return (
    <div className="charts-page">
      <div className="page-header">
        <h1 className="page-title">📈 Financial Charts</h1>
        <div className="period-selector">
          <PeriodBtn v={3} label="3M" />
          <PeriodBtn v={6} label="6M" />
          <PeriodBtn v={12} label="1Y" />
        </div>
      </div>

      {/* Summary row */}
      <div className="metric-row" style={{marginBottom:24}}>
        {[['Total Income','₹'+totalIncome.toLocaleString('en-IN',{maximumFractionDigits:0}),'#10b981'],
          ['Total Expense','₹'+totalExpense.toLocaleString('en-IN',{maximumFractionDigits:0}),'#ef4444'],
          ['Net Profit','₹'+netProfit.toLocaleString('en-IN',{maximumFractionDigits:0}), netProfit>=0 ? '#10b981' : '#ef4444']
        ].map(([label, val, color]) => (
          <div key={label} className="metric-card" style={{flex:1}}>
            <div className="metric-label">{label}</div>
            <div className="metric-value" style={{color: color as string, fontSize:22}}>{val}</div>
          </div>
        ))}
      </div>

      {/* 1. Monthly Income vs Expense Bar Chart */}
      <div className="chart-card">
        <h3 className="chart-title">📊 Monthly Income vs Expense</h3>
        <ResponsiveContainer width="100%" height={280}>
          <BarChart data={data.monthly} margin={{top:10,right:10,left:0,bottom:0}}>
            <CartesianGrid strokeDasharray="3 3" stroke={GRID} />
            <XAxis dataKey="month" tick={{fill:TEXT, fontSize:12}} />
            <YAxis tickFormatter={v => '₹'+(v/1000).toFixed(0)+'k'} tick={{fill:TEXT, fontSize:11}} />
            <Tooltip formatter={(v: number) => fmt(v)} contentStyle={{background:'#1e293b',border:'none',color:'#e2e8f0',borderRadius:8}} />
            <Legend wrapperStyle={{color:TEXT}} />
            <Bar dataKey="income"  fill="#10b981" name="Income"  radius={[4,4,0,0]} />
            <Bar dataKey="expense" fill="#ef4444" name="Expense" radius={[4,4,0,0]} />
          </BarChart>
        </ResponsiveContainer>
      </div>

      <div className="charts-row">
        {/* 2. Category Pie Chart */}
        <div className="chart-card" style={{flex:1, minWidth:280}}>
          <h3 className="chart-title">🥧 Expense by Category</h3>
          {data.categoryBreakdown.length === 0 ? (
            <div className="empty-state" style={{padding:40}}>No expense categories yet</div>
          ) : (
            <ResponsiveContainer width="100%" height={260}>
              <PieChart>
                <Pie data={data.categoryBreakdown} dataKey="value" nameKey="name"
                     cx="50%" cy="50%" outerRadius={90} label={({name, percent}) => `${name} ${(percent).toFixed(0)}%`}
                     labelLine={{stroke:TEXT}}>
                  {data.categoryBreakdown.map((_, i) => (
                    <Cell key={i} fill={COLORS[i % COLORS.length]} />
                  ))}
                </Pie>
                <Tooltip formatter={(v:number) => fmt(v)} contentStyle={{background:'#1e293b',border:'none',color:'#e2e8f0',borderRadius:8}} />
              </PieChart>
            </ResponsiveContainer>
          )}
        </div>

        {/* 3. Daily Balance Area Chart */}
        <div className="chart-card" style={{flex:2, minWidth:300}}>
          <h3 className="chart-title">📉 Daily Balance (Last 60 Days)</h3>
          <ResponsiveContainer width="100%" height={260}>
            <AreaChart data={data.dailyBalance} margin={{top:10,right:10,left:0,bottom:0}}>
              <defs>
                <linearGradient id="balGrad" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%"  stopColor="#3b82f6" stopOpacity={0.3} />
                  <stop offset="95%" stopColor="#3b82f6" stopOpacity={0}   />
                </linearGradient>
              </defs>
              <CartesianGrid strokeDasharray="3 3" stroke={GRID} />
              <XAxis dataKey="date" tick={{fill:TEXT, fontSize:10}}
                     tickFormatter={d => d.slice(5)} interval={9} />
              <YAxis tickFormatter={v => '₹'+(v/1000).toFixed(0)+'k'} tick={{fill:TEXT, fontSize:11}} />
              <Tooltip formatter={(v:number) => fmt(v)} labelFormatter={l => 'Date: '+l}
                       contentStyle={{background:'#1e293b',border:'none',color:'#e2e8f0',borderRadius:8}} />
              <Area type="monotone" dataKey="balance" stroke="#3b82f6" fill="url(#balGrad)" strokeWidth={2} name="Balance" />
            </AreaChart>
          </ResponsiveContainer>
        </div>
      </div>

      {/* 4. Net Profit Line Chart */}
      <div className="chart-card">
        <h3 className="chart-title">📈 Monthly Net Profit Trend</h3>
        <ResponsiveContainer width="100%" height={220}>
          <LineChart data={data.monthly} margin={{top:10,right:10,left:0,bottom:0}}>
            <CartesianGrid strokeDasharray="3 3" stroke={GRID} />
            <XAxis dataKey="month" tick={{fill:TEXT, fontSize:12}} />
            <YAxis tickFormatter={v => '₹'+(v/1000).toFixed(0)+'k'} tick={{fill:TEXT, fontSize:11}} />
            <Tooltip formatter={(v:number) => fmt(v)} contentStyle={{background:'#1e293b',border:'none',color:'#e2e8f0',borderRadius:8}} />
            <Line type="monotone" dataKey="net" stroke="#8b5cf6" strokeWidth={2.5}
                  dot={{fill:'#8b5cf6', r:4}} name="Net Profit"
                  activeDot={{r:6, fill:'#a78bfa'}} />
          </LineChart>
        </ResponsiveContainer>
      </div>
    </div>
  )
}
