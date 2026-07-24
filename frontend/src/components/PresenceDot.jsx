import { formatTime } from '../lib'

export default function PresenceDot({ status }) {
  if (!status) return null
  if (status.online) return <span className="text-sm">● online</span>
  return (
    <span className="text-sm text-gray-500">
      ○ {status.lastSeen ? `last seen ${formatTime(status.lastSeen)}` : 'offline'}
    </span>
  )
}
