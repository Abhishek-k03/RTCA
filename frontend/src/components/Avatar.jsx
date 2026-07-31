import { initials } from '../lib'

const SIZES = { sm: 'size-7 text-[10px]', md: 'size-9 text-[11px]', lg: 'size-16 text-lg' }

export default function Avatar({ name, size = 'md', online = false }) {
  return (
    <span className={`relative inline-flex shrink-0 items-center justify-center rounded-full bg-outgoing font-medium tracking-wide text-ink-2 ${SIZES[size]}`}>
      {initials(name)}
      {online && <span className="absolute right-0 bottom-0 size-2 rounded-full bg-sage ring-2 ring-surface" />}
    </span>
  )
}
