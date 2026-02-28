import { useMemo } from 'react'
import {
  AreaChart,
  Area,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  PieChart,
  Pie,
  Cell,
  LineChart,
  Line,
  Legend,
} from 'recharts'

// ─────────────────────────────────────────────────────────────────────────────
//  Interfaces  — must match Dashboard.tsx exactly
// ─────────────────────────────────────────────────────────────────────────────

// FIX: categoryName is string | null  (DB JOIN can return null)
interface Transaction {
  id:           number
  date:         string
  amount:       number
  description:  string
  categoryName: string | null
}

// FIX: both fields allow null to match Dashboard.tsx PnLReport breakdown
interface CategoryBreakdown {
  categoryName: string | null
  amount:       number
  type:         'INCOME' | 'EXPENSE' | null
}

interface ChartsSectionProps {
  transactions: Transaction[]
  breakdown:    CategoryBreakdown[]
}

interface ForecastPoint {
  month:           string
  actualIncome:    number | null
  actualExpense:   number | null
  forecastIncome:  number | null
  forecastExpense: number | null
}

// ─────────────────────────────────────────────────────────────────────────────
//  Constants
// ─────────────────────────────────────────────────────────────────────────────
const C_INCOME  = '#10b981'
const C_EXPENSE = '#ef4444'
const C_ACCENT  = '#3b82f6'
const C_ACCENT2 = '#6366f1'

const PIE_COLORS = [
  '#ef4444', '#f97316', '#eab308', '#8b5cf6',
  '#06b6d4', '#10b981', '#f43f5e', '#3b82f6',
]

// ─────────────────────────────────────────────────────────────────────────────
//  Formatters
// ─────────────────────────────────────────────────────────────────────────────
function yTick(value: number): string {
  if (value >= 100_000) return `₹${(value / 100_000).toFixed(1)}L`
  if (value >= 1_000)   return `₹${(value / 1_000).toFixed(0)}k`
  return `₹${value}`
}

function inr(value: number): string {
  return `₹${Math.round(value).toLocaleString('en-IN')}`
}

// ─────────────────────────────────────────────────────────────────────────────
//  Shared tooltip
// ─────────────────────────────────────────────────────────────────────────────
interface SharedPayloadItem {
  name:  string
  value: number
  color: string
}

interface SharedTooltipProps {
  active?:  boolean
  payload?: SharedPayloadItem[]
  label?:   string
}

function ChartTooltip({ active, payload, label }: SharedTooltipProps) {
  if (!active || !payload?.length) return null
  return (
    <div className="chart-tooltip">
      {label && <div className="chart-tooltip-label">{label}</div>}
      {payload.map((p, i) => (
        <div key={i} className="chart-tooltip-row">
          <span className="chart-tooltip-name" style={{ color: p.color }}>{p.name}</span>
          <span className="chart-tooltip-val">{inr(p.value)}</span>
        </div>
      ))}
    </div>
  )
}

// ─────────────────────────────────────────────────────────────────────────────
//  Pie tooltip
// ─────────────────────────────────────────────────────────────────────────────
interface PiePayloadItem {
  name:    string
  value:   number
  payload: { name: string; value: number }
}

interface PieTooltipProps {
  active?:  boolean
  payload?: PiePayloadItem[]
}

function PieTooltip({ active, payload }: PieTooltipProps) {
  if (!active || !payload?.length) return null
  const item = payload[0]
  return (
    <div className="chart-tooltip">
      <div className="chart-tooltip-label">{item.name}</div>
      <div className="chart-tooltip-row">
        <span className="chart-tooltip-name">Amount</span>
        <span className="chart-tooltip-val">{inr(item.value)}</span>
      </div>
    </div>
  )
}

// ─────────────────────────────────────────────────────────────────────────────
//  Pie label
//
//  FIX: All props are optional (number | undefined) to satisfy recharts'
//  PieLabelRenderProps. Guard each with ?? 0 inside the function body.
// ─────────────────────────────────────────────────────────────────────────────
interface PieLabelProps {
  cx?:          number
  cy?:          number
  midAngle?:    number
  outerRadius?: number
  name?:        string
  percent?:     number
}

function PieLabel({ cx, cy, midAngle, outerRadius, name, percent }: PieLabelProps) {
  const pct = percent ?? 0
  if (pct < 0.05) return null
  const RADIAN = Math.PI / 180
  const r = (outerRadius ?? 0) + 22
  const x = (cx ?? 0) + r * Math.cos(-(midAngle ?? 0) * RADIAN)
  const y = (cy ?? 0) + r * Math.sin(-(midAngle ?? 0) * RADIAN)
  return (
    <text
      x={x} y={y}
      fill="#8b9ec7"
      textAnchor={x > (cx ?? 0) ? 'start' : 'end'}
      dominantBaseline="central"
      fontSize={11}
    >
      {name ?? ''} ({(pct * 100).toFixed(0)}%)
    </text>
  )
}

