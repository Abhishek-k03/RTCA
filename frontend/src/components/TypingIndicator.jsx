export default function TypingIndicator({ names }) {
  if (!names.length) return <div className="h-5 px-2" />
  const text = names.length === 1 ? `${names[0]} is typing…` : `${names.join(', ')} are typing…`
  return <div className="h-5 px-2 text-sm text-gray-500">{text}</div>
}
