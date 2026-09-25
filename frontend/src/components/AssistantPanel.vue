<script setup lang="ts">
import { computed, nextTick, onUnmounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { api, errorMessage } from '../api'
import { relationLabels } from '../domain'
import type {
  AssistantConversation,
  AssistantStreamEvent,
  ChangeOperation,
  ChangeProposal,
  ConversationView,
} from '../domain'
import { useWorkspaceStore } from '../stores/workspace'
import { useRuntimeStore } from '../stores/runtime'

const props = defineProps<{ projectId: string }>()
const workspace = useWorkspaceStore()
const runtime = useRuntimeStore()
const { project, dirty } = storeToRefs(workspace)
const conversations = ref<AssistantConversation[]>([])
const currentId = ref('')
const view = ref<ConversationView | null>(null)
const configured = ref(false)
const draft = ref('')
const busy = ref(false)
const loading = ref(false)
const error = ref('')
const activity = ref('')
const pendingMessage = ref('')
const assistantText = ref('')
const streamError = ref('')
const streamProposals = ref<ChangeProposal[]>([])
const messagesContainer = ref<HTMLElement | null>(null)

function scrollToLatest(force = false) {
  void nextTick(() => {
    const container = messagesContainer.value
    if (!container) return
    const distance = container.scrollHeight - container.clientHeight - container.scrollTop
    if (force || distance < 100) container.scrollTop = container.scrollHeight
  })
}
const shownProposals = computed(() => [
  ...(view.value?.proposals || []),
  ...streamProposals.value.filter(
    (item) => !view.value?.proposals.some((stored) => stored.id === item.id),
  ),
])
let streamController: AbortController | null = null
const canSend = computed(() => configured.value && !busy.value && draft.value.trim().length > 0)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [status, list] = await Promise.all([
      api.assistantStatus(props.projectId),
      api.assistantConversations(props.projectId),
    ])
    configured.value = status.modelConfigured
    conversations.value = list
    const saved = localStorage.getItem(`stackarium-assistant-${props.projectId}`)
    currentId.value = list.find((item) => item.id === saved)?.id || list[0]?.id || ''
    if (currentId.value) await openConversation(currentId.value)
    else view.value = null
  } catch (cause) {
    error.value = errorMessage(cause)
  } finally {
    loading.value = false
  }
}

async function openConversation(id: string) {
  currentId.value = id
  view.value = await api.assistantConversation(props.projectId, id)
  scrollToLatest(true)
  localStorage.setItem(`stackarium-assistant-${props.projectId}`, id)
}

function newConversation() {
  if (busy.value) return
  currentId.value = ''
  view.value = null
  draft.value = ''
  error.value = ''
  activity.value = ''
  assistantText.value = ''
  streamError.value = ''
  streamProposals.value = []
  localStorage.removeItem(`stackarium-assistant-${props.projectId}`)
}

function toolStatus(name: string) {
  const labels: Record<string, string> = {
    getTopology: '正在读取当前拓扑…',
    getComponentCatalog: '正在查询组件目录…',
    getRuntimeStatus: '正在查询运行状态…',
    addComponent: '正在检查新增组件建议…',
    removeComponent: '正在检查删除组件建议…',
    connectComponents: '正在检查组件连接…',
    disconnectComponents: '正在检查断开连接…',
    updateComponentConfig: '正在检查配置修改…',
    generateRuntimeConfig: '正在生成运行配置…',
  }
  return labels[name] || '正在处理请求…'
}

function toolFinished(name: string) {
  const labels: Record<string, string> = {
    getTopology: '已读取当前拓扑',
    getComponentCatalog: '已读取组件目录',
    getRuntimeStatus: '已查询运行状态',
    addComponent: '已检查新增组件建议',
    removeComponent: '已检查删除组件建议',
    connectComponents: '已检查组件连接',
    disconnectComponents: '已检查断开连接',
    updateComponentConfig: '已检查配置修改',
    generateRuntimeConfig: '已生成运行配置',
  }
  return labels[name] || '已完成处理'
}

function handleStreamEvent(event: AssistantStreamEvent) {
  if (event.type === 'MESSAGE_DELTA') {
    assistantText.value += event.text || ''
    scrollToLatest()
  }
  if (event.type === 'TOOL_STARTED') activity.value = toolStatus(event.tool || '')
  if (event.type === 'TOOL_FINISHED') activity.value = toolFinished(event.tool || '')
  if (event.type === 'PROPOSAL_CREATED' && event.proposal) {
    streamProposals.value = [...streamProposals.value, event.proposal]
    scrollToLatest(true)
  }
  if (event.type === 'ERROR') {
    streamError.value = event.text || '模型服务暂时不可用'
    activity.value = ''
  }
}

