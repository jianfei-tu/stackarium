<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { storeToRefs } from 'pinia'
import ArchitectureCanvas from '../components/ArchitectureCanvas.vue'
import ComponentPalette from '../components/ComponentPalette.vue'
import InspectorPanel from '../components/InspectorPanel.vue'
import RuntimePanel from '../components/RuntimePanel.vue'
import RuntimeActions from '../components/RuntimeActions.vue'
import AssistantPanel from '../components/AssistantPanel.vue'
import { useWorkspaceStore } from '../stores/workspace'
import { useRuntimeStore } from '../stores/runtime'

const route = useRoute()
const store = useWorkspaceStore()
const runtimeStore = useRuntimeStore()
const { project, dirty, busy, error, message } = storeToRefs(store)
const canvas = ref<InstanceType<typeof ArchitectureCanvas> | null>(null)
const wideScreen = window.innerWidth >= 1024
const showPalette = ref(wideScreen)
const showInspector = ref(wideScreen)
const showAssistant = ref(false)
const showRuntime = ref(window.innerWidth >= 768)
const savedSizes = (() => {
  try {
    return JSON.parse(localStorage.getItem('stackarium-panel-sizes') || '{}') as Record<
      string,
      number
    >
  } catch {
    return {} as Record<string, number>
  }
})()
const leftWidth = ref(savedSizes.left || (window.innerWidth < 1280 ? 208 : 236))
const rightWidth = ref(savedSizes.right || (window.innerWidth < 1280 ? 280 : 312))
const bottomHeight = ref(savedSizes.bottom || (window.innerWidth < 1280 ? 180 : 220))

type PanelSide = 'left' | 'right' | 'bottom'
function resize(side: PanelSide, event: PointerEvent) {
  if (window.innerWidth < 1024) return
  const startX = event.clientX
  const startY = event.clientY
  const start =
    side === 'left' ? leftWidth.value : side === 'right' ? rightWidth.value : bottomHeight.value
  const move = (next: PointerEvent) => {
    if (side === 'left')
      leftWidth.value = Math.max(208, Math.min(320, start + next.clientX - startX))
    if (side === 'right')
      rightWidth.value = Math.max(280, Math.min(420, start - next.clientX + startX))
    if (side === 'bottom')
      bottomHeight.value = Math.max(
        160,
        Math.min(window.innerHeight * 0.4, start - next.clientY + startY),
      )
  }
  const stop = () => {
    window.removeEventListener('pointermove', move)
    window.removeEventListener('pointerup', stop)
    localStorage.setItem(
      'stackarium-panel-sizes',
      JSON.stringify({
        left: leftWidth.value,
        right: rightWidth.value,
        bottom: bottomHeight.value,
      }),
    )
  }
  window.addEventListener('pointermove', move)
  window.addEventListener('pointerup', stop, { once: true })
}

function resetSize(side: PanelSide) {
  if (side === 'left') leftWidth.value = window.innerWidth < 1280 ? 208 : 236
  if (side === 'right') rightWidth.value = window.innerWidth < 1280 ? 280 : 312
  if (side === 'bottom') bottomHeight.value = window.innerWidth < 1280 ? 180 : 220
  localStorage.setItem(
    'stackarium-panel-sizes',
    JSON.stringify({ left: leftWidth.value, right: rightWidth.value, bottom: bottomHeight.value }),
  )
}

onMounted(() => {
  void store.load(String(route.params.projectId))
  void runtimeStore.load(String(route.params.projectId))
})
onUnmounted(() => runtimeStore.disconnect())
watch(
  () => route.params.projectId,
  (id) => {
    void store.load(String(id))
    void runtimeStore.load(String(id))
  },
)

watch(message, (value, _previous, onCleanup) => {
  if (!value) return
  const timer = window.setTimeout(() => {
    message.value = ''
  }, 4000)
  onCleanup(() => window.clearTimeout(timer))
})

function onKeydown(event: KeyboardEvent) {
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 's') {
    event.preventDefault()
    store.save()
  }
}

function togglePalette() {
  showPalette.value = !showPalette.value
  if (window.innerWidth < 1024 && showPalette.value) showInspector.value = false
}

function toggleInspector() {
  const next = !showInspector.value
  showAssistant.value = false
  showInspector.value = next
  if (window.innerWidth < 1024 && showInspector.value) showPalette.value = false
}

function toggleAssistant() {
  const next = !showAssistant.value
  showInspector.value = false
  showAssistant.value = next
  if (window.innerWidth < 1024 && next) showPalette.value = false
  if (next) void nextTick(() => canvas.value?.fitView({ padding: 0.18, duration: 180 }))
}

