export default function FormError({ error, field }) {
  if (!error) return null
  const text = field ? error.fieldErrors?.[field] : error.message
  return text ? <p className="mt-1 text-xs text-danger">{text}</p> : null
}
