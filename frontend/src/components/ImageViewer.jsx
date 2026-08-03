import { useEffect } from 'react'
import { Download, X } from 'lucide-react'

export default function ImageViewer({ url, onClose }) {
  useEffect(() => {
    const onKey = (e) => e.key === 'Escape' && onClose()
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  const stop = (e) => e.stopPropagation()

  return (
    <div role="dialog" aria-modal="true" onClick={onClose}
      className="animate-rise fixed inset-0 z-40 flex flex-col bg-canvas/95 backdrop-blur-sm">
      <div className="flex justify-end gap-1 p-3 md:p-5">
        <a className="icon-btn" href={url} download="photo" title="Download" onClick={stop}><Download size={17} /></a>
        <button className="icon-btn" title="Close"><X size={18} /></button>
      </div>
      <div className="flex min-h-0 flex-1 items-center justify-center px-4 pb-4 md:px-12 md:pb-12">
        <img src={url} alt="" onClick={stop}
          className="max-h-full max-w-full rounded-[4px] object-contain shadow-[0_2px_24px_rgba(28,27,25,0.12)]" />
      </div>
    </div>
  )
}
