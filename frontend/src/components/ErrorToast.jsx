import { useStomp } from '../ws/StompContext'

export default function ErrorToast() {
  const { errors } = useStomp()
  if (!errors.length) return null
  return (
    <div className="fixed right-4 bottom-4 z-40 flex max-w-sm flex-col gap-2">
      {errors.map((e) => (
        <div key={e.id} className="hairline animate-rise rounded-[8px] border bg-elevated px-4 py-3 text-sm text-ink shadow-[0_1px_2px_rgba(28,27,25,0.06)]">
          <span className="mr-2 inline-block size-1.5 rounded-full bg-accent align-middle" />
          {e.message}
        </div>
      ))}
    </div>
  )
}
