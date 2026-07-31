import { Link } from 'react-router'
import { ArrowLeft } from 'lucide-react'

// shared frame for the secondary pages (profile, admin)
export default function PageShell({ eyebrow, title, children, wide = false }) {
  return (
    <div className="min-h-full bg-surface">
      <div className={`mx-auto px-6 py-8 md:px-12 md:py-14 ${wide ? 'max-w-5xl' : 'max-w-2xl'}`}>
        <Link to="/" className="btn-quiet"><ArrowLeft size={15} /> Correspondence</Link>
        <div className="animate-rise mt-12 md:mt-20">
          <p className="eyebrow">{eyebrow}</p>
          <h1 className="mt-3 font-serif text-5xl leading-none text-ink">{title}</h1>
          <div className="mt-12">{children}</div>
        </div>
      </div>
    </div>
  )
}
