import { useState } from 'react'
import { api } from '../api/endpoints'
import { displayName } from '../lib'
import FormError from './FormError'
import PresenceDot from './PresenceDot'
import UserSearch from './UserSearch'

export default function GroupInfoPanel({ conversation: c, meId, presence, onChanged }) {
  const [name, setName] = useState(c.name || '')
  const [error, setError] = useState(null)

  const isGroup = c.type === 'GROUP'
  const myRole = c.participants.find((p) => p.userId === meId)?.role
  const isOwner = myRole === 'OWNER'
  const canManage = isOwner || myRole === 'ADMIN'

  const act = async (fn) => {
    setError(null)
    try {
      const updated = await fn()
      if (updated) onChanged(updated)
    } catch (err) {
      setError(err)
    }
  }

  const remove = (userId) => act(async () => {
    await api.removeMember(c.id, userId)
    return api.conversation(c.id)
  })

  // leaving keeps the chat as read-only, so just refetch it
  const leave = () => act(async () => {
    await api.removeMember(c.id, meId)
    return api.conversation(c.id)
  })

  return (
    <aside className="flex w-72 flex-col gap-2 overflow-y-auto border-l p-2">
      <h2 className="font-bold">{isGroup ? 'Group info' : 'Chat info'}</h2>

      {isGroup && canManage && (
        <form className="flex gap-1" onSubmit={(e) => {
          e.preventDefault()
          act(() => api.renameGroup(c.id, name.trim()))
        }}>
          <input className="min-w-0 flex-1 border p-1" value={name} onChange={(e) => setName(e.target.value)} />
          <button className="border px-2" disabled={!name.trim()}>Rename</button>
        </form>
      )}

      <h3>Members ({c.participants.length})</h3>
      <ul className="flex flex-col gap-2">
        {c.participants.map((p) => (
          <li key={p.userId} className="flex flex-col border-b pb-1">
            <span>
              {displayName(p)}{p.userId === meId && ' (you)'} <span className="text-gray-500">@{p.username}</span>
            </span>
            {p.userId !== meId && <PresenceDot status={presence[p.userId]} />}
            {isGroup && (
              <span className="flex items-center gap-2 text-sm">
                {isOwner && p.userId !== meId && p.role !== 'OWNER' ? (
                  <select className="border" value={p.role}
                    onChange={(e) => act(() => api.changeParticipantRole(c.id, p.userId, e.target.value))}>
                    <option>ADMIN</option>
                    <option>MEMBER</option>
                  </select>
                ) : (
                  <span>{p.role}</span>
                )}
                {canManage && p.userId !== meId && p.role !== 'OWNER' && (
                  <button className="underline" onClick={() => remove(p.userId)}>remove</button>
                )}
              </span>
            )}
          </li>
        ))}
      </ul>

      {isGroup && canManage && (
        <>
          <h3>Add members</h3>
          <UserSearch excludeIds={c.participants.map((p) => p.userId)}
            onPick={(u) => act(() => api.addMembers(c.id, [u.id]))} />
        </>
      )}

      {isGroup && !c.removedAt && <button className="border p-1" onClick={leave}>Leave group</button>}
      <FormError error={error} />
    </aside>
  )
}
