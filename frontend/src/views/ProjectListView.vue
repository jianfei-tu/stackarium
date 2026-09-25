<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api, errorMessage } from '../api'
import type { Project } from '../domain'

const router = useRouter()
const projects = ref<Project[]>([])
const name = ref('')
const description = ref('')
const busy = ref(false)
const error = ref('')

onMounted(async () => {
  busy.value = true
  try {
    projects.value = await api.projects()
  } catch (cause) {
    error.value = errorMessage(cause)
  } finally {
    busy.value = false
  }
})

async function createProject() {
  if (!name.value.trim() || busy.value) return
  busy.value = true
  error.value = ''
  try {
    const project = await api.createProject(name.value.trim(), description.value.trim())
    await router.push(`/projects/${project.id}`)
  } catch (cause) {
    error.value = errorMessage(cause)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <main class="project-page">
    <header class="project-header">
      <span class="brand-mark">▤</span>
      <strong>Stackarium <span>栈境</span></strong>
      <small>架构工作台</small>
    </header>
    <section class="project-content">
      <div class="section-heading">
        <div>
          <h1>项目</h1>
        </div>
        <span class="muted">管理架构草图与组件配置</span>
      </div>
      <p v-if="error" class="inline-error" role="alert">{{ error }}</p>
      <div class="project-grid">
        <section class="surface create-project">
          <h2>新建项目</h2>
          <p class="muted">从空白画布开始组织服务、数据和流量关系。</p>
          <form @submit.prevent="createProject">
            <label for="project-name">项目名称</label>
            <input
              id="project-name"
              v-model="name"
              maxlength="100"
              required
              placeholder="例如：订单服务架构"
            />
            <label for="project-description">描述 <span class="muted">可选</span></label>
            <textarea
              id="project-description"
              v-model="description"
              maxlength="500"
              rows="3"
              placeholder="简要记录这个项目的用途"
            />
            <button class="primary-button" type="submit" :disabled="busy || !name.trim()">
              创建并打开工作台
            </button>
          </form>
        </section>
        <section class="surface project-list">
          <div class="list-heading">
            <h2>已有项目</h2>
            <span class="muted">{{ projects.length }}</span>
          </div>
          <p v-if="busy && !projects.length" class="muted list-empty">正在读取项目…</p>
          <p v-else-if="!projects.length" class="muted list-empty">
            还没有项目。创建一个项目开始绘制架构。
          </p>
          <RouterLink
            v-for="project in projects"
            :key="project.id"
            class="project-row"
            :to="`/projects/${project.id}`"
          >
            <span class="project-row-mark">▤</span>
            <span class="project-row-main"
              ><strong>{{ project.name }}</strong
              ><small>{{ project.description || '未填写描述' }}</small></span
            >
            <span class="row-arrow">↗</span>
          </RouterLink>
        </section>
      </div>
      <footer class="project-footnote">Stackarium · 架构建模与运行实验</footer>
    </section>
  </main>
</template>
