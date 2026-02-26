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
//  Interfaces
// ─────────────────────────────────────────────────────────────────────────────
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
//  Shared tooltip  (module-level — never recreated during render)
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
      {label && <p className="chart-tooltip-label">{label}</p>}
      {payload.map((entry, i) => (
        <p key={i} className="chart-tooltip-row" style={{ color: entry.color }}>
          <span className="chart-tooltip-name">{entry.name}</span>
          <span className="chart-tooltip-val">{inr(entry.value)}</span>
        </p>
      ))}
    </div>
  )
}

// ─────────────────────────────────────────────────────────────────────────────
//  Pie tooltip  (module-level — uses recharts' built-in `percent` field so
//  it needs NO closure over `total` and is never created during render)
// ─────────────────────────────────────────────────────────────────────────────

// Recharts internally attaches a `percent` field (0-1) to each pie payload item.
interface PiePayloadItem {
  name:    unknown
  value:   unknown
  percent: unknown
  fill?:   string
}

interface PieTooltipProps {
  active?:  boolean
  payload?: PiePayloadItem[]
}

function PieTooltip({ active, payload }: PieTooltipProps) {
  if (!active || !payload?.length) return null

  const item    = payload[0]
  const name    = typeof item.name    === 'string' ? item.name    : ''
  const value   = typeof item.value   === 'number' ? item.value   : 0
  const percent = typeof item.percent === 'number' ? item.percent : 0

  return (
    <div className="chart-tooltip">
      <p className="chart-tooltip-label">{name}</p>
      <p className="chart-tooltip-row" style={{ color: C_EXPENSE }}>
        <span className="chart-tooltip-name">Amount</span>
        <span className="chart-tooltip-val">{inr(value)}</span>
      </p>
      <p className="chart-tooltip-row" style={{ color: '#8b9ec7' }}>
        <span className="chart-tooltip-name">Share</span>
        <span className="chart-tooltip-val">{(percent * 100).toFixed(1)}%</span>
      </p>
    </div>
  )
}

// ─────────────────────────────────────────────────────────────────────────────
//  Pie label  (module-level — all props optional to match recharts' label API)
// ─────────────────────────────────────────────────────────────────────────────
interface PieLabelProps {
  cx?:          number
  cy?:          number
  midAngle?:    number
  outerRadius?: number
  percent?:     number
  name?:        string
}

function PieLabel({
  cx          = 0,
  cy          = 0,
  midAngle    = 0,
  outerRadius = 0,
  percent     = 0,
  name        = '',
}: PieLabelProps) {
  if (percent < 0.05) return null
  const RADIAN = Math.PI / 180
  const radius = outerRadius + 28
  const x = cx + radius * Math.cos(-midAngle * RADIAN)
  const y = cy + radius * Math.sin(-midAngle * RADIAN)
  return (
    <text
      x={x}
      y={y}
      fill="#8b9ec7"
      textAnchor={x > cx ? 'start' : 'end'}
      dominantBaseline="central"
      fontSize={11}
    >
      {`${name} (${(percent * 100).toFixed(0)}%)`}
    </text>
  )
}

// ─────────────────────────────────────────────────────────────────────────────
//  Empty state  (module-level)
// ─────────────────────────────────────────────────────────────────────────────
function ChartEmpty({ message }: { message: string }) {
  return (
    <div className="chart-empty">
      <span className="chart-empty-icon">📊</span>
      <p>{message}</p>
    </div>
  )
}

