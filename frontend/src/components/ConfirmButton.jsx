import { useEffect, useState } from 'react'

// first click arms it, second click within a few seconds runs the action
export default function ConfirmButton({ children, confirmText = 'Click again to confirm', onConfirm, danger = false }) {
  const [armed, setArmed] = useState(false)

  useEffect(() => {
    if (!armed) return
    const t = setTimeout(() => setArmed(false), 3000)
    return () => clearTimeout(t)
  }, [armed])

  return (
    <button
      className={`py-1.5 text-left text-sm transition-colors duration-150 ${danger || armed ? 'text-danger' : 'text-ink'} hover:opacity-80`}
      onClick={() => {
        if (!armed) return setArmed(true)
        setArmed(false)
        onConfirm()
      }}>
      {armed ? confirmText : children}
    </button>
  )
}
