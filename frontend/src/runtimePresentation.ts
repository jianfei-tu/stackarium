import type { ContainerObservation, RuntimeStatus } from './domain'

export function observationPresentation(
  observation: ContainerObservation | undefined,
  status?: RuntimeStatus,
) {
  if (!observation || observation.state === 'missing')
    return {
      tone: 'idle',
      label:
        status === 'GENERATED' ? '已生成 · 未启动' : status === 'STOPPED' ? '已停止' : '未运行',
    }
  if (observation.state === 'exited' || observation.state === 'dead')
    return { tone: 'failed', label: '容器已退出' }
  if (observation.health === 'healthy') return { tone: 'healthy', label: '健康' }
  if (observation.health === 'unhealthy') return { tone: 'warning', label: '健康检查失败' }
  if (observation.state === 'running') return { tone: 'running', label: '容器运行中 · 健康待确认' }
  return { tone: 'starting', label: '启动中' }
}
