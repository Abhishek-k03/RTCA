import { useEffect, useState } from 'react'
import { ArrowLeft, ArrowRight } from 'lucide-react'
import { api } from '../api/endpoints'
import FormError from '../components/FormError'
import PageShell from '../components/PageShell'
import { indexLabel, shortWhen } from '../lib'

export default function AdminPage() {
  const [page, setPage] = useState(0)
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    api.adminUsers(page).then(setData).catch(setError)
  }, [page])

  const changeRole = async (id, role) => {
    setError(null)
    try {
      const updated = await api.adminChangeRole(id, role)
      setData((d) => ({ ...d, content: d.content.map((u) => (u.id === id ? updated : u)) }))
    } catch (err) {
      setError(err)
    }
  }

  return (
    <PageShell eyebrow="Administration" title="Members" wide>
      <FormError error={error} />
      {data && (
        <>
          <p className="meta">{data.totalElements} accounts</p>
          <div className="mt-6 overflow-x-auto">
            <table className="w-full min-w-[640px] text-left text-sm">
              <thead>
                <tr className="hairline border-b">
                  {['No.', 'Name', 'Email', 'Joined', 'Role'].map((h) => (
                    <th key={h} className="eyebrow pb-3 font-medium">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {data.content.map((u) => (
                  <tr key={u.id} className="hairline border-b transition-colors hover:bg-outgoing/50">
                    <td className="meta py-4">{indexLabel(u.id)}</td>
                    <td className="py-4">
                      <span className="text-ink">{u.displayName || u.username}</span>
                      <span className="ml-2 text-ink-3">@{u.username}</span>
                    </td>
                    <td className="py-4 text-ink-2">{u.email}</td>
                    <td className="meta py-4">{shortWhen(u.createdAt)}</td>
                    <td className="py-4">
                      <select className={`cursor-pointer bg-transparent font-mono text-[12px] outline-none ${u.role === 'ADMIN' ? 'text-accent' : 'text-ink-2'}`}
                        value={u.role} onChange={(e) => changeRole(u.id, e.target.value)}>
                        <option value="USER">user</option>
                        <option value="ADMIN">admin</option>
                      </select>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="mt-8 flex items-center justify-between">
            <button className="btn-quiet disabled:opacity-30" disabled={page === 0} onClick={() => setPage(page - 1)}>
              <ArrowLeft size={15} /> Previous
            </button>
            <span className="meta">{data.page + 1} / {Math.max(data.totalPages, 1)}</span>
            <button className="btn-quiet disabled:opacity-30" disabled={data.last} onClick={() => setPage(page + 1)}>
              Next <ArrowRight size={15} />
            </button>
          </div>
        </>
      )}
    </PageShell>
  )
}
