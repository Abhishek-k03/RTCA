export default function FormError({ error, field }) {
  if (!error) return null
  const text = field ? error.fieldErrors?.[field] : error.message
  return text ? <p className="text-sm text-red-600">{text}</p> : null
}
