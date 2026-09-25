import { computed, ref, shallowRef } from 'vue'
import { defineStore } from 'pinia'
import { api, errorMessage } from '../api'
import type {
  ComponentDefinition,
  Project,
  RelationType,
  Topology,
  TopologyEdge,
  TopologyNode,
} from '../domain'

export interface CanvasNodeData {
  componentType: string
  displayName: string
  config: Record<string, string>
}

export interface CanvasEdgeData {
  relationType: RelationType
}

export interface CanvasNode {
  id: string
  type: string
  position: { x: number; y: number }
  data: CanvasNodeData
}

export interface CanvasEdge {
  id: string
  source: string
  target: string
  type: string
  animated: boolean
  markerEnd: string
  data: CanvasEdgeData
  class: string
}

export const useWorkspaceStore = defineStore('workspace', () => {
  const project = ref<Project | null>(null)
  const definitions = ref<ComponentDefinition[]>([])
  const nodes = shallowRef<CanvasNode[]>([])
  const edges = shallowRef<CanvasEdge[]>([])
  const revision = ref(0)
  const selectedNodeId = ref<string | null>(null)
  const selectedEdgeId = ref<string | null>(null)
  const dirty = ref(false)
  const busy = ref(false)
  const message = ref('')
  const error = ref('')

  const selectedNode = computed(() => nodes.value.find((node) => node.id === selectedNodeId.value))
  const selectedEdge = computed(() => edges.value.find((edge) => edge.id === selectedEdgeId.value))
  const definition = (type: string) => definitions.value.find((item) => item.type === type)

  async function load(projectId: string) {
    busy.value = true
    error.value = ''
    try {
      const [loadedProject, loadedDefinitions, topology] = await Promise.all([
        api.project(projectId),
        api.components(),
        api.topology(projectId),
      ])
      project.value = loadedProject
      definitions.value = loadedDefinitions
      revision.value = topology.revision
      nodes.value = topology.nodes.map((node) => ({
        id: node.id,
        type: 'stackarium',
        position: { x: node.x, y: node.y },
        data: {
          componentType: node.componentType,
          displayName: node.displayName,
          config: node.config,
        },
      }))
      edges.value = topology.edges.map((edge) => canvasEdge(edge))
      selectedNodeId.value = null
      selectedEdgeId.value = null
      dirty.value = false
      message.value = ''
    } catch (cause) {
      error.value = errorMessage(cause)
    } finally {
      busy.value = false
    }
  }

  function canvasEdge(edge: TopologyEdge): CanvasEdge {
    return {
      id: edge.id,
      source: edge.source,
      target: edge.target,
      type: 'smoothstep',
      animated: false,
      markerEnd: 'url(#stackarium-arrow)',
      data: { relationType: edge.relationType },
      class: `edge-${edge.relationType.toLowerCase()}`,
    }
  }

  function selectNode(id: string | null) {
    selectedNodeId.value = id
    selectedEdgeId.value = null
  }

  function selectEdge(id: string | null) {
    selectedEdgeId.value = id
    selectedNodeId.value = null
  }

  function addNode(type: string, x: number, y: number) {
    const item = definition(type)
    if (!item) return
    const config = Object.fromEntries(
      item.configFields.map((field) => [field.key, field.defaultValue]),
    )
    const node: CanvasNode = {
      id: crypto.randomUUID(),
      type: 'stackarium',
      position: { x, y },
      data: { componentType: type, displayName: item.name, config },
    }
    nodes.value = [...nodes.value, node]
    selectNode(node.id)
    dirty.value = true
    message.value = `已添加 ${item.name}`
  }

  function connect(sourceId: string, targetId: string) {
    const source = nodes.value.find((node) => node.id === sourceId)
    const target = nodes.value.find((node) => node.id === targetId)
    if (!source || !target || sourceId === targetId) {
      message.value = '请选择两个不同的节点'
      return
    }
    const rule = definition(source.data.componentType)?.connections.find((candidate) =>
      candidate.targetTypes.includes(target.data.componentType),
    )
    if (!rule) {
      message.value = '此组件组合没有可用的连接类型'
      return
    }
    if (
      edges.value.some(
        (edge) =>
          edge.source === sourceId &&
          edge.target === targetId &&
          edge.data?.relationType === rule.relation,
      )
    ) {
      message.value = '这条连接已经存在'
      return
    }
    const edge = canvasEdge({
      id: crypto.randomUUID(),
      source: sourceId,
      target: targetId,
      relationType: rule.relation,
    })
    edges.value = [...edges.value, edge]
    selectEdge(edge.id)
    dirty.value = true
    message.value = '连接已建立，保存后写入项目'
  }

  function updateNode(id: string, displayName: string, config: Record<string, string>) {
    nodes.value = nodes.value.map((node) =>
      node.id === id ? { ...node, data: { ...node.data, displayName, config } } : node,
    )
    dirty.value = true
    message.value = '配置已修改，尚未保存'
  }

  function removeSelection() {
    if (selectedNodeId.value) {
      nodes.value = nodes.value.filter((node) => node.id !== selectedNodeId.value)
      edges.value = edges.value.filter(
        (edge) => edge.source !== selectedNodeId.value && edge.target !== selectedNodeId.value,
      )
    } else if (selectedEdgeId.value) {
      edges.value = edges.value.filter((edge) => edge.id !== selectedEdgeId.value)
    } else return
    selectNode(null)
    dirty.value = true
    message.value = '已移除，保存后写入项目'
  }

  function positionChanged() {
    dirty.value = true
  }

  async function save() {
    if (!project.value || busy.value) return
    busy.value = true
    error.value = ''
    const payload: Topology = {
      projectId: project.value.id,
      revision: revision.value,
      nodes: nodes.value.map((node): TopologyNode => ({
        id: node.id,
        componentType: node.data.componentType,
        displayName: node.data.displayName,
        x: node.position.x,
        y: node.position.y,
        config: node.data.config,
      })),
      edges: edges.value.map((edge): TopologyEdge => ({
        id: edge.id,
        source: edge.source,
        target: edge.target,
        relationType: edge.data.relationType,
      })),
    }
    try {
      const saved = await api.saveTopology(payload)
      revision.value = saved.revision
      dirty.value = false
      message.value = '架构已保存'
    } catch (cause) {
      error.value = errorMessage(cause)
      message.value = ''
    } finally {
      busy.value = false
    }
  }

  return {
    project,
    definitions,
    nodes,
    edges,
    revision,
    selectedNodeId,
    selectedEdgeId,
    selectedNode,
    selectedEdge,
    dirty,
    busy,
    message,
    error,
    definition,
    load,
    selectNode,
    selectEdge,
    addNode,
    connect,
    updateNode,
    removeSelection,
    positionChanged,
    save,
  }
})
