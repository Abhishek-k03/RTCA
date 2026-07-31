import { lastSeen } from '../lib'

export default function PresenceDot({ status, pulse = false }) {
  if (!status) return null
  if (status.online) {
    return (
      <span className="inline-flex items-center gap-1.5 text-xs text-ink-2">
        <span className={`size-1.5 rounded-full bg-sage ${pulse ? 'animate-pulse-soft' : ''}`} />
        available
      </span>
    )
  }
  return <span className="text-xs text-ink-3">{lastSeen(status.lastSeen)}</span>
}
