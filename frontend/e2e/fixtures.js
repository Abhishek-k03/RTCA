import { test as base, expect } from '@playwright/test'

export { expect }

const PASSWORD = 'password123'
let counter = 0
const unique = () => `${Date.now().toString(36)}${(counter++).toString(36)}${Math.floor(Math.random() * 1296).toString(36)}`

// fixture values are handed over with provide (playwright calls it use)
export const test = base.extend({
  // calls the backend through the same proxy the app uses. returns parsed json
  api: async ({ request }, provide) => {
    await provide(async (method, path, { token, data, multipart } = {}) => {
      const res = await request.fetch(path, {
        method,
        headers: token ? { Authorization: `Bearer ${token}` } : {},
        data,
        multipart,
      })
      if (!res.ok()) throw new Error(`${method} ${path} -> ${res.status()} ${await res.text()}`)
      const text = await res.text()
      return text ? JSON.parse(text) : null
    })
  },

  // a fresh account per call, so tests never share state
  createUser: async ({ api }, provide) => {
    await provide(async (displayName) => {
      const username = `e2e_${unique()}`
      await api('POST', '/api/auth/register', { data: { username, email: `${username}@example.com`, password: PASSWORD, displayName } })
      const auth = await api('POST', '/api/auth/login', { data: { login: username, password: PASSWORD } })
      return { username, displayName, id: auth.user.id, token: auth.accessToken }
    })
  },

  // each user gets their own browser context, like a separate person
  signIn: async ({ browser, baseURL }, provide) => {
    const contexts = []
    await provide(async (user, options = {}) => {
      const context = await browser.newContext({ baseURL, ...options })
      contexts.push(context)
      const page = await context.newPage()
      await page.goto('/login')
      await page.locator('input[autocomplete=username]').fill(user.username)
      await page.locator('input[type=password]').fill(PASSWORD)
      await page.getByRole('button', { name: 'Continue' }).click()
      await expect(page.getByTitle('Sign out')).toBeVisible()
      return page
    })
    await Promise.all(contexts.map((c) => c.close()))
  },

  send: async ({ api }, provide) => {
    await provide((user, conversationId, content, extra = {}) => api('POST', `/api/conversations/${conversationId}/messages`, {
      token: user.token, data: { clientMessageId: unique(), content, ...extra },
    }))
  },

  // a direct chat with a first message, so it shows up for both people
  directChat: async ({ api, send }, provide) => {
    await provide(async (from, to, firstMessage) => {
      const chat = await api('POST', '/api/conversations/direct', { token: from.token, data: { userId: to.id } })
      if (firstMessage) await send(from, chat.id, firstMessage)
      return chat.id
    })
  },
})

// opens a chat once the live connection is up, so no event is missed
export async function openChat(page, conversationId) {
  await page.goto(`/c/${conversationId}`)
  await expect(page.locator('aside[data-live=true]')).toBeVisible()
  await expect(page.locator('main h1')).toBeVisible()
}

// a test picture drawn in the page, as png or jpeg bytes
export async function drawImage(page, width, height, type = 'image/png') {
  const base64 = await page.evaluate(([w, h, t]) => {
    const canvas = document.createElement('canvas')
    canvas.width = w
    canvas.height = h
    const ctx = canvas.getContext('2d')
    const g = ctx.createLinearGradient(0, 0, w, h)
    g.addColorStop(0, '#913f4a')
    g.addColorStop(1, '#7f8976')
    ctx.fillStyle = g
    ctx.fillRect(0, 0, w, h)
    return canvas.toDataURL(t, 0.9).split(',')[1]
  }, [width, height, type])
  return Buffer.from(base64, 'base64')
}

// the bubble holding a message. quotes contain the original's text too, so match the body
export const message = (page, text) => page.locator('[data-mid]', { has: page.locator('p', { hasText: text }) }).last()