// ═════════════════════════════════════════════════════════════════════════════
//  CHART 1 — Cash Flow Over Time  (Area)
// ═════════════════════════════════════════════════════════════════════════════
function CashFlowChart({ transactions }: { transactions: Transaction[] }) {
  const data = useMemo(() => {
    const byDate = new Map<string, { income: number; expense: number }>()
    for (const txn of transactions) {
      const curr = byDate.get(txn.date) ?? { income: 0, expense: 0 }
      if (txn.amount > 0) curr.income  += txn.amount
      else                curr.expense += Math.abs(txn.amount)
      byDate.set(txn.date, curr)
    }
    return Array.from(byDate.entries())
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([date, vals]) => ({
        date:    date.slice(5),
        income:  Math.round(vals.income),
        expense: Math.round(vals.expense),
      }))
  }, [transactions])

  return (
    <div className="chart-card">
      <div className="chart-card-header">
        <h4 className="chart-title">📈 Cash Flow Over Time</h4>
        <p className="chart-subtitle">Daily income vs expenses</p>
      </div>
      {data.length < 2 ? (
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
            <XAxis
              dataKey="date"
              tick={{ fill: '#4a5a7a', fontSize: 11 }}
              axisLine={false}
              tickLine={false}
            />
            <YAxis
              tickFormatter={yTick}
              tick={{ fill: '#4a5a7a', fontSize: 11 }}
              axisLine={false}
              tickLine={false}
              width={60}
            />
            <Tooltip content={<ChartTooltip />} />
            <Area
              type="monotone"
              dataKey="income"
              name="Income"
              stroke={C_INCOME}
              strokeWidth={2.5}
              fill="url(#gradIncome)"
              dot={false}
              activeDot={{ r: 5, fill: C_INCOME }}
            />
            <Area
              type="monotone"
              dataKey="expense"
              name="Expense"
              stroke={C_EXPENSE}
              strokeWidth={2.5}
              fill="url(#gradExpense)"
              dot={false}
              activeDot={{ r: 5, fill: C_EXPENSE }}
            />
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
      .map(b => ({ name: b.categoryName, value: Math.round(Number(b.amount)) }))
      .filter(b => b.value > 0),
    [breakdown]
  )

  const total = useMemo(
    () => pieData.reduce((s, b) => s + b.value, 0),
    [pieData]
  )

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
            <Pie
              data={pieData}
              cx="50%"
              cy="50%"
              outerRadius={85}
              dataKey="value"
              labelLine={false}
              label={PieLabel}
            >
              {pieData.map((_entry, index) => (
                <Cell
                  key={`cell-${index}`}
                  fill={PIE_COLORS[index % PIE_COLORS.length]}
                  stroke="transparent"
                />
              ))}
            </Pie>
            {/* PieTooltip is a stable module-level component — no render-time creation */}
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
      const month = txn.date.slice(0, 7)
      const curr  = byMonth.get(month) ?? { income: 0, expense: 0 }
      if (txn.amount > 0) curr.income  += txn.amount
      else                curr.expense += Math.abs(txn.amount)
      byMonth.set(month, curr)
    }

    const sorted = Array.from(byMonth.entries())
      .sort(([a], [b]) => a.localeCompare(b))

    if (sorted.length === 0) return []

    const avgIncome  = sorted.reduce((s, [, v]) => s + v.income,  0) / sorted.length
    const avgExpense = sorted.reduce((s, [, v]) => s + v.expense, 0) / sorted.length

    const result: ForecastPoint[] = sorted.map(([month, vals]) => ({
      month,
      actualIncome:    Math.round(vals.income),
      actualExpense:   Math.round(vals.expense),
      forecastIncome:  null,
      forecastExpense: null,
    }))

    // Bridge the last actual point into the forecast line
    const last = result[result.length - 1]
    if (last) {
      last.forecastIncome  = Math.round(avgIncome)
      last.forecastExpense = Math.round(avgExpense)
    }

    // Append 6 future months
    const lastEntry = sorted[sorted.length - 1]
    if (!lastEntry) return result

    const yr = parseInt(lastEntry[0].slice(0, 4), 10)
    const mo = parseInt(lastEntry[0].slice(5, 7), 10)

    for (let i = 1; i <= 6; i++) {
      const d   = new Date(yr, mo - 1 + i, 1)
      const key = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
      result.push({
        month:           key,
        actualIncome:    null,
        actualExpense:   null,
        forecastIncome:  Math.round(avgIncome),
        forecastExpense: Math.round(avgExpense),
      })
    }
    return result
  }, [transactions])

  return (
    <div className="chart-card chart-card-full">
      <div className="chart-card-header">
        <h4 className="chart-title">🔮 6-Month Forecast</h4>
        <p className="chart-subtitle">
          <span className="forecast-legend-actual">━━</span>&nbsp;Actual&nbsp;&nbsp;
          <span className="forecast-legend-forecast">╌╌</span>&nbsp;Projected (avg-based)
        </p>
      </div>
      {data.length < 2 ? (
        <ChartEmpty message="Add transactions across multiple months to generate a forecast" />
      ) : (
        <ResponsiveContainer width="100%" height={260}>
          <LineChart data={data} margin={{ top: 10, right: 20, left: 0, bottom: 0 }}>
            <CartesianGrid stroke="#1f2d45" strokeDasharray="3 3" vertical={false} />
            <XAxis
              dataKey="month"
              tick={{ fill: '#4a5a7a', fontSize: 11 }}
              axisLine={false}
              tickLine={false}
            />
            <YAxis
              tickFormatter={yTick}
              tick={{ fill: '#4a5a7a', fontSize: 11 }}
              axisLine={false}
              tickLine={false}
              width={60}
            />
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
