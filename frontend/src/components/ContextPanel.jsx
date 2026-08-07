import { useEffect, useState } from 'react'
import { X } from 'lucide-react'
import { api } from '../api/endpoints'
import { useTopic } from '../ws/StompContext'
import { conversationAvatar, conversationTitle, displayName } from '../lib'
import Avatar from './Avatar'
import ConfirmButton from './ConfirmButton'
import FormError from './FormError'
import PhotoPicker from './PhotoPicker'
import PresenceDot from './PresenceDot'
import UserSearch from './UserSearch'

const Rule = () => <hr className="hairline my-8 border-t" />

export default function ContextPanel({ conversation: c, meId, presence, onChanged, onClose, onClear, onDeleteChat }) {
  const [name, setName] = useState(c.name || '')
  const [error, setError] = useState(null)

  const isGroup = c.type === 'GROUP'
  const removed = !!c.removedAt
  const myRole = c.participants.find((p) => p.userId === meId)?.role
  const isOwner = myRole === 'OWNER'
  const canManage = !removed && (isOwner || myRole === 'ADMIN')
  const other = !isGroup ? c.participants.find((p) => p.userId !== meId) : null
  const otherId = other?.userId
  const [bio, setBio] = useState(null)

  // the bio isn't part of the chat, fetch it when the panel opens
  useEffect(() => {
    if (!otherId) return
    let active = true
    api.user(otherId).then((u) => active && setBio(u.bio)).catch(() => {})
    return () => {
      active = false
    }
  }, [otherId])

  useTopic('/user/queue/events', ({ type, payload: p }) => {
    if (type === 'PROFILE' && p.id === otherId) setBio(p.bio)
  })

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
    <aside className="hairline animate-rise fixed inset-0 z-30 overflow-y-auto bg-surface px-8 py-8 md:static md:z-auto md:w-[320px] md:shrink-0 md:border-l">
      <div className="flex items-start justify-between">
        <p className="meta">{isGroup ? 'Group correspondence' : 'Correspondence'}</p>
        <button className="icon-btn -mt-1.5 -mr-2" onClick={onClose} title="Close"><X size={16} /></button>
      </div>

      <div className="mt-8">
        {isGroup && canManage ? (
          <PhotoPicker name={c.name} src={c.avatarUrl}
            onUpload={async (blob) => onChanged(await api.setGroupAvatar(c.id, blob))}
            onRemove={async () => onChanged(await api.removeGroupAvatar(c.id))} />
        ) : (
          <Avatar name={conversationTitle(c, meId)} src={conversationAvatar(c, meId)} size="xl" />
        )}
      </div>
      <h2 className="mt-6 font-serif text-[34px] leading-[1.05] break-words text-ink">{conversationTitle(c, meId)}</h2>
      <div className="mt-3">
        {isGroup
          ? <p className="eyebrow">{c.participants.length} members{removed && ' · read only'}</p>
          : <PresenceDot status={presence[other?.userId]} />}
      </div>
      {other && <p className="mt-1 text-[13px] text-ink-3">@{other.username}</p>}
      {bio && <p className="mt-5 text-sm leading-relaxed break-words whitespace-pre-wrap text-ink-2">{bio}</p>}

      {canManage && (
        <form className="mt-8 flex items-end gap-3" onSubmit={(e) => {
          e.preventDefault()
          act(() => api.renameGroup(c.id, name.trim()))
        }}>
          <label className="flex min-w-0 flex-1 flex-col">
            <span className="eyebrow text-[9.5px]">Group name</span>
            <input className="field" value={name} onChange={(e) => setName(e.target.value)} />
          </label>
          <button className="btn-quiet pb-2" disabled={!name.trim() || name.trim() === c.name}>Rename</button>
        </form>
      )}

      {isGroup && (
        <>
          <Rule />
          <p className="eyebrow">Members</p>
          <ul className="mt-4 flex flex-col gap-4">
            {c.participants.map((p) => (
              <li key={p.userId} className="flex items-center gap-3">
                <Avatar name={displayName(p)} src={p.avatarUrl} size="sm" online={p.userId !== meId && presence[p.userId]?.online} />
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-sm text-ink">
                    {displayName(p)}{p.userId === meId && <span className="text-ink-3"> · you</span>}
                  </span>
                  <span className="flex items-center gap-3">
                    {isOwner && p.userId !== meId && p.role !== 'OWNER' ? (
                      <select className="eyebrow cursor-pointer bg-transparent text-[9.5px] outline-none hover:text-ink" value={p.role}
                        onChange={(e) => act(() => api.changeParticipantRole(c.id, p.userId, e.target.value))}>
                        <option value="ADMIN">Admin</option>
                        <option value="MEMBER">Member</option>
                      </select>
                    ) : (
                      <span className={`eyebrow text-[9.5px] ${p.role === 'OWNER' ? 'text-accent' : ''}`}>{p.role.toLowerCase()}</span>
                    )}
                    {canManage && p.userId !== meId && p.role !== 'OWNER' && (
                      <button className="text-[11px] text-ink-3 hover:text-danger" onClick={() => remove(p.userId)}>remove</button>
                    )}
                  </span>
                </span>
              </li>
            ))}
          </ul>

          {canManage && (
            <div className="mt-6">
              <UserSearch placeholder="Add people…" excludeIds={c.participants.map((p) => p.userId)}
                onPick={(u) => act(() => api.addMembers(c.id, [u.id]))} />
            </div>
          )}
        </>
      )}

      <Rule />
      <p className="eyebrow">Actions</p>
      <div className="mt-3 flex flex-col items-start">
        <ConfirmButton onConfirm={onClear} confirmText="Clear for you? Click again">Clear conversation</ConfirmButton>
        {(!isGroup || removed) && (
          <ConfirmButton danger onConfirm={onDeleteChat} confirmText="Delete for you? Click again">Delete conversation</ConfirmButton>
        )}
        {isGroup && !removed && (
          <ConfirmButton danger onConfirm={leave} confirmText="Leave this group? Click again">Leave group</ConfirmButton>
        )}
      </div>
      <FormError error={error} />
    </aside>
  )
}