// ─────────────────────────────────────────────────────────────────────────────
//  Empty state
// ─────────────────────────────────────────────────────────────────────────────
function ChartEmpty({ message }: { message: string }) {
  return (
    <div className="chart-empty">
      <div className="chart-empty-icon">📭</div>
      <span>{message}</span>
    </div>
  )
}

// ═════════════════════════════════════════════════════════════════════════════
//  CHART 1 — Cash Flow  (Area)
// ═════════════════════════════════════════════════════════════════════════════
function CashFlowChart({ transactions }: { transactions: Transaction[] }) {
  const data = useMemo(() => {
    const byDate = new Map<string, { income: number; expense: number }>()
    for (const txn of transactions) {
      const date = txn.date ?? ''
      const curr = byDate.get(date) ?? { income: 0, expense: 0 }
      if (txn.amount > 0) curr.income  += txn.amount
      else                curr.expense += Math.abs(txn.amount)
      byDate.set(date, curr)
    }
    return Array.from(byDate.entries())
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([date, v]) => ({ date, ...v }))
  }, [transactions])

  return (
    <div className="chart-card">
      <div className="chart-card-header">
        <h4 className="chart-title">📈 Cash Flow</h4>
        <p className="chart-subtitle">Income vs Expenses over time</p>
      </div>
      {data.length === 0 ? (
        <ChartEmpty message="Add transactions on different dates to see this chart" />
      ) : (
        <ResponsiveContainer width="100%" height={240}>
          <AreaChart data={data} margin={{ top: 10, right: 10, left: 0, bottom: 0 }}>
            <defs>
              <linearGradient id="gradIncome" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%"  stopColor={C_INCOME}  stopOpacity={0.3} />
                <stop offset="95%" stopColor={C_INCOME}  stopOpacity={0.02} />
              </linearGradient>
              <linearGradient id="gradExpense" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%"  stopColor={C_EXPENSE} stopOpacity={0.3} />
                <stop offset="95%" stopColor={C_EXPENSE} stopOpacity={0.02} />
              </linearGradient>
            </defs>
            <CartesianGrid stroke="#1f2d45" strokeDasharray="3 3" vertical={false} />
            <XAxis dataKey="date" tick={{ fill: '#4a5a7a', fontSize: 11 }} axisLine={false} tickLine={false} />
            <YAxis tickFormatter={yTick} tick={{ fill: '#4a5a7a', fontSize: 11 }} axisLine={false} tickLine={false} width={60} />
            <Tooltip content={<ChartTooltip />} />
            <Area type="monotone" dataKey="income"  name="Income"  stroke={C_INCOME}  strokeWidth={2.5} fill="url(#gradIncome)"  dot={false} activeDot={{ r: 5, fill: C_INCOME }} />
            <Area type="monotone" dataKey="expense" name="Expense" stroke={C_EXPENSE} strokeWidth={2.5} fill="url(#gradExpense)" dot={false} activeDot={{ r: 5, fill: C_EXPENSE }} />
          </AreaChart>
        </ResponsiveContainer>
      )}
    </div>
  )
}

// ═════════════════════════════════════════════════════════════════════════════
//  CHART 2 — Expense Breakdown  (Pie)
// ═════════════════════════════════════════════════════════════════════════════
function ExpensePieChart({ breakdown }: { breakdown: CategoryBreakdown[] }) {
  const pieData = useMemo(() =>
    breakdown
      .filter(b => b.type === 'EXPENSE')
      .map(b => ({ name: b.categoryName ?? 'Unknown', value: Math.round(Number(b.amount ?? 0)) }))
      .filter(b => b.value > 0),
    [breakdown]
  )

  const total = useMemo(() => pieData.reduce((s, b) => s + b.value, 0), [pieData])

  return (
    <div className="chart-card">
      <div className="chart-card-header">
        <h4 className="chart-title">🥧 Expense Breakdown</h4>
        <p className="chart-subtitle">By category · {inr(total)} total</p>
      </div>
      {pieData.length === 0 ? (
        <ChartEmpty message="No expense categories found. Categories are assigned by the AI categoriser." />
      ) : (
        <ResponsiveContainer width="100%" height={240}>
          <PieChart>
            <Pie data={pieData} cx="50%" cy="50%" outerRadius={85} dataKey="value" labelLine={false} label={PieLabel}>
              {pieData.map((_entry, index) => (
                <Cell key={`cell-${index}`} fill={PIE_COLORS[index % PIE_COLORS.length]} stroke="transparent" />
              ))}
            </Pie>
            <Tooltip content={<PieTooltip />} />
          </PieChart>
        </ResponsiveContainer>
      )}
    </div>
  )
}

