<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { useRuntimeStore } from '../stores/runtime'
import { useWorkspaceStore } from '../stores/workspace'
import { runtimeLabels, type ContainerObservation, type RuntimeEvent } from '../domain'

const emit = defineEmits<{ focusNode: [id: string] }>()
const runtimeStore = useRuntimeStore()
const workspace = useWorkspaceStore()
const { runtime, events, logs, selectedLogNodeId, connected, lastSync, error } =
  storeToRefs(runtimeStore)
const tab = ref<'status' | 'events' | 'logs'>('status')
const followLogs = ref(false)
const eventTypeFilter = ref('all')
const eventNodeFilter = ref('all')
const selectedTraceId = ref<string | null>(null)
let refreshTimer: ReturnType<typeof setInterval> | null = null
onMounted(() => {
  refreshTimer = setInterval(() => {
    if (tab.value === 'logs' && followLogs.value) void runtimeStore.refreshLogs()
  }, 5000)
})
onUnmounted(() => {
  if (refreshTimer) clearInterval(refreshTimer)
})

const currentLogNode = computed(() =>
  runtime.value?.components.find((item) => item.nodeId === selectedLogNodeId.value),
)
const logText = computed(() => [logs.value?.stdout, logs.value?.stderr].filter(Boolean).join('\n'))
const filteredEvents = computed(() =>
  events.value.filter(
    (event) =>
      (eventTypeFilter.value === 'all' ||
        (eventTypeFilter.value === 'lifecycle'
          ? !event.eventType.includes('_')
          : event.eventType === eventTypeFilter.value)) &&
      (eventNodeFilter.value === 'all' || event.nodeId === eventNodeFilter.value),
  ),
)
const traceEvents = computed(() =>
  selectedTraceId.value
    ? events.value
        .filter((event) => event.payload.traceId === selectedTraceId.value)
        .slice()
        .sort((left, right) => Date.parse(left.timestamp) - Date.parse(right.timestamp))
    : [],
)

function containerStateLabel(state: string) {
  const labels: Record<string, string> = {
    created: '已创建',
    running: '运行中',
    restarting: '重启中',
    exited: '已退出',
    dead: '已退出',
    missing: '未创建',
  }
  return labels[state] || '状态待确认'
}

function componentState(item: ContainerObservation) {
  if (item.health === 'healthy') return '健康'
  if (item.health === 'unhealthy') return '健康检查失败'
  if (item.state === 'running') return '运行中 · 健康待确认'
  if (item.state === 'missing' && runtime.value?.status === 'STOPPED') return '已停止'
  return containerStateLabel(item.state)
}

function componentType(item: ContainerObservation) {
  return workspace.definition(item.componentType)?.name || '组件'
}

function requestStageLabel(stage: string | undefined) {
  const labels: Record<string, string> = {
    'gateway.received': 'Gateway 接收',
    'gateway.routed': 'Gateway 转发',
    'order.received': '订单服务接收',
    'order.completed': '订单服务完成',
    'order.blocked': '订单请求被限流',
    'inventory.called': '调用库存服务',
    'inventory.received': '库存服务接收',
    'inventory.completed': '库存服务完成',
  }
  return labels[stage || ''] || '请求处理'
}

function runtimeFailure() {
  if (runtime.value?.errorPhase === 'HEALTH')
    return runtime.value.errorMessage || '组件健康检查失败，请查看容器日志。'
  if (runtime.value?.errorPhase === 'START')
    return '运行环境启动失败，请检查 Docker Desktop 和后端日志。'
  if (runtime.value?.errorPhase === 'STOP')
    return '运行环境停止失败，请检查 Docker Desktop 和后端日志。'
  return '运行状态读取失败，请检查 Docker Desktop 和后端日志。'
}

