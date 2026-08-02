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

async function send(method, path, { json, form } = {}) {
  const headers = {}
  if (json !== undefined) headers['Content-Type'] = 'application/json'
  if (token) headers.Authorization = `Bearer ${token}`

  const res = await fetch(path, {
    method,
    headers,
    body: form ?? (json !== undefined ? JSON.stringify(json) : undefined),
  })
  if (res.status === 401 && token) onUnauthorized()
  return res
}

async function parse(res) {
  const text = await res.text()
  let data = null
  try {
    data = text ? JSON.parse(text) : null
  } catch {
    // not json, e.g. a proxy error page
  }
  if (!res.ok) throw new ApiError(res.status, data?.message || res.statusText, data?.fieldErrors)
  return data
}

export const request = async (method, path, body) => parse(await send(method, path, { json: body }))

export const upload = async (method, path, form) => parse(await send(method, path, { form }))

// images need the auth header too, so they can't be plain <img src> urls
export async function fetchFile(path) {
  const res = await send('GET', path)
  if (!res.ok) throw new ApiError(res.status, res.statusText)
  return res.blob()
}