// ═════════════════════════════════════════════════════════════════════════════
//  CHART 3 — 6-Month Forecast  (Line)
// ═════════════════════════════════════════════════════════════════════════════
function ForecastChart({ transactions }: { transactions: Transaction[] }) {
  const data = useMemo((): ForecastPoint[] => {
    if (transactions.length === 0) return []

    const byMonth = new Map<string, { income: number; expense: number }>()
    for (const txn of transactions) {
      const month = (txn.date ?? '').slice(0, 7)
      if (!month) continue
      const curr = byMonth.get(month) ?? { income: 0, expense: 0 }
      if (txn.amount > 0) curr.income  += txn.amount
      else                curr.expense += Math.abs(txn.amount)
      byMonth.set(month, curr)
    }

    const sorted = Array.from(byMonth.entries()).sort(([a], [b]) => a.localeCompare(b))
    if (sorted.length < 2) return []

    const incomeValues  = sorted.map(([, v]) => v.income)
    const expenseValues = sorted.map(([, v]) => v.expense)
    const n = sorted.length

    const linReg = (vals: number[]) => {
      const sumX  = vals.reduce((s, _, i) => s + i, 0)
      const sumY  = vals.reduce((s, v) => s + v, 0)
      const sumXY = vals.reduce((s, v, i) => s + i * v, 0)
      const sumX2 = vals.reduce((s, _, i) => s + i * i, 0)
      const slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX)
      const intercept = (sumY - slope * sumX) / n
      return (x: number) => Math.max(0, slope * x + intercept)
    }

    const incomeReg  = linReg(incomeValues)
    const expenseReg = linReg(expenseValues)

    const result: ForecastPoint[] = sorted.map(([month, v], i) => ({
      month,
      actualIncome:    v.income,
      actualExpense:   v.expense,
      forecastIncome:  i === n - 1 ? incomeReg(n)  : null,
      forecastExpense: i === n - 1 ? expenseReg(n) : null,
    }))

    const lastMonth = sorted[n - 1][0]
    const [yr, mo]  = lastMonth.split('-').map(Number)
    for (let i = 1; i <= 2; i++) {
      const date  = new Date(yr, mo - 1 + i, 1)
      const label = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`
      result.push({ month: label, actualIncome: null, actualExpense: null, forecastIncome: incomeReg(n + i - 1), forecastExpense: expenseReg(n + i - 1) })
    }

    return result
  }, [transactions])

  return (
    <div className="chart-card">
      <div className="chart-card-header">
        <h4 className="chart-title">🔮 Cash Flow Forecast</h4>
        <p className="chart-subtitle">Actual + 2-month linear projection</p>
      </div>
      {data.length === 0 ? (
        <ChartEmpty message="Add transactions across multiple months to generate a forecast" />
      ) : (
        <ResponsiveContainer width="100%" height={260}>
          <LineChart data={data} margin={{ top: 10, right: 20, left: 0, bottom: 0 }}>
            <CartesianGrid stroke="#1f2d45" strokeDasharray="3 3" vertical={false} />
            <XAxis dataKey="month" tick={{ fill: '#4a5a7a', fontSize: 11 }} axisLine={false} tickLine={false} />
            <YAxis tickFormatter={yTick} tick={{ fill: '#4a5a7a', fontSize: 11 }} axisLine={false} tickLine={false} width={60} />
            <Tooltip content={<ChartTooltip />} />
            <Legend wrapperStyle={{ fontSize: '12px', color: '#8b9ec7', paddingTop: '12px' }} />
            <Line type="monotone" dataKey="actualIncome"    name="Actual Income"    stroke={C_INCOME}  strokeWidth={2.5} dot={{ r: 4, fill: C_INCOME }}  activeDot={{ r: 6 }} connectNulls={false} />
            <Line type="monotone" dataKey="actualExpense"   name="Actual Expense"   stroke={C_EXPENSE} strokeWidth={2.5} dot={{ r: 4, fill: C_EXPENSE }} activeDot={{ r: 6 }} connectNulls={false} />
            <Line type="monotone" dataKey="forecastIncome"  name="Forecast Income"  stroke={C_ACCENT}  strokeWidth={2}   strokeDasharray="6 4" dot={false} activeDot={{ r: 4 }} connectNulls={false} />
            <Line type="monotone" dataKey="forecastExpense" name="Forecast Expense" stroke={C_ACCENT2} strokeWidth={2}   strokeDasharray="6 4" dot={false} activeDot={{ r: 4 }} connectNulls={false} />
          </LineChart>
        </ResponsiveContainer>
      )}
    </div>
  )
}

// ═════════════════════════════════════════════════════════════════════════════
//  Export
// ═════════════════════════════════════════════════════════════════════════════
export default function ChartsSection({ transactions, breakdown }: ChartsSectionProps) {
  return (
    <div className="charts-section">
      <div className="charts-grid-2">
        <CashFlowChart   transactions={transactions} />
        <ExpensePieChart breakdown={breakdown} />
      </div>
      <ForecastChart transactions={transactions} />
    </div>
  )
}