function eventText(event: RuntimeEvent) {
  if (event.eventType === 'runtime.generated') return '运行环境已生成'
  if (event.eventType === 'runtime.status.changed')
    return `运行环境 ${runtimeLabels[event.payload.status as keyof typeof runtimeLabels] || '状态已更新'}`
  if (event.eventType === 'REQUEST_TRACE')
    return `${requestStageLabel(event.payload.stage)} · ${event.payload.service || ''} · ${event.payload.status || ''}${event.payload.durationMs ? ` · ${event.payload.durationMs} ms` : ''}`
  if (event.eventType === 'CACHE_ACCESS')
    return `${({ HIT: '命中', MISS: '未命中', WRITE: '写入' } as Record<string, string>)[event.payload.result] || '访问'} · ${event.payload.cacheName || '缓存'} / ${event.payload.key || '—'} · ${event.payload.durationMs || '0'} ms`
  if (event.eventType === 'MESSAGE_PUBLISHED' || event.eventType === 'MESSAGE_CONSUMED')
    return `${event.eventType === 'MESSAGE_PUBLISHED' ? '发布' : '消费'} · ${event.payload.routingKey || '消息'} · ${event.payload.messageId ? `消息 ${event.payload.messageId.slice(0, 8)}` : '消息'}`
  if (event.eventType === 'SENTINEL_BLOCKED')
    return `限流 · ${event.payload.resource || '请求'} · ${event.payload.reason || ''}`
  const state = containerStateLabel(event.payload.state || '')
  const health =
    event.payload.health === 'healthy'
      ? '健康'
      : event.payload.health === 'unhealthy'
        ? '不健康'
        : event.payload.health === 'starting'
          ? '健康检查中'
          : '待确认'
  return `容器 ${state} · ${health}`
}

function eventLabel(event: RuntimeEvent) {
  return (
    (
      {
        'runtime.generated': '环境生成',
        'runtime.status.changed': '运行状态',
        'component.status.changed': '组件状态',
        REQUEST_TRACE: '请求',
        CACHE_ACCESS: '缓存',
        MESSAGE_PUBLISHED: '消息发布',
        MESSAGE_CONSUMED: '消息消费',
        SENTINEL_BLOCKED: '限流',
      } as Record<string, string>
    )[event.eventType] || '其他事件'
  )
}

async function copyLogs() {
  if (logText.value) await navigator.clipboard.writeText(logText.value)
}
</script>

