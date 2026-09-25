<script setup lang="ts">
import { computed, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { useWorkspaceStore } from '../stores/workspace'

const emit = defineEmits<{ add: [type: string] }>()
const store = useWorkspaceStore()
const { definitions } = storeToRefs(store)
const query = ref('')
const categories = ['流量入口', '应用服务', '数据存储', '缓存', '消息', '治理']
const groups = computed(() =>
  categories
    .map((category) => ({
      category,
      items: definitions.value.filter(
        (item) =>
          item.category === category &&
          `${item.name} ${item.description}`
            .toLowerCase()
            .includes(query.value.trim().toLowerCase()),
      ),
    }))
    .filter((group) => group.items.length),
)

function dragStart(event: DragEvent, type: string) {
  event.dataTransfer?.setData('application/stackarium-component', type)
  if (event.dataTransfer) event.dataTransfer.effectAllowed = 'copy'
}
</script>

<template>
  <aside class="palette panel">
    <div class="panel-heading">
      <strong>组件库</strong><span class="panel-count">{{ definitions.length }}</span>
    </div>
    <div class="palette-search">
      <input v-model="query" aria-label="搜索组件" placeholder="搜索组件…" />
    </div>
    <div class="panel-scroll">
      <template v-for="group in groups" :key="group.category">
        <div class="group-label">{{ group.category }}</div>
        <button
          v-for="item in group.items"
          :key="item.type"
          class="palette-item"
          type="button"
          draggable="true"
          :title="`${item.description} · 点击或拖入画布`"
          @click="emit('add', item.type)"
          @dragstart="dragStart($event, item.type)"
        >
          <span class="component-symbol" aria-hidden="true">{{ item.name.slice(0, 1) }}</span>
          <span class="palette-name">{{ item.name }}</span>
          <span class="palette-meta">{{ item.runtimeAvailable ? '可运行' : '仅拓扑' }}</span>
        </button>
      </template>
      <div v-if="!groups.length" class="palette-empty">
        没有匹配的组件。<button type="button" @click="query = ''">清除搜索</button>
      </div>
    </div>
    <div class="palette-footer">拖入画布，或点击快速添加</div>
  </aside>
</template>
