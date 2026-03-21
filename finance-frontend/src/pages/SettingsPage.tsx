import { useCallback, useEffect, useState } from 'react'
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
  const { user, logout, updateProfile, capabilities } = useAuth()
  const [settings, setSettings] = useState<UserSettings | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [saveMsg, setSaveMsg] = useState<string | null>(null)
  const [saveErr, setSaveErr] = useState<string | null>(null)

  const [pwCurrent, setPwCurrent] = useState('')
  const [pwNew, setPwNew] = useState('')
  const [pwConfirm, setPwConfirm] = useState('')
  const [pwSaving, setPwSaving] = useState(false)
  const [pwMsg, setPwMsg] = useState<string | null>(null)
  const [pwErr, setPwErr] = useState<string | null>(null)
  const [showPw, setShowPw] = useState(false)

  const [exporting, setExporting] = useState(false)
  const [exportMsg, setExportMsg] = useState<string | null>(null)
  const [dangerErr, setDangerErr] = useState<string | null>(null)
  const [confirmDeleteOpen, setConfirmDeleteOpen] = useState(false)

  const loadSettings = useCallback(async () => {
    setLoading(true)
    try {
      const res = await api.get<UserSettings>('/api/v1/settings')
      const d = res.data
      setSettings({
        email: d?.email ?? user?.email ?? '',
        companyName: d?.companyName ?? 'My Company',
        currency: d?.currency ?? 'INR',
        emailPrefs: {
          anomalyAlerts: d?.emailPrefs?.anomalyAlerts ?? true,
          forecastAlerts: d?.emailPrefs?.forecastAlerts ?? true,
          budgetAlerts: d?.emailPrefs?.budgetAlerts ?? true,
          weeklySummary: d?.emailPrefs?.weeklySummary ?? true,
          trialReminders: d?.emailPrefs?.trialReminders ?? true,
        },
      })
    } catch {
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
    } finally {
      setLoading(false)
    }
  }, [user?.email])

  useEffect(() => {
    void loadSettings()
  }, [loadSettings])

  const handleSave = async () => {
    if (!settings) return

    setSaving(true)
    setSaveErr(null)
    setSaveMsg(null)

    try {
      const res = await api.post<UserSettings>('/api/v1/settings', settings)
      setSettings(res.data)
      updateProfile(res.data.email)
      setSaveMsg('Settings saved successfully.')
      window.setTimeout(() => setSaveMsg(null), 3500)
    } catch {
      setSaveErr('Failed to save settings. Please try again.')
    } finally {
      setSaving(false)
    }
  }

  const handleChangePassword = async () => {
    setPwErr(null)
    setPwMsg(null)

    if (!pwCurrent) {
      setPwErr('Enter your current password.')
      return
    }
    if (pwNew.length < 8) {
      setPwErr('New password must be at least 8 characters.')
      return
    }
    if (pwNew !== pwConfirm) {
      setPwErr('New passwords do not match.')
      return
    }
    if (pwNew === pwCurrent) {
      setPwErr('New password must differ from your current password.')
      return
    }

    setPwSaving(true)
    try {
      const res = await api.post<{ message?: string }>('/api/v1/auth/change-password', {
        currentPassword: pwCurrent,
        newPassword: pwNew,
      })
      setPwMsg(res.data.message || 'Password changed. You will need to log in again.')
      setPwCurrent('')
      setPwNew('')
      setPwConfirm('')
      window.setTimeout(() => {
        void logout()
      }, 2500)
    } catch (error: unknown) {
      const data = (error as { response?: { data?: { error?: string; message?: string } } })?.response?.data
      setPwErr(data?.error ?? data?.message ?? 'Failed to change password.')
    } finally {
      setPwSaving(false)
    }
  }

  const handleExport = async () => {
    if (!user?.companyId) {
      setDangerErr('Company information is missing. Please log in again.')
      return
    }

    setExporting(true)
    setExportMsg(null)
    setDangerErr(null)
    try {
      const res = await api.get(`/api/v1/${user.companyId}/export/csv`, {
        responseType: 'blob',
      })
      const url = URL.createObjectURL(new Blob([res.data as BlobPart]))
      const link = document.createElement('a')
      link.href = url
      link.download = `transactions-${new Date().toISOString().slice(0, 10)}.csv`
      link.click()
      URL.revokeObjectURL(url)
      setExportMsg('Transaction export started successfully.')
    } catch {
      setDangerErr('Export failed. Please try again.')
    } finally {
      setExporting(false)
    }
  }

  const handleDeleteAccount = async () => {
    setDangerErr(null)
    try {
      await api.delete('/api/v1/account')
      await logout()
    } catch {
      setDangerErr('Account deletion failed. Please contact support.')
    } finally {
      setConfirmDeleteOpen(false)
    }
  }

  const togglePref = (key: keyof EmailPrefs) => {
    if (!settings) return

    setSettings(current => current ? {
      ...current,
      emailPrefs: {
        ...(current.emailPrefs ?? { anomalyAlerts: true, forecastAlerts: true, budgetAlerts: true, weeklySummary: true, trialReminders: true }),
        [key]: !(current.emailPrefs?.[key] ?? true),
      },
    } : current)
  }

  if (loading) {
    return <div className="loading">⏳ Loading settings…</div>
  }

  if (!settings) {
    return null
  }

  const notificationOptions: Array<{ key: keyof EmailPrefs; label: string; desc: string }> = [
    { key: 'anomalyAlerts', label: 'Anomaly Alerts', desc: 'Email when unusual transactions are detected' },
    { key: 'forecastAlerts', label: 'Forecast Alerts', desc: 'Weekly cash flow forecast summary' },
    { key: 'budgetAlerts', label: 'Budget Alerts', desc: 'Alert when spending exceeds budget limits' },
    { key: 'weeklySummary', label: 'Weekly Summary', desc: 'Monday morning P&L and health score digest' },
  ]

  if (user?.subscriptionTier !== 'ACTIVE' && user?.subscriptionTier !== 'MAX') {
    notificationOptions.push({
      key: 'trialReminders',
      label: 'Trial Reminders',
      desc: 'Reminder emails before your trial expires',
    })
  }

  return (
    <div className="settings-page">
      <div className="page-header">
        <h1 className="page-title">⚙️ Settings</h1>
      </div>

      {saveMsg && <div className="success-toast">✅ {saveMsg}</div>}
      {saveErr && <div className="error" style={{ marginBottom: 12 }}>❌ {saveErr}</div>}
      {exportMsg && <div className="success-toast">✅ {exportMsg}</div>}
      {dangerErr && <div className="error" style={{ marginBottom: 12 }}>❌ {dangerErr}</div>}

      <div className="settings-section">
        <div className="settings-section-title">👤 Profile</div>
        <div className="settings-section-desc">Your personal account information.</div>

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>
          <div className="form-group" style={{ marginBottom: 0, gridColumn: '1 / -1' }}>
            <label>Email address</label>
            <input
              type="email"
              className="form-input"
              value={settings.email}
              onChange={event => setSettings(current => current ? { ...current, email: event.target.value } : current)}
            />
          </div>
        </div>

        <div style={{ marginTop: 16, display: 'flex', justifyContent: 'flex-end' }}>
          <button className="btn-primary" onClick={handleSave} disabled={saving}>
            {saving ? '⏳ Saving…' : '💾 Save Profile'}
          </button>
        </div>
      </div>

      <div className="settings-section">
        <div className="settings-section-title">🏢 Workspace Profile</div>
        <div className="settings-section-desc">
          {capabilities.canManageCompanyProfile
            ? 'Update your company name and default currency.'
            : 'Only the workspace owner can edit company name and currency.'}
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>
          <div className="form-group" style={{ marginBottom: 0 }}>
            <label>Company name</label>
            <input
              type="text"
              className="form-input"
              value={settings.companyName}
              onChange={event => setSettings(current => current ? { ...current, companyName: event.target.value } : current)}
              disabled={!capabilities.canManageCompanyProfile}
            />
          </div>
          <div className="form-group" style={{ marginBottom: 0 }}>
            <label>Default currency</label>
            <select
              className="form-select"
              value={settings.currency}
              onChange={event => setSettings(current => current ? { ...current, currency: event.target.value } : current)}
              disabled={!capabilities.canManageCompanyProfile}
            >
              {CURRENCIES.map(currency => (
                <option key={currency} value={currency}>{currency}</option>
              ))}
            </select>
          </div>
        </div>

        <div style={{ marginTop: 16, display: 'flex', justifyContent: 'flex-end' }}>
          <button className="btn-primary" onClick={handleSave} disabled={saving || !capabilities.canManageCompanyProfile}>
            {saving ? '⏳ Saving…' : capabilities.canManageCompanyProfile ? '💾 Save Workspace' : 'Read-only Workspace'}
          </button>
        </div>
      </div>

      <div className="settings-section">
        <div className="settings-section-title">🔔 Email Notifications</div>
        <div className="settings-section-desc">
          Control which emails FinanceAI sends to {settings.email}.
        </div>

        {notificationOptions.map(({ key, label, desc }) => (
          <div key={key} className="settings-row">
            <div>
              <div className="settings-row-label">{label}</div>
              <div className="settings-row-desc">{desc}</div>
            </div>
            <label className="toggle-switch">
              <input
                type="checkbox"
                checked={settings.emailPrefs?.[key] ?? true}
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

      <div className="settings-section">
        <div className="settings-section-title">🔐 Security</div>
        <div className="settings-section-desc">Change your password. You will be logged out after a successful change.</div>

        {pwMsg && <div className="success-toast" style={{ marginBottom: 12 }}>✅ {pwMsg}</div>}
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
                onChange={event => setPwCurrent(event.target.value)}
              />
              <button className="input-icon" type="button" onClick={() => setShowPw(current => !current)}>
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
              onChange={event => setPwNew(event.target.value)}
            />
          </div>
          <div className="form-group" style={{ marginBottom: 0 }}>
            <label>Confirm new password</label>
            <input
              type={showPw ? 'text' : 'password'}
              className="form-input"
              placeholder="Repeat new password"
              value={pwConfirm}
              onChange={event => setPwConfirm(event.target.value)}
            />
          </div>
        </div>

        {pwNew && (
          <div style={{ marginTop: 8 }}>
            <div style={{ background: 'rgba(255,255,255,0.05)', borderRadius: 4, height: 4, overflow: 'hidden' }}>
              <div
                style={{
                  height: '100%',
                  borderRadius: 4,
                  transition: 'width 0.3s',
                  width: `${Math.min(100, pwNew.length * 8)}%`,
                  background: pwNew.length < 8 ? '#ef4444' : pwNew.length < 12 ? '#f59e0b' : '#22c55e',
                }}
              />
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

      <div className="settings-section">
        <div className="settings-section-title">📦 Data Export</div>
        <div className="settings-section-desc">Download your transactions as a CSV file at any time.</div>

        <div className="settings-row">
          <div>
            <div className="settings-row-label">Export Transactions</div>
            <div className="settings-row-desc">Download all transactions as CSV</div>
          </div>
          <button className="btn-secondary" onClick={handleExport} disabled={exporting}>
            {exporting ? '⏳ Exporting…' : '⬇️ Download CSV'}
          </button>
        </div>
      </div>

      <div className="settings-section danger-zone">
        <div className="settings-section-title">⚠️ Danger Zone</div>
        <div className="settings-section-desc">
          {capabilities.canManageCompanyProfile
            ? 'Irreversible actions. Proceed with caution.'
            : 'Workspace-destructive actions are only available to the workspace owner.'}
        </div>

        <div className="settings-row">
          <div>
            <div className="settings-row-label">Log Out</div>
            <div className="settings-row-desc">Sign out of your account on this device</div>
          </div>
          <button className="btn-secondary" onClick={() => { void logout() }}>
            🚪 Log Out
          </button>
        </div>

        {capabilities.canManageCompanyProfile ? (
          <div className="settings-row">
            <div>
              <div className="settings-row-label" style={{ color: '#f87171' }}>Delete Account</div>
              <div className="settings-row-desc">Permanently delete all company data. This cannot be undone.</div>
            </div>
            <button className="btn-danger" onClick={() => setConfirmDeleteOpen(true)}>
              🗑️ Delete Account
            </button>
          </div>
        ) : (
          <div className="settings-row">
            <div>
              <div className="settings-row-label">Workspace deletion</div>
              <div className="settings-row-desc">Only the workspace owner can delete the account and company data.</div>
            </div>
            <button className="btn-secondary" disabled>
              Owner only
            </button>
          </div>
        )}
      </div>

      {confirmDeleteOpen && (
        <div className="modal-overlay" onClick={event => { if (event.target === event.currentTarget) setConfirmDeleteOpen(false) }}>
          <div className="modal-box" style={{ maxWidth: 420 }}>
            <div className="modal-header">
              <h2 className="modal-title">Delete Workspace Account</h2>
              <button className="modal-close" onClick={() => setConfirmDeleteOpen(false)}>×</button>
            </div>
            <p style={{ margin: '16px 0', color: 'var(--text-secondary)', lineHeight: 1.6 }}>
              This permanently deletes the workspace, team data, transactions, and settings. This action cannot be undone.
            </p>
            <div className="modal-footer">
              <button className="btn-secondary" onClick={() => setConfirmDeleteOpen(false)}>
                Cancel
              </button>
              <button className="btn-danger" onClick={() => { void handleDeleteAccount() }}>
                Delete permanently
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
