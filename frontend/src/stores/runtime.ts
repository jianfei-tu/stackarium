import { computed, ref, shallowRef } from 'vue'
import { defineStore } from 'pinia'
import { api, errorMessage } from '../api'
import type { ContainerObservation, RuntimeEvent, RuntimeLogs, RuntimeView } from '../domain'

export const useRuntimeStore = defineStore('runtime', () => {
  const runtime = shallowRef<RuntimeView | null>(null)
  const events = shallowRef<RuntimeEvent[]>([])
  const logs = shallowRef<RuntimeLogs | null>(null)
  const selectedLogNodeId = ref<string | null>(null)
  const busy = ref<string | null>(null)
  const error = ref('')
  const connected = ref(false)
  const lastSync = ref<string | null>(null)
  const recentEventNodeId = ref<string | null>(null)
  let pulseTimer: ReturnType<typeof setTimeout> | null = null
  const canStart = computed(
    () => runtime.value?.status === 'GENERATED' || runtime.value?.status === 'STOPPED',
  )
  const canStop = computed(
    () => !!runtime.value && !['PREPARING', 'STOPPED', 'STOPPING'].includes(runtime.value.status),
  )
  let projectId = ''
  let socket: WebSocket | null = null
  let pollTimer: ReturnType<typeof setInterval> | null = null
  let reconnectTimer: ReturnType<typeof setTimeout> | null = null

  function component(nodeId: string): ContainerObservation | undefined {
    return runtime.value?.components.find((item) => item.nodeId === nodeId)
  }

  async function refresh() {
    if (!projectId) return
    try {
      const next = await api.latestRuntime(projectId)
      const changed = next?.id !== runtime.value?.id
      runtime.value = next
      lastSync.value = new Date().toISOString()
      if (changed) {
        events.value = next ? await api.runtimeEvents(projectId, next.id) : []
        logs.value = null
        selectedLogNodeId.value = null
      }
      error.value = ''
    } catch (cause) {
      error.value = errorMessage(cause)
    }
  }

  function connect() {
    if (!projectId || socket) return
    const active = projectId
    const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
    socket = new WebSocket(`${protocol}//${location.host}/ws/projects/${active}/runtime`)
    socket.onopen = () => {
      connected.value = true
    }
    socket.onmessage = (message) => {
      try {
        const event = JSON.parse(message.data) as RuntimeEvent
        if (
          event.runtimeId === runtime.value?.id &&
          !events.value.some((item) => item.eventId === event.eventId)
        ) {
          events.value = [event, ...events.value]
            .sort((left, right) => Date.parse(right.timestamp) - Date.parse(left.timestamp))
            .slice(0, 100)
          if (
            event.nodeId &&
            ['REQUEST_TRACE', 'CACHE_ACCESS', 'MESSAGE_PUBLISHED', 'MESSAGE_CONSUMED'].includes(
              event.eventType,
            )
          ) {
            recentEventNodeId.value = event.nodeId
            if (pulseTimer) clearTimeout(pulseTimer)
            pulseTimer = setTimeout(() => {
              recentEventNodeId.value = null
            }, 250)
          }
        }
        void refresh()
      } catch {
        /* REST polling remains the reconciliation path. */
      }
    }
    socket.onclose = () => {
      socket = null
      connected.value = false
      if (projectId === active) reconnectTimer = setTimeout(connect, 3000)
    }
    socket.onerror = () => {
      connected.value = false
    }
  }

  async function load(id: string) {
    disconnect()
    projectId = id
    await refresh()
    connect()
    pollTimer = setInterval(() => {
      void refresh()
    }, 5000)
  }

  function disconnect() {
    projectId = ''
    if (pollTimer) clearInterval(pollTimer)
    if (reconnectTimer) clearTimeout(reconnectTimer)
    if (pulseTimer) clearTimeout(pulseTimer)
    if (socket) socket.close()
    socket = null
    pollTimer = null
    reconnectTimer = null
    connected.value = false
    recentEventNodeId.value = null
  }

  async function act(action: 'generate' | 'start' | 'stop') {
    if (!projectId || busy.value) return
    busy.value = action
    error.value = ''
    try {
      if (action === 'generate') await api.generateRuntime(projectId)
      else if (action === 'start' && runtime.value)
        await api.startRuntime(projectId, runtime.value.id)
      else if (runtime.value) await api.stopRuntime(projectId, runtime.value.id)
      await refresh()
      if (runtime.value) events.value = await api.runtimeEvents(projectId, runtime.value.id)
    } catch (cause) {
      error.value = errorMessage(cause)
    } finally {
      busy.value = null
    }
  }

  async function selectLog(nodeId: string) {
    selectedLogNodeId.value = nodeId
    await refreshLogs()
  }

  async function refreshLogs() {
    if (!runtime.value || !projectId || !selectedLogNodeId.value) return
    try {
      logs.value = await api.runtimeLogs(projectId, runtime.value.id, selectedLogNodeId.value)
    } catch (cause) {
      error.value = errorMessage(cause)
    }
  }

  return {
    runtime,
    events,
    logs,
    selectedLogNodeId,
    busy,
    error,
    connected,
    lastSync,
    recentEventNodeId,
    canStart,
    canStop,
    component,
    load,
    disconnect,
    refresh,
    act,
    selectLog,
    refreshLogs,
  }
})
