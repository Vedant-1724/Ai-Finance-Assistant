// PATH: finance-frontend/src/pages/TeamPage.tsx
// Team management — invite members, assign roles, remove members.
// Calls:
//   GET    /api/v1/{companyId}/team          → list members
//   POST   /api/v1/{companyId}/team/invite   → invite by email
//   DELETE /api/v1/{companyId}/team/{memberId} → remove member

import { useEffect, useState, useCallback } from 'react'
import api from '../api'
import { useAuth } from '../context/AuthContext'

type Role = 'OWNER' | 'EDITOR' | 'VIEWER'

interface Member {
  id: number
  email: string
  role: Role
  acceptedAt: string | null
  inviteEmail: string | null
  createdAt: string
}

interface InviteResponse {
  message: string
  memberId: number
  emailDeliveryEnabled: boolean
  inviteUrl?: string
}

const ROLE_LABELS: Record<Role, { label: string; color: string; desc: string }> = {
  OWNER: { label: 'Owner', color: '#60a5fa', desc: 'Full access, billing, invite' },
  EDITOR: { label: 'Editor', color: '#4ade80', desc: 'Add/edit transactions, view reports' },
  VIEWER: { label: 'Viewer', color: '#94a3b8', desc: 'Read-only access to dashboard' },
}

