export default function TypingIndicator({ names }) {
  const text = !names.length ? '' : names.length === 1 ? `${names[0]} is writing` : `${names.join(', ')} are writing`
  return (
    <div className="mx-auto h-6 w-full max-w-3xl px-6 md:px-12">
      {text && (
        <p className="animate-rise inline-flex items-center gap-2 font-serif text-[15px] text-ink-2 italic">
          {text}
          <span className="inline-flex gap-0.5">
            {[0, 1, 2].map((i) => (
              <span key={i} className="size-1 animate-pulse rounded-full bg-ink-3" style={{ animationDelay: `${i * 180}ms` }} />
            ))}
          </span>
        </p>
      )}
    </div>
  )
}
