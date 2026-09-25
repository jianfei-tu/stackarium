<script setup lang="ts">
import { computed } from 'vue'
import { storeToRefs } from 'pinia'
import { useWorkspaceStore } from '../stores/workspace'
import { useRuntimeStore } from '../stores/runtime'
import { runtimeLabels } from '../domain'

const workspace = useWorkspaceStore()
const runtimeStore = useRuntimeStore()
const { nodes, dirty } = storeToRefs(workspace)
const { runtime, busy, canStart, canStop } = storeToRefs(runtimeStore)
const unsupported = computed(() =>
  nodes.value.filter((node) => !workspace.definition(node.data.componentType)?.runtimeAvailable),
)
const canGenerate = computed(
  () =>
    !dirty.value &&
    nodes.value.length > 0 &&
    !unsupported.value.length &&
    (!runtime.value || runtime.value.status === 'STOPPED'),
)
const stale = computed(() => runtime.value && runtime.value.topologyRevision !== workspace.revision)
const hint = computed(() =>
  dirty.value
    ? '请先保存架构'
    : unsupported.value.length
      ? `暂不支持运行：${unsupported.value.map((node) => node.data.displayName).join('、')}`
      : '生成当前拓扑的 Docker Compose 环境',
)
</script>

<template>
  <div class="runtime-actions">
    <span
      v-if="runtime"
      class="toolbar-runtime-status"
      :class="`state-${runtime.status.toLowerCase()}`"
      >{{ runtimeLabels[runtime.status] }}</span
    >
    <button
      v-if="!runtime || runtime.status === 'STOPPED'"
      class="toolbar-button"
      :disabled="!!busy || !canGenerate"
      :title="hint"
      @click="runtimeStore.act('generate')"
    >
      生成运行环境
    </button>
    <button
      v-if="runtime && canStart"
      class="toolbar-button"
      :disabled="!!busy || !!stale"
      :title="stale ? '架构已修改，请重新生成运行环境' : '启动真实 Docker 容器'"
      @click="runtimeStore.act('start')"
    >
      启动
    </button>
    <button
      v-if="runtime && canStop"
      class="toolbar-button"
      :disabled="!!busy"
      @click="runtimeStore.act('stop')"
    >
      停止
    </button>
  </div>
</template>