<template>
  <section class="runtime-panel" aria-label="运行面板">
    <div class="runtime-tabs">
      <button :class="{ active: tab === 'status' }" @click="tab = 'status'">运行状态</button>
      <button :class="{ active: tab === 'events' }" @click="tab = 'events'">
        事件 <span v-if="events.length" class="tab-count">{{ events.length }}</span>
      </button>
      <button :class="{ active: tab === 'logs' }" @click="tab = 'logs'">日志</button>
      <span class="runtime-spacer" />
      <span
        v-if="lastSync"
        class="runtime-sync"
        :title="`最近同步：${new Date(lastSync).toLocaleString()}`"
        >{{ connected ? '实时连接' : '轮询同步' }}</span
      >
      <span
        class="runtime-indicator"
        :class="runtime ? `state-${runtime.status.toLowerCase()}` : ''"
        ><span class="status-dot" /> {{ runtime ? runtimeLabels[runtime.status] : '未生成' }}</span
      >
    </div>
    <div class="runtime-body">
      <div v-if="error" class="runtime-error" role="alert">
        {{ error }} <button @click="runtimeStore.refresh()">重试</button>
      </div>
      <template v-if="tab === 'status'">
        <div v-if="!runtime" class="runtime-empty">
          <strong>尚未生成运行环境</strong>
          <p>保存可运行组件后，从顶部工具栏生成 Docker Compose 环境。</p>
        </div>
        <div v-else class="runtime-content">
          <div class="runtime-summary">
            <strong>{{ runtimeLabels[runtime.status] }}</strong
            ><span>{{ runtime.components.length }} 个组件</span>
          </div>
          <div v-if="runtime.errorMessage" class="runtime-failure">
            {{ runtimeFailure() }}
          </div>
          <div class="runtime-component-list">
            <button
              v-for="item in runtime.components"
              :key="item.nodeId"
              class="runtime-component"
              @click="emit('focusNode', item.nodeId)"
            >
              <span class="runtime-rail" :class="`health-${item.health}`" /><span
                class="runtime-component-name"
                >{{ item.displayName }}</span
              ><span class="runtime-component-type">{{ componentType(item) }}</span
              ><span class="runtime-component-state" :class="`health-${item.health}`">{{
                componentState(item)
              }}</span
              ><code :title="item.containerId ? `容器 ID：${item.containerId}` : '尚未创建容器'">{{
                item.containerId ? item.containerId.slice(0, 12) : '—'
              }}</code>
            </button>
          </div>
        </div>
      </template>
      <template v-else-if="tab === 'events'"
        ><div v-if="!events.length" class="runtime-empty">
          <strong>暂无运行事件</strong>
          <p>运行状态、真实请求、缓存和消息活动会记录在这里。</p>
        </div>
        <div v-else class="event-view">
          <div class="event-toolbar">
            <label
              >类型
              <select v-model="eventTypeFilter">
                <option value="all">全部</option>
                <option value="REQUEST_TRACE">请求</option>
                <option value="CACHE_ACCESS">缓存</option>
                <option value="MESSAGE_PUBLISHED">消息发布</option>
                <option value="MESSAGE_CONSUMED">消息消费</option>
                <option value="SENTINEL_BLOCKED">限流</option>
                <option value="lifecycle">运行状态</option>
              </select></label
            >
            <label
              >节点
              <select v-model="eventNodeFilter">
                <option value="all">全部</option>
                <option
                  v-for="item in runtime?.components || []"
                  :key="item.nodeId"
                  :value="item.nodeId"
                >
                  {{ item.displayName }}
                </option>
              </select></label
            >
            <span>{{ filteredEvents.length }} 条</span>
          </div>
          <div v-if="selectedTraceId" class="trace-strip">
            <div class="trace-title">
              <span>TRACE / {{ selectedTraceId }}</span
              ><button @click="selectedTraceId = null">关闭</button>
            </div>
            <div class="trace-stages">
              <span v-for="event in traceEvents" :key="event.eventId" :title="eventText(event)">{{
                event.payload.stage ? requestStageLabel(event.payload.stage) : eventLabel(event)
              }}</span>
            </div>
          </div>
          <div class="event-list">
            <div v-for="event in filteredEvents" :key="event.eventId" class="event-row">
              <time>{{ new Date(event.timestamp).toLocaleTimeString() }}</time>
              <span class="event-kind">{{ eventLabel(event) }}</span>
              <span class="event-detail">{{ eventText(event) }}</span>
              <button
                v-if="event.payload.traceId"
                class="event-trace"
                :title="`查看请求链路：${event.payload.traceId}`"
                @click="selectedTraceId = event.payload.traceId"
              >
                {{ event.payload.traceId.slice(0, 8) }}
              </button>
              <span v-else />
              <button
                v-if="event.nodeId"
                class="event-link"
                @click="emit('focusNode', event.nodeId)"
              >
                定位 ↗
              </button>
            </div>
            <div v-if="!filteredEvents.length" class="events-no-match">当前筛选没有事件。</div>
          </div>
        </div></template
      >
      <template v-else
        ><div v-if="!runtime" class="runtime-empty">
          <strong>暂无运行日志</strong>
          <p>生成运行环境后，可按组件读取真实容器日志。</p>
        </div>
        <div v-else class="logs-layout">
          <div class="logs-toolbar">
            <label
              >组件
              <select
                :value="selectedLogNodeId || ''"
                @change="runtimeStore.selectLog(($event.target as HTMLSelectElement).value)"
              >
                <option value="" disabled>选择组件</option>
                <option v-for="item in runtime.components" :key="item.nodeId" :value="item.nodeId">
                  {{ item.displayName }}
                </option>
              </select></label
            ><span class="logs-spacer" /><label class="logs-follow"
              ><input v-model="followLogs" type="checkbox" /> 自动刷新</label
            ><button
              class="quiet-button"
              :disabled="!selectedLogNodeId"
              @click="runtimeStore.refreshLogs()"
            >
              刷新</button
            ><button class="quiet-button" :disabled="!logText" @click="copyLogs">复制</button>
          </div>
          <div v-if="selectedLogNodeId" class="logs-output">
            <div class="logs-caption">
              {{ currentLogNode?.displayName }} · 最近 100 行 · Docker Compose 原始输出
            </div>
            <pre>{{ logText || '当前没有可读取的日志。' }}</pre>
          </div>
          <div v-else class="logs-placeholder">选择组件查看真实容器日志。</div>
        </div></template
      >
    </div>
  </section>
</template>
