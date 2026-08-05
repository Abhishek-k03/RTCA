import { useCallback, useState } from 'react'

const KEY = 'notifications'
export const notificationsSupported = typeof window !== 'undefined' && 'Notification' in window

const stored = () => {
  try {
    return localStorage.getItem(KEY) === 'on'
  } catch {
    return false
  }
}

const store = (on) => {
  try {
    localStorage.setItem(KEY, on ? 'on' : 'off')
  } catch {
    // storage blocked, the setting just won't stick
  }
}

const enabled = () => notificationsSupported && Notification.permission === 'granted' && stored()

// opt in from a click, browsers only ask for permission after a user action
export function useNotificationSetting() {
  const [on, setOn] = useState(enabled)

  // resolves to 'on', 'off' or 'denied'
  const toggle = useCallback(async () => {
    if (on) {
      store(false)
      setOn(false)
      return 'off'
    }
    const permission = await Notification.requestPermission()
    if (permission !== 'granted') return 'denied'
    store(true)
    setOn(true)
    return 'on'
  }, [on])

  return { on, toggle }
}

// only while the tab is in the background. one per chat, a newer one replaces it
export function notify({ title, body, tag, onClick }) {
  if (!enabled() || document.visibilityState === 'visible') return
  const n = new Notification(title, { body, tag, icon: '/favicon.svg' })
  n.onclick = () => {
    window.focus()
    onClick?.()
    n.close()
  }
}
