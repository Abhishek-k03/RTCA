import { useEffect, useState } from 'react'

const KEY = 'theme'
const media = () => window.matchMedia('(prefers-color-scheme: dark)')

const read = () => {
  try {
    return localStorage.getItem(KEY) || 'system'
  } catch {
    return 'system'
  }
}

export function applyTheme(choice = read()) {
  const dark = choice === 'dark' || (choice === 'system' && media().matches)
  document.documentElement.dataset.theme = dark ? 'dark' : 'light'
}

// 'system' | 'light' | 'dark'
export function useTheme() {
  const [theme, setTheme] = useState(read)

  useEffect(() => {
    applyTheme(theme)
    try {
      localStorage.setItem(KEY, theme)
    } catch {
      // not persisted, still applied
    }
    if (theme !== 'system') return
    const m = media()
    const onChange = () => applyTheme('system')
    m.addEventListener('change', onChange)
    return () => m.removeEventListener('change', onChange)
  }, [theme])

  const next = () => setTheme((t) => (t === 'system' ? 'light' : t === 'light' ? 'dark' : 'system'))
  return { theme, next }
}
