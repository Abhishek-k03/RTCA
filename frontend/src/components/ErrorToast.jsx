import { useStomp } from '../ws/StompContext'

export default function ErrorToast() {
  const { errors } = useStomp()
  if (!errors.length) return null
  return (
    <div className="fixed bottom-2 right-2 flex flex-col gap-1">
      {errors.map((e) => (
        <div key={e.id} className="border bg-white p-2 text-sm">{e.message}</div>
      ))}
    </div>
  )
}
