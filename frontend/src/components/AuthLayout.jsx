import Brand from './Brand'

export default function AuthLayout({ eyebrow, title, children, footer }) {
  const today = new Date().toLocaleDateString('en-GB', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })
  return (
    <div className="grid min-h-full md:grid-cols-[1.1fr_1fr]">
      <section className="flex flex-col justify-between p-8 md:p-14">
        <Brand large />
        <div className="hidden max-w-md md:block">
          <p className="font-serif text-[40px] leading-[1.1] text-ink">
            Letters, <span className="italic text-ink-2">in real time.</span>
          </p>
          <p className="mt-6 max-w-sm text-sm leading-relaxed text-ink-2">
            A quiet place for private conversations: one to one, or in small circles.
          </p>
        </div>
        <p className="eyebrow hidden md:block">{today}</p>
      </section>

      <section className="flex items-center bg-surface px-8 py-12 md:border-l md:border-hairline md:px-16">
        <div className="animate-rise w-full max-w-sm">
          <p className="eyebrow">{eyebrow}</p>
          <h1 className="mt-3 font-serif text-4xl text-ink">{title}</h1>
          <div className="mt-10">{children}</div>
          <div className="mt-10 text-sm text-ink-2">{footer}</div>
        </div>
      </section>
    </div>
  )
}
