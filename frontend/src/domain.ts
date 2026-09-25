export type RelationType = 'CALL' | 'CACHE' | 'MESSAGE' | 'PROXY' | 'DISCOVERY'

export interface Project {
  id: string
  name: string
  description: string
  createdAt: string
  updatedAt: string
}

export interface ConfigField {
  key: string
  label: string
  kind: 'text' | 'number'
  defaultValue: string
  required: boolean
}

export interface ComponentDefinition {
  type: string
  name: string
  category: string
  description: string
  configFields: ConfigField[]
  connections: { relation: RelationType; targetTypes: string[] }[]
  runtimeAvailable: boolean
}

export interface TopologyNode {
  id: string
  componentType: string
  displayName: string
  x: number
  y: number
  config: Record<string, string>
}

export interface TopologyEdge {
  id: string
  source: string
  target: string
  relationType: RelationType
}

export interface Topology {
  projectId: string
  revision: number
  nodes: TopologyNode[]
  edges: TopologyEdge[]
}

export type RuntimeStatus =
  | 'GENERATED'
  | 'PREPARING'
  | 'STARTING'
  | 'RUNNING'
  | 'DEGRADED'
  | 'STOPPING'
  | 'STOPPED'
  | 'FAILED'

export interface ContainerObservation {
  nodeId: string
  componentType: string
  displayName: string
  serviceName: string
  containerId: string
  state: string
  health: string
  observedAt: string
}

export interface RuntimeView {
  id: string
  projectId: string
  topologyRevision: number
  status: RuntimeStatus
  errorPhase: string | null
  errorNodeId: string | null
  errorMessage: string | null
  createdAt: string
  startedAt: string | null
  stoppedAt: string | null
  observedAt: string
  components: ContainerObservation[]
}

export interface RuntimeEvent {
  eventId: string
  runtimeId: string
  timestamp: string
  eventType:
    | 'runtime.generated'
    | 'runtime.status.changed'
    | 'component.status.changed'
    | 'REQUEST_TRACE'
    | 'CACHE_ACCESS'
    | 'MESSAGE_PUBLISHED'
    | 'MESSAGE_CONSUMED'
    | 'SENTINEL_BLOCKED'
  nodeId: string | null
  payload: Record<string, string>
}

export interface RuntimeLogs {
  nodeId: string
  observedAt: string
  stdout: string
  stderr: string
}

export const runtimeLabels: Record<RuntimeStatus, string> = {
  GENERATED: '已生成',
  PREPARING: '准备中',
  STARTING: '启动中',
  RUNNING: '运行中',
  DEGRADED: '告警',
  STOPPING: '停止中',
  STOPPED: '已停止',
  FAILED: '失败',
}

export const relationLabels: Record<RelationType, string> = {
  CALL: '调用',
  CACHE: '缓存',
  MESSAGE: '消息',
  PROXY: '代理',
  DISCOVERY: '发现',
}

export interface AssistantConversation {
  id: string
  projectId: string
  createdAt: string
  updatedAt: string
}

export interface AssistantMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  createdAt: string
}

export interface ChangeOperation {
  type: 'ADD_NODE' | 'REMOVE_NODE' | 'CONNECT' | 'DISCONNECT' | 'UPDATE_CONFIG'
  nodeId: string | null
  componentType: string | null
  displayName: string | null
  config: Record<string, string> | null
  sourceNodeId: string | null
  targetNodeId: string | null
  relationType: RelationType | null
  configKey: string | null
  oldValue: string | null
  newValue: string | null
}

export interface ChangeProposal {
  id: string
  projectId: string
  conversationId: string
  expectedRevision: number
  summary: string
  operations: ChangeOperation[]
  status: 'PENDING' | 'CONFIRMED' | 'CANCELLED' | 'STALE'
  createdAt: string
}

export interface ConversationView {
  conversation: AssistantConversation
  messages: AssistantMessage[]
  proposals: ChangeProposal[]
  modelConfigured: boolean
}

export interface AssistantStreamEvent {
  type: 'MESSAGE_DELTA' | 'TOOL_STARTED' | 'TOOL_FINISHED' | 'PROPOSAL_CREATED' | 'DONE' | 'ERROR'
  text: string | null
  tool: string | null
  proposal: ChangeProposal | null
}
