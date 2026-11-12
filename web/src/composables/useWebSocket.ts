import { ref } from 'vue'
import { getStoredAccessToken } from '../api/client'

export interface ConsoleLogEntry {
  timestamp?: number
  time?: string
  level: string
  logger?: string
  thread?: string
  message: string
  messageFormat?: string
  source?: string
}

type MonitorCallback = (payload: any) => void

const connected = ref(false)
const connecting = ref(false)
const status = ref<Record<string, any> | null>(null)
const lastStatusAt = ref<Date | null>(null)
const logs = ref<ConsoleLogEntry[]>([])
const commandHistory = ref<string[]>([])
const completions = ref<{ requestId: string; items: string[] }>({
  requestId: '',
  items: []
})

const monitorLogListeners = new Set<MonitorCallback>()
const playerEventListeners = new Set<MonitorCallback>()

let socket: WebSocket | null = null
let reconnectTimer: ReturnType<typeof setTimeout> | null = null
let intentionalClose = false
let reconnectAttempt = 0
let refUsers = 0

function deepMerge(target: Record<string, any>, patch: Record<string, any>) {
  for (const key of Object.keys(patch)) {
    const val = patch[key]
    if (
      val &&
      typeof val === 'object' &&
      !Array.isArray(val) &&
      target[key] &&
      typeof target[key] === 'object' &&
      !Array.isArray(target[key])
    ) {
      deepMerge(target[key], val)
    } else {
      target[key] = val
    }
  }
}

function buildWsUrl(): string {
  const proto = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const token = getStoredAccessToken()
  const base = `${proto}//${window.location.host}/ws`
  return token ? `${base}?token=${encodeURIComponent(token)}` : base
}

function scheduleReconnect() {
  if (intentionalClose || reconnectTimer || refUsers <= 0) return
  const delay = Math.min(5000, 1000 * Math.pow(1.6, reconnectAttempt))
  reconnectAttempt++
  reconnectTimer = setTimeout(() => {
    reconnectTimer = null
    openSocket()
  }, delay)
}

function openSocket() {
  if (
    socket &&
    (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING)
  ) {
    return
  }
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
  intentionalClose = false
  connecting.value = true

  let ws: WebSocket
  try {
    ws = new WebSocket(buildWsUrl())
    socket = ws
  } catch {
    connecting.value = false
    scheduleReconnect()
    return
  }

  ws.onopen = () => {
    if (socket !== ws) return
    connected.value = true
    connecting.value = false
    reconnectAttempt = 0
  }

  ws.onclose = () => {
    if (socket !== ws) return
    connected.value = false
    connecting.value = false
    socket = null
    scheduleReconnect()
  }

  ws.onerror = () => {
    if (socket !== ws) return
    connected.value = false
    connecting.value = false
  }

  ws.onmessage = (ev) => {
    if (socket !== ws) return
    try {
      const msg = JSON.parse(ev.data)
      if (!msg || !msg.type) return

      switch (msg.type) {
        case 'status':
          if (msg.data && typeof msg.data === 'object' && Object.keys(msg.data).length > 0) {
            status.value = msg.data
            lastStatusAt.value = new Date()
          }
          break

        case 'status_delta':
          if (msg.data && typeof msg.data === 'object') {
            lastStatusAt.value = msg.timestamp ? new Date(msg.timestamp) : new Date()
            if (!status.value) {
              if (Object.keys(msg.data).length > 0) {
                status.value = msg.data
              }
            } else if (Object.keys(msg.data).length > 0) {
              const copy = { ...status.value }
              deepMerge(copy, msg.data)
              status.value = copy
            }
          }
          break

        case 'logs':
          if (Array.isArray(msg.data)) {
            logs.value = msg.data.slice(-2000)
          }
          break

        case 'new_log': {
          let entry: ConsoleLogEntry | null = null
          if (typeof msg.data_json === 'string') {
            try {
              const parsed = JSON.parse(msg.data_json)
              entry =
                typeof parsed === 'object'
                  ? parsed
                  : { time: '', level: 'INFO', message: String(parsed), source: 'server' }
            } catch {
              entry = { time: '', level: 'INFO', message: msg.data_json, source: 'server' }
            }
          } else if (msg.data && typeof msg.data === 'object') {
            entry = msg.data
          }
          if (entry) {
            logs.value.push(entry)
            if (logs.value.length > 2000) {
              logs.value.splice(0, logs.value.length - 2000)
            }
          }
          break
        }

        case 'completions':
          completions.value = {
            requestId: String(msg.requestId ?? ''),
            items: Array.isArray(msg.data) ? msg.data : []
          }
          break

        case 'command_history':
          if (Array.isArray(msg.data)) {
            commandHistory.value = msg.data
          }
          break

        case 'new_monitor_log':
          if (msg.data) {
            monitorLogListeners.forEach((cb) => cb(msg.data))
          }
          break

        case 'new_player_event':
          if (msg.data) {
            playerEventListeners.forEach((cb) => cb(msg.data))
          }
          break
      }
    } catch {
      // ignore malformed messages
    }
  }
}

function sendPayload(payload: Record<string, any>): boolean {
  if (!socket || socket.readyState !== WebSocket.OPEN) return false
  socket.send(JSON.stringify(payload))
  return true
}

export function reauthenticateWs(token: string | null) {
  if (socket && socket.readyState === WebSocket.OPEN) {
    socket.send(JSON.stringify({ action: 'auth', token: token ?? '' }))
    return
  }
  if (refUsers > 0) {
    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
    if (socket) {
      const old = socket
      socket = null
      intentionalClose = true
      try {
        old.close()
      } catch {
        // ignore
      }
    }
    openSocket()
  }
}

export function retainWebSocket() {
  refUsers++
  if (refUsers === 1) {
    openSocket()
  }
}

export function releaseWebSocket() {
  refUsers = Math.max(0, refUsers - 1)
  if (refUsers === 0) {
    intentionalClose = true
    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
    if (socket) {
      socket.close()
      socket = null
    }
    connected.value = false
    connecting.value = false
  }
}

export function useWebSocket() {
  return {
    connected,
    connecting,
    status,
    lastStatusAt,
    logs,
    commandHistory,
    completions,
    ensureConnected: () => openSocket(),
    refreshStatus: () => sendPayload({ action: 'fetch_status' }),
    fetchLogs: () => sendPayload({ action: 'fetch_logs' }),
    sendCommand: (command: string) => sendPayload({ action: 'execute_command', command }),
    requestCompletions: (command: string, requestId: string) =>
      sendPayload({ action: 'fetch_completions', command, requestId }),
    requestCommandHistory: () => sendPayload({ action: 'fetch_command_history' }),
    onMonitorLog: (cb: MonitorCallback) => {
      monitorLogListeners.add(cb)
      return () => monitorLogListeners.delete(cb)
    },
    onPlayerEvent: (cb: MonitorCallback) => {
      playerEventListeners.add(cb)
      return () => playerEventListeners.delete(cb)
    }
  }
}

