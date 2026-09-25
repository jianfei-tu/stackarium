import { createRouter, createWebHistory } from 'vue-router'
import ProjectListView from './views/ProjectListView.vue'
import WorkspaceView from './views/WorkspaceView.vue'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: ProjectListView },
    { path: '/projects/:projectId', component: WorkspaceView },
  ],
})
