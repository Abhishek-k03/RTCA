import { Client } from '@stomp/stompjs'
import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react'
import { freshToken } from '../api/client'
import { useAuth } from '../auth/AuthContext'

const StompContext = createContext(null)

export function StompProvider({ children }) {
  const { user } = useAuth()
  const signedIn = !!user
  const clientRef = useRef(null)
  // id -> { dest, cb, sub }, resubscribed on every (re)connect
  const subsRef = useRef(new Map())
  const nextId = useRef(0)
  const [connected, setConnected] = useState(false)
  const [errors, setErrors] = useState([])

  const pushError = useCallback((message) => {
    const id = ++nextId.current
    setErrors((e) => [...e, { id, message }])
    setTimeout(() => setErrors((e) => e.filter((x) => x.id !== id)), 5000)
  }, [])

  const attach = (client, entry) => {
    entry.sub = client.subscribe(entry.dest, (frame) => entry.cb(JSON.parse(frame.body)))
  }

  useEffect(() => {
    if (!signedIn) return
    const client = new Client({
      brokerURL: `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws`,
      // access tokens are short lived, so get a fresh one for every (re)connect
      beforeConnect: async (c) => {
        c.connectHeaders = { Authorization: `Bearer ${await freshToken()}` }
      },
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      reconnectDelay: 3000,
      onConnect: () => {
        setConnected(true)
        subsRef.current.forEach((entry) => attach(client, entry))
      },
      onDisconnect: () => setConnected(false),
      onWebSocketClose: () => setConnected(false),
      onStompError: (frame) => pushError(frame.headers.message || 'STOMP error'),
    })
    clientRef.current = client
    client.activate()
    return () => {
      clientRef.current = null
      setConnected(false)
      client.deactivate()
    }
  }, [signedIn, pushError])

  const subscribe = useCallback((dest, cb) => {
    const id = ++nextId.current
    const entry = { dest, cb, sub: null }
    subsRef.current.set(id, entry)
    const client = clientRef.current
    if (client?.connected) attach(client, entry)
    return () => {
      subsRef.current.delete(id)
      if (clientRef.current?.connected) entry.sub?.unsubscribe()
    }
  }, [])

  const publish = useCallback((dest, body) => {
    const client = clientRef.current
    if (!client?.connected) return false
    client.publish({ destination: dest, body: JSON.stringify(body) })
    return true
  }, [])

  useEffect(() => subscribe('/user/queue/errors', (e) => pushError(`${e.status}: ${e.message}`)), [subscribe, pushError])

  return (
    <StompContext.Provider value={{ connected, subscribe, publish, errors, pushError }}>
      {children}
    </StompContext.Provider>
  )
}

export const useStomp = () => useContext(StompContext)

// handler kept in a ref so callers don't need to memoize it
export function useTopic(dest, handler) {
  const { subscribe } = useStomp()
  const ref = useRef(handler)
  useEffect(() => { ref.current = handler })
  useEffect(() => {
    if (!dest) return
    return subscribe(dest, (event) => ref.current(event))
  }, [dest, subscribe])
}