function focusRuntimeNode(id: string) {
  store.selectNode(id)
  showAssistant.value = false
  showInspector.value = true
  if (window.innerWidth < 1024) showPalette.value = false
}
</script>

<template>
  <div class="workspace" tabindex="-1" @keydown="onKeydown">
    <header class="workbench-toolbar">
      <RouterLink class="toolbar-brand" to="/" title="返回项目列表"
        ><span class="brand-mark">▤</span><span>栈境</span></RouterLink
      ><span class="toolbar-divider" />
      <div class="toolbar-project">
        <strong>{{ project?.name || '正在打开项目…' }}</strong
        ><small>{{ dirty ? '未保存修改' : '已保存' }}</small>
      </div>
      <div class="toolbar-center">
        <button
          class="toolbar-button"
          title="适配画布"
          @click="canvas?.fitView({ padding: 0.25, duration: 180 })"
        >
          适配视图
        </button>
      </div>
      <div class="toolbar-right">
        <span class="toolbar-phase">架构建模</span>
        <RuntimeActions />
        <button
          class="toolbar-button"
          :class="{ pressed: showAssistant }"
          :aria-pressed="showAssistant"
          title="切换架构助手"
          @click="toggleAssistant"
        >
          架构助手
        </button>
        <button
          class="toolbar-button"
          :class="{ pressed: showPalette }"
          :aria-pressed="showPalette"
          title="切换组件库"
          @click="togglePalette"
        >
          <span class="desktop-label">组件库</span><span class="mobile-label">组件</span></button
        ><button
          class="toolbar-button"
          :class="{ pressed: showInspector }"
          :aria-pressed="showInspector"
          title="切换 Inspector"
          @click="toggleInspector"
        >
          <span class="desktop-label">Inspector</span><span class="mobile-label">详情</span></button
        ><button
          class="toolbar-button"
          :class="{ pressed: showRuntime }"
          :aria-pressed="showRuntime"
          title="切换运行面板"
          @click="showRuntime = !showRuntime"
        >
          <span class="desktop-label">Runtime</span><span class="mobile-label">运行</span></button
        ><button
          class="primary-button save-button"
          :disabled="busy || !dirty"
          @click="store.save()"
        >
          {{ busy ? '处理中…' : '保存架构' }}
        </button>
      </div>
    </header>
    <div v-if="error" class="workspace-alert" role="alert">
      {{ error }} <button @click="store.load(String(route.params.projectId))">重新加载</button>
    </div>
    <div v-else-if="message" class="workspace-feedback" role="status">{{ message }}</div>
    <div
      class="workspace-main"
      :style="{
        '--palette-width': `${leftWidth}px`,
        '--inspector-width': `${showAssistant ? Math.max(360, rightWidth) : rightWidth}px`,
        '--runtime-height': `${bottomHeight}px`,
      }"
      :class="{
        'without-palette': !showPalette,
        'without-inspector': !showInspector && !showAssistant,
        'without-runtime': !showRuntime,
      }"
    >
      <ComponentPalette v-if="showPalette" @add="canvas?.addAtCenter($event)" />
      <ArchitectureCanvas ref="canvas" />
      <InspectorPanel v-if="showInspector" />
      <AssistantPanel v-if="showAssistant" :project-id="String(route.params.projectId)" />
      <RuntimePanel v-if="showRuntime" @focus-node="focusRuntimeNode" />
      <div
        v-if="showPalette"
        class="panel-resizer palette-resizer"
        role="separator"
        aria-label="调整组件库宽度"
        aria-orientation="vertical"
        @pointerdown="resize('left', $event)"
        @dblclick="resetSize('left')"
      />
      <div
        v-if="showInspector || showAssistant"
        class="panel-resizer inspector-resizer"
        role="separator"
        aria-label="调整 Inspector 宽度"
        aria-orientation="vertical"
        @pointerdown="resize('right', $event)"
        @dblclick="resetSize('right')"
      />
      <div
        v-if="showRuntime"
        class="panel-resizer runtime-resizer"
        role="separator"
        aria-label="调整运行面板高度"
        aria-orientation="horizontal"
        @pointerdown="resize('bottom', $event)"
        @dblclick="resetSize('bottom')"
      />
    </div>
    <div class="workspace-statusbar">
      <span>STACKARIUM / 工作台</span
      ><span class="statusbar-right">{{
        busy ? '读取中' : dirty ? '有未保存修改' : '架构已同步'
      }}</span>
    </div>
  </div>
</template>
