// PATH: finance-frontend/src/pages/SettingsPage.tsx
import { useState } from 'react'
import api from '../api'
import { useAuth } from '../context/AuthContext'

export default function SettingsPage() {
  const { user } = useAuth()
  const [exporting, setExporting]   = useState<'pdf'|'csv'|null>(null)
  const [exportMsg, setExportMsg]   = useState<string|null>(null)
  const companyId = user?.companyId

  const headers = { Authorization: `Bearer ${user?.token}` }

  const handleExport = async (type: 'pdf' | 'csv') => {
    setExporting(type); setExportMsg(null)
    try {
      const res = await api.get(`/api/v1/${companyId}/export/${type}`, {
        headers, responseType: 'blob'
      })
      const url  = URL.createObjectURL(new Blob([res.data]))
      const link = document.createElement('a')
      const date = new Date().toISOString().slice(0,10)
      link.href = url
      link.download = `transactions-${date}.${type}`
      link.click()
      URL.revokeObjectURL(url)
      setExportMsg(`✅ ${type.toUpperCase()} downloaded successfully`)
    } catch (e: any) {
      if (e?.response?.status === 402) setExportMsg('⚠️ PDF export requires Trial or Pro. CSV is free.')
      else setExportMsg('❌ Export failed. Please try again.')
    } finally { setExporting(null) }
    setTimeout(() => setExportMsg(null), 4000)
  }

  return (
    <div className="settings-page">
      <div className="page-header">
        <h1 className="page-title">⚙️ Settings</h1>
      </div>

      {/* Export Section */}
      <div className="card" style={{marginBottom:24}}>
        <h3 style={{marginTop:0, color:'#e2e8f0'}}>📥 Export Your Data</h3>
        <p style={{color:'#94a3b8', marginBottom:16}}>Download all your transactions for backup or your accountant.</p>
        {exportMsg && <div className="success-toast" style={{marginBottom:12}}>{exportMsg}</div>}
        <div style={{display:'flex', gap:12, flexWrap:'wrap'}}>
          <button className="btn-secondary" onClick={() => handleExport('csv')} disabled={exporting !== null}
                  style={{display:'flex', alignItems:'center', gap:8}}>
            {exporting === 'csv' ? '⏳ Exporting...' : '📊 Export CSV'} <span style={{color:'#10b981', fontSize:11}}>FREE</span>
          </button>
          <button className="btn-primary" onClick={() => handleExport('pdf')} disabled={exporting !== null}
                  style={{display:'flex', alignItems:'center', gap:8}}>
            {exporting === 'pdf' ? '⏳ Generating PDF...' : '📄 Export PDF'} <span style={{color:'#a78bfa', fontSize:11}}>PRO</span>
          </button>
        </div>
      </div>

      {/* Account Info */}
      <div className="card" style={{marginBottom:24}}>
        <h3 style={{marginTop:0, color:'#e2e8f0'}}>👤 Account</h3>
        <div style={{color:'#94a3b8'}}>
          <div style={{marginBottom:8}}><strong style={{color:'#e2e8f0'}}>Email:</strong> {user?.email}</div>
          <div style={{marginBottom:8}}><strong style={{color:'#e2e8f0'}}>Plan:</strong> {' '}
            <span style={{color: user?.subscriptionTier === 'ACTIVE' ? '#10b981' : '#f59e0b'}}>
              {user?.subscriptionTier || 'FREE'}
            </span>
          </div>
          <div><strong style={{color:'#e2e8f0'}}>AI Chats Today:</strong> {user?.aiChatsRemaining} remaining</div>
        </div>
      </div>

      {/* Email Notification Preferences */}
      <div className="card">
        <h3 style={{marginTop:0, color:'#e2e8f0'}}>🔔 Email Notifications</h3>
        <p style={{color:'#64748b', fontSize:13}}>
          Email notifications are sent automatically. To manage preferences, update your profile.
          Currently configured via <code style={{color:'#3b82f6'}}>application.yaml → app.mail.enabled</code>.
        </p>
        <div style={{color:'#94a3b8', fontSize:13, lineHeight:1.8}}>
          ✅ Anomaly detection alerts<br/>
          ✅ Cash flow warnings<br/>
          ✅ Budget threshold alerts (90%+)<br/>
          ✅ Trial expiry reminders<br/>
          ✅ Monthly financial health score
        </div>
      </div>
    </div>
  )
}
