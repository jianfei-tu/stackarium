<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { Background } from '@vue-flow/background'
import { VueFlow, useVueFlow, type Connection } from '@vue-flow/core'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import ArchitectureNode from './ArchitectureNode.vue'
import { useWorkspaceStore } from '../stores/workspace'

const store = useWorkspaceStore()
const { nodes, edges } = storeToRefs(store)
const wrapper = ref<HTMLElement | null>(null)
const { screenToFlowCoordinate, fitView, zoomIn, zoomOut } = useVueFlow()
const nodeCount = computed(() => nodes.value.length)

function addAtCenter(type: string) {
  if (!wrapper.value) return
  const rect = wrapper.value.getBoundingClientRect()
  const point = screenToFlowCoordinate({
    x: rect.left + rect.width / 2,
    y: rect.top + rect.height / 2,
  })
  store.addNode(type, point.x - 98 + nodeCount.value * 12, point.y - 50 + nodeCount.value * 12)
}

function onDrop(event: DragEvent) {
  event.preventDefault()
  const type = event.dataTransfer?.getData('application/stackarium-component')
  if (!type) return
  const point = screenToFlowCoordinate({ x: event.clientX, y: event.clientY })
  store.addNode(type, point.x - 98, point.y - 50)
}

function onConnect(connection: Connection) {
  store.connect(connection.source, connection.target)
}

onMounted(async () => {
  await nextTick()
  if (nodeCount.value) fitView({ padding: 0.25, duration: 0 })
})

defineExpose({ addAtCenter, fitView })
</script>

<template>
  <main ref="wrapper" class="canvas-wrap" @dragover.prevent @drop="onDrop">
    <VueFlow
      v-model:nodes="nodes"
      v-model:edges="edges"
      :min-zoom="0.35"
      :max-zoom="1.8"
      :delete-key-code="null"
      :connection-radius="24"
      fit-view-on-init
      @connect="onConnect"
      @node-click="store.selectNode($event.node.id)"
      @edge-click="store.selectEdge($event.edge.id)"
      @pane-click="store.selectNode(null)"
      @node-drag-stop="store.positionChanged()"
    >
      <Background pattern-color="#27363b" :gap="20" :size="1" />
      <template #node-stackarium="nodeProps"><ArchitectureNode v-bind="nodeProps" /></template>
      <svg>
        <defs>
          <marker
            id="stackarium-arrow"
            markerWidth="10"
            markerHeight="10"
            refX="8"
            refY="5"
            orient="auto"
            markerUnits="strokeWidth"
          >
            <path d="M 1 1 L 8 5 L 1 9" fill="none" stroke="#819196" stroke-width="1.5" />
          </marker>
        </defs>
      </svg>
    </VueFlow>
    <div v-if="!nodeCount" class="canvas-empty">
      <strong>从左侧添加组件</strong>
      <p>把服务、数据库或流量入口放到画布，再从端口建立连接。</p>
    </div>
    <div class="mobile-canvas-note">窄屏可查看架构；拖动和连线请使用桌面宽度。</div>
    <div class="canvas-controls">
      <button type="button" title="缩小" aria-label="缩小" @click="zoomOut()">−</button
      ><button type="button" title="适配画布" @click="fitView({ padding: 0.25, duration: 180 })">
        适配</button
      ><button type="button" title="放大" aria-label="放大" @click="zoomIn()">+</button>
    </div>
    <div class="canvas-status">{{ nodeCount }} 节点 <span>·</span> {{ edges.length }} 连接</div>
  </main>
</template>
