import { computed, inject, provide } from 'vue'
import { api } from '../api/index.js'
import { revision, session, useResource } from './state.js'

const currentCreditKey = Symbol('current-credit')
const empty = { data: { value: null }, lastKnown: { value: null }, loading: { value: false }, error: { value: null }, reload() {} }

export function createCurrentCreditResource(loadCredit = api.myCredit, currentSession = session, changeSource = revision) {
  const resource = useResource((signal) => {
    const userId = currentSession.value?.userId
    return userId ? loadCredit(signal, userId) : Promise.resolve(null)
  }, [() => currentSession.value?.userId, changeSource])
  const current = {
    ...resource,
    lastKnown: computed(() => resource.data.value?.userId === currentSession.value?.userId ? resource.data.value : null),
    // 会话切换、请求失败或重新加载时，不使用上一用户或上一轮的信用状态。
    data: computed(() => !resource.loading.value && !resource.error.value &&
      resource.data.value?.userId === currentSession.value?.userId ? resource.data.value : null),
  }
  return current
}

export function provideCurrentCredit() {
  const current = createCurrentCreditResource()
  provide(currentCreditKey, current)
  return current
}

export function useCurrentCredit() {
  return inject(currentCreditKey, empty)
}
