import { useEffect, useState } from 'react'
import { fetchFile } from './client'

// file url -> object url (or the pending fetch). a file id never changes, so once per session
const cache = new Map()

function load(url) {
  let entry = cache.get(url)
  if (!entry) {
    entry = fetchFile(url).then((blob) => {
      const objectUrl = URL.createObjectURL(blob)
      cache.set(url, objectUrl)
      return objectUrl
    }).catch((err) => {
      cache.delete(url)
      throw err
    })
    cache.set(url, entry)
  }
  return entry
}

// reuse a local copy we already have, e.g. an image we just sent
export function primeFileUrl(url, objectUrl) {
  if (typeof cache.get(url) !== 'string') cache.set(url, objectUrl)
}

export function clearFileCache() {
  cache.forEach((v) => typeof v === 'string' && URL.revokeObjectURL(v))
  cache.clear()
}

// null while loading or when the file can't be seen
export function useFileUrl(url) {
  const local = !url || url.startsWith('blob:')
  const cached = local ? url : cache.get(url)
  const [loaded, setLoaded] = useState({ url: null, objectUrl: null })

  useEffect(() => {
    if (local || typeof cache.get(url) === 'string') return
    let active = true
    load(url).then((objectUrl) => active && setLoaded({ url, objectUrl })).catch(() => {})
    return () => {
      active = false
    }
  }, [url, local])

  if (typeof cached === 'string') return cached
  return loaded.url === url ? loaded.objectUrl : null
}
