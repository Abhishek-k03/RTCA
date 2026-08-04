export class ApiError extends Error {
  constructor(status, message, fieldErrors) {
    super(message)
    this.status = status
    this.fieldErrors = fieldErrors || {}
  }
}

// the access token lives in memory only, the refresh token is an http-only cookie
let token = null
let refreshing = null
let onUnauthorized = () => {}

// older versions kept the access token in local storage
try {
  localStorage.removeItem('token')
} catch {
  // storage blocked, nothing to clean up
}

export const setToken = (t) => { token = t }
export const setOnUnauthorized = (fn) => { onUnauthorized = fn }

/**
 * Swaps the refresh cookie for a new access token. Resolves to the auth response,
 * or null when the session is over. Throws when the server can't be reached.
 * Callers at the same time share one request.
 */
export function refreshSession() {
  refreshing ??= fetch('/api/auth/refresh', { method: 'POST' })
    .then(async (res) => {
      if (res.status === 401) return null
      if (!res.ok) throw new ApiError(res.status, res.statusText)
      const data = await res.json()
      token = data.accessToken
      return data
    })
    .finally(() => {
      refreshing = null
    })
  return refreshing
}

function secondsLeft(t) {
  try {
    const payload = JSON.parse(atob(t.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')))
    return payload.exp - Date.now() / 1000
  } catch {
    return 0
  }
}

// for the websocket connect, which can't retry on a 401 like fetch does
export async function freshToken() {
  if (token && secondsLeft(token) > 60) return token
  const data = await refreshSession().catch(() => undefined)
  if (data === null) onUnauthorized()
  return token
}

async function send(method, path, { json, form } = {}, retry = true) {
  const headers = {}
  if (json !== undefined) headers['Content-Type'] = 'application/json'
  if (token) headers.Authorization = `Bearer ${token}`

  const res = await fetch(path, {
    method,
    headers,
    body: form ?? (json !== undefined ? JSON.stringify(json) : undefined),
  })
  // the access token expired: refresh once and try again
  if (res.status === 401 && token && retry) {
    const data = await refreshSession().catch(() => undefined)
    if (data) return send(method, path, { json, form }, false)
    if (data === null) onUnauthorized()
  }
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
