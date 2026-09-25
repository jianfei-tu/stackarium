<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { relationLabels } from '../domain'
import { useWorkspaceStore } from '../stores/workspace'
import { useRuntimeStore } from '../stores/runtime'
import { observationPresentation } from '../runtimePresentation'

const store = useWorkspaceStore()
const runtimeStore = useRuntimeStore()
const { selectedNode, selectedEdge, nodes } = storeToRefs(store)
const draftName = ref('')
const draftConfig = ref<Record<string, string>>({})
const showConfirm = ref(false)
const definition = computed(() =>
  selectedNode.value ? store.definition(selectedNode.value.data.componentType) : null,
)
const currentObservation = computed(() =>
  selectedNode.value ? runtimeStore.component(selectedNode.value.id) : undefined,
)
const currentStatus = computed(() =>
  observationPresentation(currentObservation.value, runtimeStore.runtime?.status),
)
const sourceName = computed(
  () => nodes.value.find((node) => node.id === selectedEdge.value?.source)?.data.displayName || '—',
)
const targetName = computed(
  () => nodes.value.find((node) => node.id === selectedEdge.value?.target)?.data.displayName || '—',
)
const changed = computed(
  () =>
    selectedNode.value &&
    (draftName.value !== selectedNode.value.data.displayName ||
      JSON.stringify(draftConfig.value) !== JSON.stringify(selectedNode.value.data.config)),
)
const valid = computed(
  () =>
    draftName.value.trim() &&
    definition.value?.configFields.every((field) => {
      const value = draftConfig.value[field.key]?.trim()
      if (field.required && !value) return false
      if (
        field.kind === 'number' &&
        (!/^\d+$/.test(value || '') || Number(value) < 1 || Number(value) > 65535)
      )
        return false
      return true
    }),
)

watch(
  selectedNode,
  (node) => {
    draftName.value = node?.data.displayName || ''
    draftConfig.value = { ...node?.data.config }
    showConfirm.value = false
  },
  { immediate: true },
)

function apply() {
  if (!selectedNode.value || !valid.value) return
  store.updateNode(selectedNode.value.id, draftName.value.trim(), { ...draftConfig.value })
}

function cancel() {
  draftName.value = selectedNode.value?.data.displayName || ''
  draftConfig.value = { ...selectedNode.value?.data.config }
}
</script>

<template>
  <aside class="inspector panel">
    <div class="panel-heading">
      <strong>Inspector</strong
      ><button
        v-if="selectedNode || selectedEdge"
        class="plain-icon"
        title="取消选择"
        aria-label="取消选择"
        @click="store.selectNode(null)"
      >
        ×</button
      ><span v-else class="panel-count">详情</span>
    </div>
    <div class="panel-scroll inspector-scroll">
      <div v-if="selectedNode && definition" :key="selectedNode.id">
        <div class="inspector-identity">
          <span class="eyebrow">{{ definition.category }}</span>
          <h2>{{ selectedNode.data.displayName }}</h2>
          <p>{{ definition.description }}</p>
        </div>
        <section class="inspector-section">
          <h3>身份</h3>
          <div class="property-row">
            <span>组件类型</span><strong>{{ definition.name }}</strong>
          </div>
        </section>
        <section class="inspector-section">
          <h3>连接</h3>
          <div class="property-row">
            <span>入站</span
            ><strong>{{
              store.edges.filter((edge) => edge.target === selectedNode?.id).length
            }}</strong>
          </div>
          <div class="property-row">
            <span>出站</span
            ><strong>{{
              store.edges.filter((edge) => edge.source === selectedNode?.id).length
            }}</strong>
          </div>
        </section>
        <section class="inspector-section">
          <h3>配置</h3>
          <label class="field"
            ><span>实例名称</span><input v-model="draftName" maxlength="120" /><small
              v-if="!draftName.trim()"
              class="field-error"
              >请输入实例名称</small
            ></label
          ><label v-for="field in definition.configFields" :key="field.key" class="field"
            ><span>{{ field.label }}</span
            ><input
              v-model="draftConfig[field.key]"
              :inputmode="field.kind === 'number' ? 'numeric' : 'text'"
              :maxlength="200"
            /><small
              v-if="
                field.kind === 'number' &&
                (!/^\d+$/.test(draftConfig[field.key] || '') ||
                  Number(draftConfig[field.key]) < 1 ||
                  Number(draftConfig[field.key]) > 65535)
              "
              class="field-error"
              >请输入 1–65535 的端口</small
            ></label
          >
          <div v-if="changed" class="inspector-actions">
            <button type="button" class="primary-button" :disabled="!valid" @click="apply">
              应用修改</button
            ><button type="button" class="quiet-button" @click="cancel">取消</button>
          </div>
        </section>
        <section class="inspector-section">
          <h3>状态</h3>
          <div class="status-line" :class="`runtime-${currentStatus.tone}`">
            <span class="status-dot" /> {{ currentStatus.label }}
          </div>
          <p v-if="currentObservation?.containerId" class="muted compact">
            容器 ID {{ currentObservation.containerId.slice(0, 12) }}
          </p>
          <p v-else class="muted compact">
            {{
              definition.runtimeAvailable
                ? '状态由 Docker 实际观测提供。'
                : '该组件目前仅支持拓扑建模。'
            }}
          </p>
        </section>
        <div class="inspector-danger">
          <button
            v-if="!showConfirm"
            class="danger-button"
            type="button"
            @click="showConfirm = true"
          >
            移除节点
          </button>
          <div v-else class="confirm-remove">
            <p>移除节点及其连接？</p>
            <button class="danger-button" type="button" @click="store.removeSelection()">
              确认移除</button
            ><button class="quiet-button" type="button" @click="showConfirm = false">取消</button>
          </div>
        </div>
      </div>
      <div v-else-if="selectedEdge" class="edge-inspector">
        <div class="inspector-identity">
          <span class="eyebrow">连接</span>
          <h2>{{ relationLabels[selectedEdge.data!.relationType] }}连接</h2>
          <p>方向由输出端指向输入端</p>
        </div>
        <section class="inspector-section">
          <h3>连接关系</h3>
          <div class="property-row">
            <span>来源</span><strong>{{ sourceName }}</strong>
          </div>
          <div class="property-row">
            <span>目标</span><strong>{{ targetName }}</strong>
          </div>
          <div class="property-row">
            <span>类型</span><strong>{{ relationLabels[selectedEdge.data!.relationType] }}</strong>
          </div>
        </section>
        <div class="inspector-danger">
          <button v-if="!showConfirm" class="danger-button" @click="showConfirm = true">
            移除连接
          </button>
          <div v-else class="confirm-remove">
            <p>移除这条连接？</p>
            <button class="danger-button" @click="store.removeSelection()">确认移除</button
            ><button class="quiet-button" @click="showConfirm = false">取消</button>
          </div>
        </div>
      </div>
      <div v-else class="inspector-empty">
        <strong>选择一个节点或连接</strong>
        <p>在这里查看身份、连接关系，并修改节点配置。</p>
        <div class="shortcut-note">从节点右侧端口拖向目标节点左侧端口，可建立连接。</div>
      </div>
    </div>
  </aside>
</template>
