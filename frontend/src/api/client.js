export class ApiError extends Error {
  constructor(status, message, fieldErrors) {
    super(message)
    this.status = status
    this.fieldErrors = fieldErrors || {}
  }
}

let token = localStorage.getItem('token')
let onUnauthorized = () => {}

export const setToken = (t) => {
  token = t
  if (t) localStorage.setItem('token', t)
  else localStorage.removeItem('token')
}
export const getToken = () => token
export const setOnUnauthorized = (fn) => { onUnauthorized = fn }

export async function request(method, path, body) {
  const headers = {}
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (token) headers.Authorization = `Bearer ${token}`

  const res = await fetch(path, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  })

  if (res.status === 401 && token) onUnauthorized()
  const text = await res.text()
  const data = text ? JSON.parse(text) : null
  if (!res.ok) throw new ApiError(res.status, data?.message || res.statusText, data?.fieldErrors)
  return data
}