export default function TeamPage({ companyId }: { companyId: number }) {
  const { user, isFree, capabilities } = useAuth()
  const [members, setMembers] = useState<Member[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [invEmail, setInvEmail] = useState('')
  const [invRole, setInvRole] = useState<Role>('VIEWER')
  const [inviting, setInviting] = useState(false)
  const [invMsg, setInvMsg] = useState<string | null>(null)
  const [invErr, setInvErr] = useState<string | null>(null)
  const [inviteUrl, setInviteUrl] = useState<string | null>(null)
  const [removing, setRemoving] = useState<number | null>(null)
  const [memberToRemove, setMemberToRemove] = useState<Member | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const res = await api.get<Member[]>(`/api/v1/${companyId}/team`)
      setMembers(Array.isArray(res.data) ? res.data : [])
    } catch (e: unknown) {
      const status = (e as { response?: { status?: number } })?.response?.status
      if (status === 402) setError('UPGRADE_REQUIRED')
      else setError('Failed to load team members.')
    } finally {
      setLoading(false)
    }
  }, [companyId])

  useEffect(() => {
    void load()
  }, [load])

  const handleInvite = async () => {
    if (!invEmail.trim()) {
      setInvErr('Email is required.')
      return
    }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(invEmail)) {
      setInvErr('Enter a valid email address.')
      return
    }

    setInviting(true)
    setInvErr(null)
    setInvMsg(null)
    setInviteUrl(null)

    try {
      const res = await api.post<InviteResponse>(
        `/api/v1/${companyId}/team/invite`,
        { email: invEmail.trim(), role: invRole }
      )
      setInvMsg(res.data.message)
      setInviteUrl(res.data.emailDeliveryEnabled ? null : res.data.inviteUrl ?? null)
      setInvEmail('')
      void load()
    } catch (e: unknown) {
      const msg = (e as { response?: { data?: { error?: string } } })?.response?.data?.error
      setInvErr(msg ?? 'Failed to send invitation.')
    } finally {
      setInviting(false)
    }
  }

  const handleRemove = async (memberId: number) => {
    setRemoving(memberId)
    setInvErr(null)
    try {
      await api.delete(`/api/v1/${companyId}/team/${memberId}`)
      setMembers(prev => prev.filter(m => m.id !== memberId))
      setMemberToRemove(null)
    } catch {
      setInvErr('Failed to remove member. Please try again.')
    } finally {
      setRemoving(null)
    }
  }

  if (!capabilities.canManageTeam) return (
    <div className="upgrade-gate">
      <div style={{ fontSize: 56 }}>👥</div>
      <h2>Team management is owner-only</h2>
      <p>You can collaborate inside the workspace, but only the workspace owner can invite, remove, or manage team members.</p>
      <a href="/subscription" className="btn-liquid-glass" style={{ display: 'inline-block' }}><span>View Workspace Plan &rarr;</span></a>
    </div>
  )

  if (isFree) return (
    <div className="upgrade-gate">
      <div style={{ fontSize: 56 }}>👥</div>
      <h2>Team Management requires Trial or Pro</h2>
      <p>Invite team members with EDITOR or VIEWER roles to collaborate on your company finances.</p>
      <a href="/subscription" className="btn-liquid-glass" style={{ display: 'inline-block' }}><span>Upgrade Now &rarr;</span></a>
    </div>
  )

  if (loading) return <div className="loading">⏳ Loading team members…</div>
  if (error === 'UPGRADE_REQUIRED') return (
    <div className="upgrade-gate">
      <div style={{ fontSize: 56 }}>👥</div>
      <h2>Team Management requires Trial or Pro</h2>
      <a href="/subscription" className="btn-liquid-glass" style={{ display: 'inline-block' }}><span>Upgrade Now &rarr;</span></a>
    </div>
  )
  if (error) return <div className="error">❌ {error}</div>

  return (
    <div className="team-page">
      <div className="page-header">
        <h1 className="page-title">👥 Team</h1>
        <span style={{ fontSize: 13, color: '#475569' }}>{members.length} member{members.length !== 1 ? 's' : ''}</span>
      </div>

      <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
        {(Object.entries(ROLE_LABELS) as [Role, typeof ROLE_LABELS[Role]][]).map(([role, info]) => (
          <div key={role} style={{
            padding: '8px 14px', borderRadius: 8, background: 'rgba(255,255,255,0.03)',
            border: '1px solid rgba(255,255,255,0.07)', fontSize: 12,
          }}>
            <span style={{ color: info.color, fontWeight: 700 }}>{info.label}</span>
            <span style={{ color: '#475569', marginLeft: 8 }}>{info.desc}</span>
          </div>
        ))}
      </div>

      <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
        <div style={{ padding: '16px 20px', borderBottom: '1px solid var(--border)' }}>
          <span className="card-title">Current Members</span>
        </div>
        {members.length === 0 ? (
          <div className="empty-state">
            <div className="empty-icon">👥</div>
            <p className="empty-title">No team members yet</p>
            <p className="empty-sub">Invite a colleague below to collaborate</p>
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column' }}>
            {members.map(m => {
              const roleInfo = ROLE_LABELS[m.role] ?? ROLE_LABELS.VIEWER
              const initials = (m.email || m.inviteEmail || '?')[0].toUpperCase()
              const isPending = !m.acceptedAt
              const isCurrentUser = m.email === user?.email

              return (
                <div key={m.id} className="team-member-card" style={{
                  borderRadius: 0,
                  border: 'none',
                  borderBottom: '1px solid rgba(255,255,255,0.04)',
                }}>
                  <div className="team-avatar">{initials}</div>

                  <div className="team-member-info">
                    <div className="team-member-email">
                      {m.email || m.inviteEmail}
                      {isCurrentUser && (
                        <span style={{
                          marginLeft: 8, fontSize: 10, fontWeight: 700, padding: '1px 6px',
                          borderRadius: 10, background: 'rgba(59,130,246,0.15)', color: '#60a5fa',
                        }}>YOU</span>
                      )}
                      {isPending && (
                        <span style={{
                          marginLeft: 8, fontSize: 10, fontWeight: 700, padding: '1px 6px',
                          borderRadius: 10, background: 'rgba(245,158,11,0.12)', color: '#fcd34d',
                        }}>PENDING</span>
                      )}
                    </div>
                    <div className="team-member-role">
                      {isPending ? 'Invitation sent' : `Joined ${new Date(m.acceptedAt!).toLocaleDateString('en-IN')}`}
                    </div>
                  </div>

                  <span style={{
                    padding: '4px 12px', borderRadius: 20, fontSize: 12, fontWeight: 700,
                    background: `${roleInfo.color}18`, color: roleInfo.color,
                    border: `1px solid ${roleInfo.color}40`,
                  }}>
                    {roleInfo.label}
                  </span>

                  {!isCurrentUser && m.role !== 'OWNER' && (
                    <button
                      className="btn-danger"
                      style={{ padding: '6px 12px', fontSize: 12 }}
                      onClick={() => setMemberToRemove(m)}
                      disabled={removing === m.id}
                      title="Remove from team"
                    >
                      {removing === m.id ? '⏳' : '✕ Remove'}
                    </button>
                  )}
                </div>
              )
            })}
          </div>
        )}
      </div>

      <div className="invite-form">
        <div style={{ marginBottom: 16 }}>
          <div className="card-title">Invite a Team Member</div>
          <div style={{ fontSize: 13, color: '#475569', marginTop: 4 }}>
            They'll receive an email invitation to join your company workspace.
          </div>
        </div>

        {invMsg && (
          <div className="success-toast" style={{ marginBottom: 12, display: 'grid', gap: 10 }}>
            <div>{invMsg}</div>
            {inviteUrl && (
              <div style={{ fontSize: 12, lineHeight: 1.5, wordBreak: 'break-word' }}>
                Manual invite link: <a href={inviteUrl}>{inviteUrl}</a>
              </div>
            )}
          </div>
        )}
        {invErr && <div className="auth-error" style={{ marginBottom: 12 }}>{invErr}</div>}

        <div className="invite-form-row">
          <div className="form-group">
            <label>Email address</label>
            <input
              type="email"
              className="form-input"
              placeholder="colleague@company.com"
              value={invEmail}
              onChange={e => setInvEmail(e.target.value)}
              onKeyDown={e => e.key === 'Enter' && handleInvite()}
            />
          </div>
          <div className="form-group" style={{ minWidth: 140 }}>
            <label>Role</label>
            <select
              className="form-select"
              value={invRole}
              onChange={e => setInvRole(e.target.value as Role)}
            >
              <option value="VIEWER">Viewer</option>
              <option value="EDITOR">Editor</option>
            </select>
          </div>
          <div className="form-group" style={{ justifyContent: 'flex-end', minWidth: 120 }}>
            <label style={{ visibility: 'hidden' }}>Send</label>
            <button
              className="btn-liquid-glass"
              onClick={handleInvite}
              disabled={inviting}
            >
              <span>{inviting ? '⏳ Sending…' : '✉️ Send Invite'}</span>
            </button>
          </div>
        </div>
      </div>

      {memberToRemove && (
        <div className="modal-overlay" onClick={event => { if (event.target === event.currentTarget) setMemberToRemove(null) }}>
          <div className="modal-box" style={{ maxWidth: 420 }}>
            <div className="modal-header">
              <h2 className="modal-title">Remove Team Member</h2>
              <button className="modal-close" onClick={() => setMemberToRemove(null)}>×</button>
            </div>
            <p style={{ margin: '16px 0', color: 'var(--text-secondary)', lineHeight: 1.6 }}>
              Remove <strong>{memberToRemove.email || memberToRemove.inviteEmail || 'this member'}</strong> from the workspace? Pending invites will also be revoked.
            </p>
            <div className="modal-footer">
              <button className="btn-secondary" onClick={() => setMemberToRemove(null)}>
                Cancel
              </button>
              <button
                className="btn-danger"
                onClick={() => { void handleRemove(memberToRemove.id) }}
                disabled={removing === memberToRemove.id}
              >
                {removing === memberToRemove.id ? 'Removing…' : 'Remove member'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
