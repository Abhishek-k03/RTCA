import { useState } from 'react'
import { Link } from 'react-router'
import { LogOut, Monitor, Moon, Plus, Search, Shield, Sun, X } from 'lucide-react'
import { useAuth } from '../auth/AuthContext'
import { useStomp } from '../ws/StompContext'
import { useTheme } from '../theme'
import { displayName } from '../lib'
import Brand from './Brand'
import ConversationList from './ConversationList'
import NewGroupDialog from './NewGroupDialog'
import SearchPanel from './SearchPanel'

const THEME_ICON = { system: Monitor, light: Sun, dark: Moon }

export default function Sidebar({ conversations, activeId, recents, onOpenChat, onOpenUser, onGroupCreated, className = '' }) {
  const { user, logout } = useAuth()
  const { connected } = useStomp()
  const { theme, next } = useTheme()
  const [searching, setSearching] = useState(false)
  const [query, setQuery] = useState('')
  const [showNewGroup, setShowNewGroup] = useState(false)
  const ThemeIcon = THEME_ICON[theme]

  const closeSearch = () => {
    setSearching(false)
    setQuery('')
  }

  const pick = (fn) => (entry) => {
    closeSearch()
    fn(entry)
  }

  return (
    <aside className={`flex min-h-0 flex-col bg-canvas ${className}`}>
      <div className="flex items-end justify-between px-6 pt-8 pb-6">
        <Brand />
        <span className={`meta mb-0.5 inline-flex items-center gap-1.5 ${connected ? '' : 'text-accent'}`}
          title={connected ? 'Connected' : 'Reconnecting'}>
          <span className={`size-1.5 rounded-full ${connected ? 'bg-sage' : 'animate-pulse bg-accent'}`} />
          {connected ? 'live' : 'offline'}
        </span>
      </div>

      <div className="px-6 pb-2">
        <label className="flex items-center gap-2 border-b border-hairline py-1.5 transition-colors focus-within:border-ink">
          <Search size={15} className="shrink-0 text-ink-3" />
          <input className="min-w-0 flex-1 bg-transparent py-1 text-sm text-ink outline-none placeholder:text-ink-3"
            placeholder="Search" value={query}
            onFocus={() => setSearching(true)}
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Escape') {
                closeSearch()
                e.currentTarget.blur()
              }
            }} />
          {searching && (
            <button className="text-ink-3 hover:text-ink" onClick={closeSearch} title="Close search"><X size={15} /></button>
          )}
        </label>
      </div>

      {!searching && (
        <div className="flex items-center justify-between px-6 pt-5 pb-2">
          <p className="eyebrow">Correspondence</p>
          <button className="icon-btn -mr-2" title="New group" onClick={() => setShowNewGroup(!showNewGroup)}>
            <Plus size={16} className={`transition-transform duration-200 ${showNewGroup ? 'rotate-45' : ''}`} />
          </button>
        </div>
      )}

      {showNewGroup && !searching && (
        <NewGroupDialog onClose={() => setShowNewGroup(false)} onCreated={(c) => {
          setShowNewGroup(false)
          onGroupCreated(c)
        }} />
      )}

      <nav className="min-h-0 flex-1 overflow-y-auto">
        {searching
          ? <SearchPanel query={query} conversations={conversations} meId={user.id} recents={recents}
              onOpenChat={pick(onOpenChat)} onOpenUser={pick(onOpenUser)} />
          : <ConversationList conversations={conversations} activeId={activeId} meId={user.id} />}
      </nav>

      <footer className="hairline flex items-center gap-1 border-t px-4 py-3">
        <Link to="/profile" className="flex min-w-0 flex-1 items-center gap-2 rounded-[8px] px-2 py-1.5 text-sm text-ink-2 transition-colors hover:text-ink">
          <span className="truncate">{displayName(user)}</span>
        </Link>
        {user.role === 'ADMIN' && (
          <Link to="/admin" className="icon-btn" title="Administration"><Shield size={16} /></Link>
        )}
        <button className="icon-btn" title={`Theme: ${theme}`} onClick={next}><ThemeIcon size={16} /></button>
        <button className="icon-btn" title="Sign out" onClick={logout}><LogOut size={16} /></button>
      </footer>
    </aside>
  )
}
