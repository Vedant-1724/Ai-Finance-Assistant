// PATH: finance-frontend/src/pages/SettingsPage.tsx
// Account settings — profile, email notifications, security, data export, danger zone.
// Calls:
//   GET  /api/v1/settings          → load preferences
//   POST /api/v1/settings          → save preferences
//   POST /api/v1/auth/change-password
//   GET  /api/v1/{companyId}/transactions/export?format=csv

import { useEffect, useState, useCallback } from 'react'
import api from '../api'
import { useAuth } from '../context/AuthContext'

interface EmailPrefs {
  anomalyAlerts: boolean
  forecastAlerts: boolean
  budgetAlerts: boolean
  weeklySummary: boolean
  trialReminders: boolean
}

interface UserSettings {
  email: string
  companyName: string
  currency: string
  emailPrefs: EmailPrefs
}

const CURRENCIES = ['INR', 'USD', 'EUR', 'GBP', 'AED', 'SGD']

export default function SettingsPage() {
  const { user, logout } = useAuth()
  const [settings, setSettings] = useState<UserSettings | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [saveMsg, setSaveMsg] = useState<string | null>(null)
  const [saveErr, setSaveErr] = useState<string | null>(null)

  // Password change
  const [pwCurrent, setPwCurrent] = useState('')
  const [pwNew, setPwNew] = useState('')
  const [pwConfirm, setPwConfirm] = useState('')
  const [pwSaving, setPwSaving] = useState(false)
  const [pwMsg, setPwMsg] = useState<string | null>(null)
  const [pwErr, setPwErr] = useState<string | null>(null)
  const [showPw, setShowPw] = useState(false)

  // Export
  const [exporting, setExporting] = useState(false)

  // ── Load ──────────────────────────────────────────────────────────────────
  const load = useCallback(async () => {
    setLoading(true)
    try {
      const res = await api.get<UserSettings>('/api/v1/settings')
      setSettings(res.data)
    } catch {
      // Fallback defaults so page always renders
      setSettings({
        email: user?.email ?? '',
        companyName: 'My Company',
        currency: 'INR',
        emailPrefs: {
          anomalyAlerts: true,
          forecastAlerts: true,
          budgetAlerts: true,
          weeklySummary: true,
          trialReminders: true,
        },
      })
    } finally { setLoading(false) }
  }, [user?.email])

  useEffect(() => { void load() }, [load])

  // ── Save settings ─────────────────────────────────────────────────────────
  const handleSave = async () => {
    if (!settings) return
    setSaving(true); setSaveErr(null); setSaveMsg(null)
    try {
      await api.post('/api/v1/settings', settings)
      setSaveMsg('✅ Settings saved successfully.')
      setTimeout(() => setSaveMsg(null), 3500)
    } catch {
      setSaveErr('Failed to save settings. Please try again.')
    } finally { setSaving(false) }
  }

  // ── Change password ───────────────────────────────────────────────────────
  const handleChangePassword = async () => {
    setPwErr(null); setPwMsg(null)
    if (!pwCurrent) { setPwErr('Enter your current password.'); return }
    if (pwNew.length < 8) { setPwErr('New password must be ≥ 8 characters.'); return }
    if (pwNew !== pwConfirm) { setPwErr('New passwords do not match.'); return }
    if (pwNew === pwCurrent) { setPwErr('New password must differ from current.'); return }

    setPwSaving(true)
    try {
      await api.post('/api/v1/auth/change-password',
        { currentPassword: pwCurrent, newPassword: pwNew },
        { headers }
      )
      setPwMsg('✅ Password changed. You will need to log in again.')
      setPwCurrent(''); setPwNew(''); setPwConfirm('')
      setTimeout(() => logout(), 2500)
    } catch (e: unknown) {
      const msg = (e as { response?: { data?: { error?: string } } })?.response?.data?.error
      setPwErr(msg ?? 'Failed to change password.')
    } finally { setPwSaving(false) }
  }

  // ── Export CSV ────────────────────────────────────────────────────────────
  const handleExport = async () => {
    setExporting(true)
    try {
      const res = await api.get(
        `/api/v1/${user?.companyId}/transactions/export?format=csv`,
        { headers, responseType: 'blob' }
      )
      const url = URL.createObjectURL(new Blob([res.data as BlobPart]))
      const link = document.createElement('a')
      link.href = url
      link.download = `transactions-${new Date().toISOString().slice(0, 10)}.csv`
      link.click()
      URL.revokeObjectURL(url)
    } catch {
      alert('Export failed. Please try again.')
    } finally { setExporting(false) }
  }

  const togglePref = (key: keyof EmailPrefs) => {
    if (!settings) return
    setSettings(prev => prev ? {
      ...prev,
      emailPrefs: { ...prev.emailPrefs, [key]: !prev.emailPrefs[key] }
    } : prev)
  }

  if (loading) return <div className="loading">⏳ Loading settings…</div>
  if (!settings) return null

  return (
    <div className="settings-page">
      <div className="page-header">
        <h1 className="page-title">⚙️ Settings</h1>
      </div>

      {saveMsg && <div className="success-toast">{saveMsg}</div>}
      {saveErr && <div className="error" style={{ marginBottom: 12 }}>{saveErr}</div>}

      {/* ── Profile ──────────────────────────────────────────────────────── */}
      <div className="settings-section">
        <div className="settings-section-title">👤 Profile</div>
        <div className="settings-section-desc">Your account and company information.</div>

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>
          <div className="form-group" style={{ marginBottom: 0 }}>
            <label>Email address</label>
            <input
              type="email" className="form-input"
              value={settings.email}
              onChange={e => setSettings(prev => prev ? { ...prev, email: e.target.value } : prev)}
            />
          </div>
          <div className="form-group" style={{ marginBottom: 0 }}>
            <label>Company name</label>
            <input
              type="text" className="form-input"
              value={settings.companyName}
              onChange={e => setSettings(prev => prev ? { ...prev, companyName: e.target.value } : prev)}
            />
          </div>
          <div className="form-group" style={{ marginBottom: 0 }}>
            <label>Default currency</label>
            <select
              className="form-select"
              value={settings.currency}
              onChange={e => setSettings(prev => prev ? { ...prev, currency: e.target.value } : prev)}
            >
              {CURRENCIES.map(c => <option key={c}>{c}</option>)}
            </select>
          </div>
        </div>

        <div style={{ marginTop: 16, display: 'flex', justifyContent: 'flex-end' }}>
          <button className="btn-primary" onClick={handleSave} disabled={saving}>
            {saving ? '⏳ Saving…' : '💾 Save Changes'}
          </button>
        </div>
      </div>

      {/* ── Email Notifications ───────────────────────────────────────────── */}
      <div className="settings-section">
        <div className="settings-section-title">🔔 Email Notifications</div>
        <div className="settings-section-desc">
          Control which emails FinanceAI sends to {settings.email}.
        </div>

        {(
          [
            { key: 'anomalyAlerts', label: 'Anomaly Alerts', desc: 'Email when unusual transactions are detected' },
            { key: 'forecastAlerts', label: 'Forecast Alerts', desc: 'Weekly cash flow forecast summary' },
            { key: 'budgetAlerts', label: 'Budget Alerts', desc: 'Alert when spending exceeds budget limits' },
            { key: 'weeklySummary', label: 'Weekly Summary', desc: 'Monday morning P&L and health score digest' },
            { key: 'trialReminders', label: 'Trial Reminders', desc: 'Reminder emails before your trial expires' },
          ] as { key: keyof EmailPrefs; label: string; desc: string }[]
        ).map(({ key, label, desc }) => (
          <div key={key} className="settings-row">
            <div>
              <div className="settings-row-label">{label}</div>
              <div className="settings-row-desc">{desc}</div>
            </div>
            <label className="toggle-switch">
              <input
                type="checkbox"
                checked={settings.emailPrefs[key]}
                onChange={() => togglePref(key)}
              />
              <span className="toggle-slider" />
            </label>
          </div>
        ))}

        <div style={{ marginTop: 16, display: 'flex', justifyContent: 'flex-end' }}>
          <button className="btn-primary" onClick={handleSave} disabled={saving}>
            {saving ? '⏳ Saving…' : '💾 Save Preferences'}
          </button>
        </div>
      </div>

      {/* ── Security ──────────────────────────────────────────────────────── */}
      <div className="settings-section">
        <div className="settings-section-title">🔐 Security</div>
        <div className="settings-section-desc">Change your password. You'll be logged out after a successful change.</div>

        {pwMsg && <div className="success-toast" style={{ marginBottom: 12 }}>{pwMsg}</div>}
        {pwErr && <div className="auth-error" style={{ marginBottom: 12 }}>{pwErr}</div>}

        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <div className="form-group" style={{ marginBottom: 0 }}>
            <label>Current password</label>
            <div className="input-wrapper">
              <input
                type={showPw ? 'text' : 'password'}
                className="form-input"
                placeholder="Enter current password"
                value={pwCurrent}
                onChange={e => setPwCurrent(e.target.value)}
              />
              <button className="input-icon" type="button" onClick={() => setShowPw(p => !p)}>
                {showPw ? '🙈' : '👁️'}
              </button>
            </div>
          </div>
          <div className="form-group" style={{ marginBottom: 0 }}>
            <label>New password</label>
            <input
              type={showPw ? 'text' : 'password'}
              className="form-input"
              placeholder="Min. 8 characters"
              value={pwNew}
              onChange={e => setPwNew(e.target.value)}
            />
          </div>
          <div className="form-group" style={{ marginBottom: 0 }}>
            <label>Confirm new password</label>
            <input
              type={showPw ? 'text' : 'password'}
              className="form-input"
              placeholder="Repeat new password"
              value={pwConfirm}
              onChange={e => setPwConfirm(e.target.value)}
            />
          </div>
        </div>

        {/* Password strength indicator */}
        {pwNew && (
          <div style={{ marginTop: 8 }}>
            <div style={{ background: 'rgba(255,255,255,0.05)', borderRadius: 4, height: 4, overflow: 'hidden' }}>
              <div style={{
                height: '100%', borderRadius: 4, transition: 'width 0.3s',
                width: `${Math.min(100, pwNew.length * 8)}%`,
                background: pwNew.length < 8 ? '#ef4444' : pwNew.length < 12 ? '#f59e0b' : '#22c55e'
              }} />
            </div>
            <span style={{ fontSize: 11, color: '#94a3b8', marginTop: 3, display: 'block' }}>
              {pwNew.length < 8 ? 'Too short' : pwNew.length < 12 ? 'Good' : 'Strong'} password
            </span>
          </div>
        )}

        <div style={{ marginTop: 16, display: 'flex', justifyContent: 'flex-end' }}>
          <button className="btn-primary" onClick={handleChangePassword} disabled={pwSaving}>
            {pwSaving ? '⏳ Changing…' : '🔐 Change Password'}
          </button>
        </div>
      </div>

      {/* ── Data Export ───────────────────────────────────────────────────── */}
      <div className="settings-section">
        <div className="settings-section-title">📦 Data Export</div>
        <div className="settings-section-desc">Download your data at any time. CSV includes all transactions.</div>

        <div className="settings-row">
          <div>
            <div className="settings-row-label">Export Transactions</div>
            <div className="settings-row-desc">Download all transactions as a CSV file</div>
          </div>
          <button className="btn-secondary" onClick={handleExport} disabled={exporting}>
            {exporting ? '⏳ Exporting…' : '⬇️ Download CSV'}
          </button>
        </div>
      </div>

      {/* ── Danger Zone ───────────────────────────────────────────────────── */}
      <div className="settings-section danger-zone">
        <div className="settings-section-title">⚠️ Danger Zone</div>
        <div className="settings-section-desc">Irreversible actions. Proceed with caution.</div>

        <div className="settings-row">
          <div>
            <div className="settings-row-label">Log Out</div>
            <div className="settings-row-desc">Sign out of your account on this device</div>
          </div>
          <button className="btn-secondary" onClick={logout}>
            🚪 Log Out
          </button>
        </div>

        <div className="settings-row">
          <div>
            <div className="settings-row-label" style={{ color: '#f87171' }}>Delete Account</div>
            <div className="settings-row-desc">
              Permanently deletes all data. This cannot be undone.
            </div>
          </div>
          <button
            className="btn-danger"
            onClick={() => {
              if (confirm(
                'Are you absolutely sure you want to delete your account?\n\n' +
                'This will permanently delete ALL your transactions, reports, ' +
                'invoices, and company data. This action CANNOT be undone.'
              )) {
                api.delete('/api/v1/account')
                  .then(() => logout())
                  .catch(() => alert('Account deletion failed. Please contact support.'))
              }
            }}
          >
            🗑️ Delete Account
          </button>
        </div>
      </div>
    </div>
  )
}