function stopGenerating() {
  streamController?.abort()
}

async function send(text = draft.value) {
  if (!configured.value || busy.value || !text.trim()) return
  const message = text.trim()
  busy.value = true
  error.value = ''
  activity.value = '正在等待模型响应…'
  draft.value = ''
  pendingMessage.value = message
  scrollToLatest(true)
  assistantText.value = ''
  streamError.value = ''
  streamProposals.value = []
  streamController = new AbortController()
  let done = false
  try {
    if (!currentId.value) {
      const created = await api.createAssistantConversation(props.projectId)
      conversations.value = [created, ...conversations.value]
      currentId.value = created.id
      localStorage.setItem(`stackarium-assistant-${props.projectId}`, created.id)
    }
    await api.assistantStream(
      props.projectId,
      currentId.value,
      message,
      streamController.signal,
      (event) => {
        handleStreamEvent(event)
        if (event.type === 'DONE') done = true
      },
    )
  } catch (cause) {
    streamError.value =
      cause instanceof Error && cause.name === 'AbortError' ? '已停止生成' : errorMessage(cause)
    activity.value = ''
  } finally {
    streamController = null
    if (currentId.value) {
      try {
        await openConversation(currentId.value)
        if (!view.value?.messages.some((item) => item.content === message && item.role === 'user'))
          draft.value = message
      } catch (cause) {
        error.value = errorMessage(cause)
      }
    } else draft.value = message
    pendingMessage.value = ''
    if (done) {
      assistantText.value = ''
      streamError.value = ''
      streamProposals.value = []
      activity.value = ''
    }
    busy.value = false
  }
}

function switchConversation(id: string) {
  if (busy.value) return
  if (!id) newConversation()
  else {
    assistantText.value = ''
    streamError.value = ''
    streamProposals.value = []
    void openConversation(id)
  }
}

onUnmounted(() => streamController?.abort())

async function decide(proposal: ChangeProposal, confirm: boolean) {
  if (busy.value || (confirm && dirty.value)) return
  busy.value = true
  error.value = ''
  try {
    if (confirm) {
      await api.confirmProposal(props.projectId, proposal.id)
      await workspace.load(props.projectId)
      await runtime.load(props.projectId)
      activity.value = '修改已确认，画布已同步'
    } else {
      await api.cancelProposal(props.projectId, proposal.id)
      activity.value = '建议已取消，拓扑未变化'
    }
    await openConversation(currentId.value)
  } catch (cause) {
    error.value = errorMessage(cause)
  } finally {
    busy.value = false
  }
}

function nodeName(id: string | null) {
  return workspace.nodes.find((node) => node.id === id)?.data.displayName || '节点'
}

function proposalNodeName(id: string | null, proposal: ChangeProposal) {
  const added = proposal.operations.find(
    (operation) => operation.type === 'ADD_NODE' && operation.nodeId === id,
  )
  return added?.displayName || nodeName(id)
}

function configLabel(operation: ChangeOperation) {
  const node = workspace.nodes.find((item) => item.id === operation.nodeId)
  const type = node?.data.componentType || operation.componentType || ''
  return (
    workspace.definition(type)?.configFields.find((field) => field.key === operation.configKey)
      ?.label || '配置项'
  )
}

function operationLabel(operation: ChangeOperation, proposal: ChangeProposal) {
  if (operation.type === 'ADD_NODE')
    return `新增 ${operation.displayName || workspace.definition(operation.componentType || '')?.name || '组件'}`
  if (operation.type === 'REMOVE_NODE')
    return `删除 ${operation.displayName || nodeName(operation.nodeId)}`
  if (operation.type === 'CONNECT')
    return `连接 ${proposalNodeName(operation.sourceNodeId, proposal)} → ${proposalNodeName(operation.targetNodeId, proposal)} · ${operation.relationType ? relationLabels[operation.relationType] : '连接'}`
  if (operation.type === 'DISCONNECT')
    return `断开 ${proposalNodeName(operation.sourceNodeId, proposal)} → ${proposalNodeName(operation.targetNodeId, proposal)} · ${operation.relationType ? relationLabels[operation.relationType] : '连接'}`
  return `修改 ${operation.displayName || nodeName(operation.nodeId)} · ${configLabel(operation)}: ${operation.oldValue || '—'} → ${operation.newValue || '—'}`
}

watch(
  () => props.projectId,
  () => void load(),
  { immediate: true },
)
</script>

