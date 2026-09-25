import axios from 'axios'
import type {
  ComponentDefinition,
  Project,
  RuntimeEvent,
  RuntimeLogs,
  RuntimeView,
  Topology,
  AssistantConversation,
  AssistantStreamEvent,
  ConversationView,
  ChangeProposal,
} from './domain'

const client = axios.create({ baseURL: '/api/v1', timeout: 10000 })

export const api = {
  async projects() {
    return (await client.get<Project[]>('/projects')).data
  },
  async createProject(name: string, description: string) {
    return (await client.post<Project>('/projects', { name, description })).data
  },
  async project(id: string) {
    return (await client.get<Project>(`/projects/${id}`)).data
  },
  async components() {
    return (await client.get<ComponentDefinition[]>('/components')).data
  },
  async topology(projectId: string) {
    return (await client.get<Topology>(`/projects/${projectId}/topology`)).data
  },
  async saveTopology(topology: Topology) {
    return (
      await client.put<Topology>(`/projects/${topology.projectId}/topology`, {
        expectedRevision: topology.revision,
        nodes: topology.nodes,
        edges: topology.edges,
      })
    ).data
  },
  async latestRuntime(projectId: string) {
    const response = await client.get<RuntimeView>(`/projects/${projectId}/runtime`)
    return response.status === 204 ? null : response.data
  },
  async generateRuntime(projectId: string) {
    return (await client.post<RuntimeView>(`/projects/${projectId}/runtime/generate`)).data
  },
  async startRuntime(projectId: string, runtimeId: string) {
    return (await client.post<RuntimeView>(`/projects/${projectId}/runtime/${runtimeId}/start`))
      .data
  },
  async stopRuntime(projectId: string, runtimeId: string) {
    return (await client.post<RuntimeView>(`/projects/${projectId}/runtime/${runtimeId}/stop`)).data
  },
  async runtimeEvents(projectId: string, runtimeId: string) {
    return (await client.get<RuntimeEvent[]>(`/projects/${projectId}/runtime/${runtimeId}/events`))
      .data
  },
  async runtimeLogs(projectId: string, runtimeId: string, nodeId: string) {
    return (
      await client.get<RuntimeLogs>(`/projects/${projectId}/runtime/${runtimeId}/logs`, {
        params: { nodeId, lines: 100 },
      })
    ).data
  },
  async assistantStatus(projectId: string) {
    return (
      await client.get<{ modelConfigured: boolean }>(`/projects/${projectId}/assistant/status`)
    ).data
  },
  async assistantConversations(projectId: string) {
    return (
      await client.get<AssistantConversation[]>(`/projects/${projectId}/assistant/conversations`)
    ).data
  },
  async createAssistantConversation(projectId: string) {
    return (
      await client.post<AssistantConversation>(`/projects/${projectId}/assistant/conversations`)
    ).data
  },
  async assistantConversation(projectId: string, conversationId: string) {
    return (
      await client.get<ConversationView>(
        `/projects/${projectId}/assistant/conversations/${conversationId}`,
      )
    ).data
  },
  async assistantStream(
    projectId: string,
    conversationId: string,
    message: string,
    signal: AbortSignal,
    onEvent: (event: AssistantStreamEvent) => void,
  ) {
    const response = await fetch(
      `/api/v1/projects/${projectId}/assistant/conversations/${conversationId}/messages`,
      {
        method: 'POST',
        headers: { Accept: 'text/event-stream', 'Content-Type': 'application/json' },
        body: JSON.stringify({ message }),
        signal,
      },
    )
    if (!response.ok) {
      const payload = (await response.json().catch(() => null)) as ServerError | null
      throw new Error(serverErrorMessage(response.status, payload))
    }
    if (!response.body) throw new Error('模型响应没有流式内容')

    const reader = response.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''
    let finished = false
    try {
      while (true) {
        const { value, done } = await reader.read()
        buffer += decoder.decode(value, { stream: !done })
        let boundary: RegExpExecArray | null
        while ((boundary = /\r?\n\r?\n/.exec(buffer))) {
          const frame = buffer.slice(0, boundary.index)
          buffer = buffer.slice(boundary.index + boundary[0].length)
          const data = frame
            .split(/\r?\n/)
            .filter((line) => line.startsWith('data:'))
            .map((line) => line.slice(5).trimStart())
            .join('\n')
          if (!data) continue
          const event = JSON.parse(data) as AssistantStreamEvent
          onEvent(event)
          if (event.type === 'DONE' || event.type === 'ERROR') finished = true
        }
        if (done) break
      }
    } finally {
      reader.releaseLock()
    }
    if (!finished) throw new Error('模型连接中断，请重试')
  },
  async confirmProposal(projectId: string, proposalId: string) {
    return (
      await client.post<Topology>(
        `/projects/${projectId}/assistant/proposals/${proposalId}/confirm`,
      )
    ).data
  },
  async cancelProposal(projectId: string, proposalId: string) {
    return (
      await client.post<ChangeProposal>(
        `/projects/${projectId}/assistant/proposals/${proposalId}/cancel`,
      )
    ).data
  },
}

interface ServerError {
  code?: string
  message?: string
}

function serverErrorMessage(status: number, payload: ServerError | null | undefined): string {
  const messages: Record<string, string> = {
    REVISION_CONFLICT: '架构已变化，请重新加载后重试',
    PROPOSAL_NOT_FOUND: '该修改建议已不存在，请重新生成建议',
    CONVERSATION_NOT_FOUND: '该对话已不存在，请新建对话',
    RUNTIME_STALE: '架构已修改，请重新生成运行环境',
    RUNTIME_GENERATE_FAILED: '生成运行环境失败，请检查 Docker Desktop 和后端日志',
    RUNTIME_LOGS_FAILED: '读取容器日志失败，请检查 Docker Desktop 和后端日志',
    INVALID_COMPOSE: '运行配置未通过验证，请检查组件配置',
  }
  if (payload?.code && messages[payload.code]) return messages[payload.code]
  if (payload?.code?.startsWith('AI_') && payload.message) return payload.message
  if (status >= 500) return '服务暂时不可用，请稍后重试'
  if (
    payload?.message &&
    !/Exception|Internal Server Error|revision mismatch/i.test(payload.message)
  )
    return payload.message
  if (status === 404) return '内容已不存在，请刷新后重试'
  if (status === 409) return '内容已发生变化，请刷新后重试'
  return '请求未完成，请重试'
}

export function errorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    if (!error.response) return '无法连接到后端服务，请确认平台仍在运行'
    return serverErrorMessage(error.response.status, error.response.data as ServerError | null)
  }
  if (error instanceof TypeError) return '无法连接到后端服务，请确认平台仍在运行'
  return error instanceof Error ? error.message : '请求未完成，请重试'
}
