<script setup lang="ts">
import { computed } from 'vue'
import { Handle, Position, type NodeProps } from '@vue-flow/core'
import { useWorkspaceStore, type CanvasNodeData } from '../stores/workspace'
import { useRuntimeStore } from '../stores/runtime'
import { observationPresentation } from '../runtimePresentation'

const props = defineProps<NodeProps<CanvasNodeData>>()
const store = useWorkspaceStore()
const runtimeStore = useRuntimeStore()
const definition = computed(() => store.definition(props.data.componentType))
const detail = computed(() => {
  const first = definition.value?.configFields[0]
  return first
    ? `${first.label}  ${props.data.config[first.key] || '—'}`
    : definition.value?.description || ''
})
const runtimeState = computed(() =>
  observationPresentation(runtimeStore.component(props.id), runtimeStore.runtime?.status),
)
</script>

<template>
  <div
    class="architecture-node"
    :class="[
      { 'is-selected': selected, 'event-pulse': runtimeStore.recentEventNodeId === id },
      `runtime-${runtimeState.tone}`,
    ]"
  >
    <Handle type="target" :position="Position.Left" class="node-handle" title="输入连接" />
    <div class="node-track" />
    <div class="node-header">
      <span class="node-glyph">{{ definition?.name.slice(0, 1) || '?' }}</span
      ><strong :title="data.displayName">{{ data.displayName }}</strong
      ><span class="node-idle" :title="runtimeState.label" />
    </div>
    <div class="node-body">
      <span class="node-type">{{ definition?.name || '未知组件' }}</span
      ><span class="node-detail" :title="detail">{{ detail }}</span>
    </div>
    <div class="node-footer">
      {{ definition?.runtimeAvailable ? runtimeState.label : '仅拓扑 · 暂不支持运行' }}
    </div>
    <Handle type="source" :position="Position.Right" class="node-handle" title="输出连接" />
  </div>
</template>
