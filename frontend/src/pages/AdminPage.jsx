import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { api } from '../api/endpoints'
import FormError from '../components/FormError'

const cell = 'border p-1'

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
    <div className="flex flex-col gap-2 p-4">
      <Link to="/" className="underline">← Back to chats</Link>
      <h1 className="text-xl">Users</h1>
      <FormError error={error} />
      {data && (
        <>
          <table className="border">
            <thead>
              <tr>
                <th className={cell}>ID</th>
                <th className={cell}>Username</th>
                <th className={cell}>Email</th>
                <th className={cell}>Display name</th>
                <th className={cell}>Role</th>
              </tr>
            </thead>
            <tbody>
              {data.content.map((u) => (
                <tr key={u.id}>
                  <td className={cell}>{u.id}</td>
                  <td className={cell}>{u.username}</td>
                  <td className={cell}>{u.email}</td>
                  <td className={cell}>{u.displayName}</td>
                  <td className={cell}>
                    <select className="border" value={u.role} onChange={(e) => changeRole(u.id, e.target.value)}>
                      <option>USER</option>
                      <option>ADMIN</option>
                    </select>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <div className="flex items-center gap-2">
            <button className="border px-2" disabled={page === 0} onClick={() => setPage(page - 1)}>Prev</button>
            <span>Page {data.page + 1} of {Math.max(data.totalPages, 1)} ({data.totalElements} users)</span>
            <button className="border px-2" disabled={data.last} onClick={() => setPage(page + 1)}>Next</button>
          </div>
        </>
      )}
    </div>
  )
}