<template>
  <aside class="assistant-panel inspector panel" aria-label="架构助手">
    <div class="assistant-heading">
      <div>
        <strong>架构助手</strong><small>{{ project?.name || '当前项目' }}</small>
      </div>
      <button class="quiet-button" title="新建对话" :disabled="busy" @click="newConversation">
        新对话
      </button>
    </div>
    <div class="assistant-session">
      <label for="assistant-conversation">对话</label>
      <select
        id="assistant-conversation"
        :value="currentId"
        :disabled="busy"
        @change="switchConversation(($event.target as HTMLSelectElement).value)"
      >
        <option v-if="!currentId" value="">新对话</option>
        <option v-for="item in conversations" :key="item.id" :value="item.id">
          {{ new Date(item.createdAt).toLocaleString('zh-CN') }}
        </option>
      </select>
    </div>
    <div v-if="loading" class="assistant-notice">正在读取对话…</div>
    <div v-else-if="!configured" class="assistant-notice assistant-unconfigured">
      <strong>AI 模型尚未配置</strong>
      <p>
        填写本地 .env 中的 BASE_URL、MODEL 和 API_KEY 后重启平台即可使用。画布与 Runtime
        可继续使用。
      </p>
    </div>
    <div v-if="error" class="assistant-error" role="alert">{{ error }}</div>
    <div ref="messagesContainer" class="assistant-messages" aria-live="polite">
      <div v-if="configured && !view?.messages.length && !pendingMessage" class="assistant-empty">
        基于当前拓扑提问，或让助手提出架构修改。修改需要你确认后才会写入项目。
      </div>
      <div
        v-for="item in view?.messages || []"
        :key="item.id"
        class="assistant-message"
        :class="item.role"
      >
        <span class="assistant-role">{{ item.role === 'user' ? '你' : '架构助手' }}</span>
        <p>{{ item.content }}</p>
      </div>
      <div v-if="pendingMessage" class="assistant-message user" data-testid="pending-message">
        <span class="assistant-role">你</span>
        <p>{{ pendingMessage }}</p>
      </div>
      <div v-if="busy || assistantText || streamError" class="assistant-message assistant">
        <span class="assistant-role">架构助手{{ busy ? ' · 生成中' : '' }}</span>
        <p>{{ assistantText }}<span v-if="busy" class="assistant-cursor">▍</span></p>
        <small v-if="streamError" class="assistant-stream-error">{{ streamError }}</small>
      </div>
      <section
        v-for="proposal in shownProposals"
        :key="proposal.id"
        class="assistant-proposal"
        :class="proposal.status.toLowerCase()"
      >
        <div class="assistant-proposal-heading">
          <strong>建议修改</strong>
        </div>
        <p>{{ proposal.summary }}</p>
        <ul>
          <li v-for="(operation, index) in proposal.operations" :key="index">
            {{ operationLabel(operation, proposal) }}
          </li>
        </ul>
        <div v-if="proposal.status === 'PENDING'" class="assistant-proposal-actions">
          <button
            class="primary-button"
            :disabled="busy || dirty"
            :title="dirty ? '请先保存画布中的本地修改' : ''"
            @click="decide(proposal, true)"
          >
            确认修改
          </button>
          <button class="quiet-button" :disabled="busy" @click="decide(proposal, false)">
            取消
          </button>
        </div>
        <small v-else>{{
          proposal.status === 'CONFIRMED'
            ? '已确认'
            : proposal.status === 'CANCELLED'
              ? '已取消'
              : '架构已变化，请重新生成建议'
        }}</small>
        <small v-if="dirty && proposal.status === 'PENDING'">请先保存画布中的修改</small>
      </section>
    </div>
    <div v-if="activity || busy" class="assistant-activity" role="status">
      {{ activity }}
    </div>
    <div v-if="configured" class="assistant-composer">
      <div class="assistant-shortcuts">
        <button @click="send('解释当前架构')">解释架构</button>
        <button @click="send('检查当前组件连接')">检查连接</button>
        <button @click="send('当前哪些组件正在运行？')">运行状态</button>
      </div>
      <textarea
        v-model="draft"
        rows="3"
        maxlength="2000"
        aria-label="向架构助手输入消息"
        placeholder="询问当前架构或提出修改…"
        @keydown.ctrl.enter.prevent="send()"
      />
      <div class="assistant-compose-footer">
        <button v-if="busy" class="quiet-button" @click="stopGenerating">停止生成</button>
        <small>Ctrl + Enter 发送</small
        ><button class="primary-button" :disabled="!canSend" @click="send()">
          {{ busy ? '处理中…' : '发送' }}
        </button>
      </div>
    </div>
  </aside>
</template>
