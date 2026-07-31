export default function Brand({ large = false }) {
  return (
    <div className="flex flex-col">
      <span className={`font-serif leading-none tracking-tight text-ink ${large ? 'text-6xl' : 'text-[28px]'}`}>RTCA</span>
      <span className="eyebrow mt-1.5 text-[9.5px]">Private correspondence</span>
    </div>
  )
}
