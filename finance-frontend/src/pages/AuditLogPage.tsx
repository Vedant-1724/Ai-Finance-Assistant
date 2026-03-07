// PATH: finance-frontend/src/pages/AuditLogPage.tsx
import { useEffect, useState, useCallback } from 'react'
import api from '../api'
import { useAuth } from '../context/AuthContext'

interface AuditEntry {
  id: number; companyId: number; userId: number; action: string;
  entityType: string; entityId: number; newValue: string; ipAddress: string; createdAt: string
}
interface PageResponse { content: AuditEntry[]; totalPages: number; number: number }

export default function AuditLogPage({ companyId }: { companyId: number }) {
  const { user } = useAuth()
  const [data, setData]     = useState<PageResponse | null>(null)
  const [page, setPage]     = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError]   = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const res = await api.get<PageResponse>(`/api/v1/${companyId}/audit?page=${page}&size=50`, { headers })
      setData(res.data)
    } catch (e: any) {
      if (e?.response?.status === 402) setError('UPGRADE_REQUIRED')
      else setError('Failed to load audit log')
    } finally { setLoading(false) }
  }, [companyId, page])

  useEffect(() => { void load() }, [load])

  if (loading) return <div className="loading">⏳ Loading audit log...</div>
  if (error === 'UPGRADE_REQUIRED') return (
    <div className="upgrade-gate">
      <div style={{fontSize:48}}>📋</div>
      <h2>Audit Log requires Trial or Pro</h2>
      <a href="/subscription" className="btn-primary">Upgrade Now</a>
    </div>
  )
  if (error) return <div className="error">❌ {error}</div>

  const actionColor = (a: string) =>
    a.includes('DELETE') ? '#ef4444' : a.includes('CREATE') ? '#10b981' : a.includes('EXPORT') ? '#f59e0b' : '#3b82f6'

  return (
    <div className="audit-page">
      <div className="page-header">
        <h1 className="page-title">📋 Audit Log</h1>
        <p className="page-subtitle">Complete immutable record of all actions in your account</p>
      </div>
      <div className="card">
        <table className="data-table">
          <thead>
            <tr><th>Time</th><th>Action</th><th>Entity</th><th>Details</th><th>IP</th></tr>
          </thead>
          <tbody>
            {data?.content.map(e => (
              <tr key={e.id}>
                <td style={{color:'#64748b',fontSize:12, whiteSpace:'nowrap'}}>
                  {new Date(e.createdAt).toLocaleString('en-IN')}
                </td>
                <td><span className="badge" style={{background: actionColor(e.action)+'20', color: actionColor(e.action)}}>
                  {e.action.replace(/_/g,' ')}
                </span></td>
                <td style={{color:'#94a3b8', fontSize:12}}>{e.entityType} #{e.entityId}</td>
                <td style={{color:'#64748b', fontSize:11}}>{e.newValue?.slice(0,60)}</td>
                <td style={{color:'#475569', fontSize:11}}>{e.ipAddress || '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {data && data.totalPages > 1 && (
          <div style={{display:'flex', gap:8, justifyContent:'center', marginTop:16}}>
            <button className="btn-secondary" onClick={() => setPage(p => Math.max(0,p-1))} disabled={page===0}>← Prev</button>
            <span style={{color:'#94a3b8', padding:'8px 12px'}}>Page {page+1} of {data.totalPages}</span>
            <button className="btn-secondary" onClick={() => setPage(p => p+1)} disabled={page>=data.totalPages-1}>Next →</button>
          </div>
        )}
      </div>
    </div>
  )
}

