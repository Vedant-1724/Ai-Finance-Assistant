// PATH: finance-frontend/src/pages/TeamPage.tsx
import { useEffect, useState, useCallback } from 'react'
import api from '../api'
import { useAuth } from '../context/AuthContext'

interface Member {
  id: number; companyId: number; userId: number | null; role: string;
  inviteEmail: string | null; acceptedAt: string | null; createdAt: string
}

export default function TeamPage({ companyId }: { companyId: number }) {
  const { user } = useAuth()
  const [members, setMembers]   = useState<Member[]>([])
  const [loading, setLoading]   = useState(true)
  const [error, setError]       = useState<string | null>(null)
  const [showInvite, setShowInvite] = useState(false)
  const [email, setEmail]       = useState('')
  const [role, setRole]         = useState('VIEWER')
  const [inviting, setInviting] = useState(false)
  const [msg, setMsg]           = useState<string | null>(null)

  const headers = { Authorization: `Bearer ${user?.token}` }

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const res = await api.get<Member[]>(`/api/v1/${companyId}/team`, { headers })
      setMembers(res.data)
    } catch (e: any) {
      if (e?.response?.status === 402) setError('UPGRADE_REQUIRED')
      else setError('Failed to load team')
    } finally { setLoading(false) }
  }, [companyId, user?.token])

  useEffect(() => { void load() }, [load])

  const handleInvite = async () => {
    if (!email) return
    setInviting(true)
    try {
      await api.post(`/api/v1/${companyId}/team/invite`, { email, role }, { headers })
      setMsg('✅ Invite sent to ' + email)
      setEmail(''); setShowInvite(false)
      void load()
    } catch { setMsg('❌ Failed to send invite') } finally { setInviting(false) }
    setTimeout(() => setMsg(null), 3000)
  }

  const handleRemove = async (id: number) => {
    if (!confirm('Remove this team member?')) return
    await api.delete(`/api/v1/${companyId}/team/${id}`, { headers })
    void load()
  }

  if (loading) return <div className="loading">⏳ Loading team...</div>
  if (error === 'UPGRADE_REQUIRED') return (
    <div className="upgrade-gate">
      <div style={{fontSize:48}}>👥</div>
      <h2>Team Access requires Pro</h2>
      <p>Invite your accountant or team members to view your financial dashboard.</p>
      <a href="/subscription" className="btn-primary">Upgrade to Pro</a>
    </div>
  )

  const roleColor = (r: string) => r === 'OWNER' ? '#10b981' : r === 'EDITOR' ? '#3b82f6' : '#94a3b8'

  return (
    <div className="team-page">
      <div className="page-header">
        <div>
          <h1 className="page-title">👥 Team Access</h1>
          <p className="page-subtitle">Invite accountants or team members to your dashboard</p>
        </div>
        <button className="btn-primary" onClick={() => setShowInvite(true)}>+ Invite Member</button>
      </div>

      {msg && <div className="success-toast">{msg}</div>}

      {showInvite && (
        <div className="modal-overlay" onClick={() => setShowInvite(false)}>
          <div className="modal-box" onClick={e => e.stopPropagation()}>
            <h2>Invite Team Member</h2>
            <div className="form-group">
              <label>Email Address</label>
              <input className="form-input" type="email" placeholder="accountant@firm.com"
                     value={email} onChange={e => setEmail(e.target.value)} />
            </div>
            <div className="form-group">
              <label>Role</label>
              <select className="form-input" value={role} onChange={e => setRole(e.target.value)}>
                <option value="VIEWER">Viewer — read-only access</option>
                <option value="EDITOR">Editor — can add/edit transactions</option>
              </select>
            </div>
            <div style={{display:'flex', gap:8, justifyContent:'flex-end'}}>
              <button className="btn-secondary" onClick={() => setShowInvite(false)}>Cancel</button>
              <button className="btn-primary" onClick={handleInvite} disabled={inviting || !email}>
                {inviting ? 'Sending...' : 'Send Invite'}
              </button>
            </div>
          </div>
        </div>
      )}

      <div className="card">
        {members.length === 0 ? (
          <div className="empty-state" style={{padding:32}}>
            <p>No team members yet. Invite your accountant to get started.</p>
          </div>
        ) : (
          <table className="data-table">
            <thead><tr><th>Email</th><th>Role</th><th>Status</th><th>Added</th><th>Actions</th></tr></thead>
            <tbody>
              {members.map(m => (
                <tr key={m.id}>
                  <td style={{color:'#e2e8f0'}}>{m.inviteEmail || 'Unknown'}</td>
                  <td><span className="badge" style={{color: roleColor(m.role), background: roleColor(m.role)+'20'}}>{m.role}</span></td>
                  <td><span className="badge" style={{color: m.acceptedAt ? '#10b981' : '#f59e0b', background: m.acceptedAt ? '#052e16' : '#451a03'}}>
                    {m.acceptedAt ? '✅ Active' : '⏳ Pending'}
                  </span></td>
                  <td style={{color:'#64748b', fontSize:12}}>{new Date(m.createdAt).toLocaleDateString('en-IN')}</td>
                  <td>
                    {m.role !== 'OWNER' && (
                      <button style={{background:'none', border:'none', color:'#ef4444', cursor:'pointer'}}
                              onClick={() => handleRemove(m.id)}>Remove</button>
                    )}
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
