import { computed, onScopeDispose, ref, watch } from 'vue'
import { api } from '../api/index.js'

export const session = ref(null)
export const revision = ref(0)
export const toasts = ref([])
let toastId = 0

export function notify(message, tone = 'success') {
  const id = ++toastId
  toasts.value.push({ id, message, tone })
  setTimeout(() => {
    toasts.value = toasts.value.filter((item) => item.id !== id)
  }, 6500)
}
export const changed = () => {
  revision.value += 1
}
export function setSession(user) {
  sessionPromise = undefined
  session.value = user
  changed()
}
export function clearSession() {
  sessionPromise = undefined
  if (session.value !== null) {
    session.value = null
    changed()
  }
}
let sessionPromise
export async function loadSession() {
  if (session.value) return session.value
  sessionPromise ??= api
    .me()
    .then((user) => {
      session.value = user
      return user
    })
    .catch((error) => {
      if (error.status === 401) {
        session.value = null
        return null
      }
      throw error
    })
    .finally(() => {
      sessionPromise = undefined
    })
  return sessionPromise
}

export function useResource(loader, sources = []) {
  const data = ref(null)
  const loading = ref(true)
  const error = ref(null)
  const refreshKey = ref(0)
  let controller
  const stop = watch(
    [...sources, refreshKey],
    async (_, __, cleanup) => {
      controller?.abort()
      controller = new AbortController()
      const requestController = controller
      cleanup(() => requestController.abort())
      loading.value = true
      error.value = null
      try {
        const result = await loader(requestController.signal)
        if (!requestController.signal.aborted) data.value = result
      } catch (failure) {
        if (!requestController.signal.aborted) error.value = failure
      } finally {
        if (!requestController.signal.aborted) loading.value = false
      }
    },
    { immediate: true },
  )
  onScopeDispose(() => {
    controller?.abort()
    stop()
  })
  return {
    data,
    loading,
    error,
    reload: () => {
      refreshKey.value += 1
    },
  }
}

export function usePaged(loader, filters = []) {
  const page = ref(0)
  const size = ref(10)
  watch(
    [...filters, size],
    () => {
      page.value = 0
    },
    { flush: 'sync' },
  )
  const resource = useResource(
    (signal) => loader({ page: page.value, size: size.value }, signal),
    [page, size, ...filters, revision],
  )
  return {
    ...resource,
    page,
    size,
    items: computed(() => resource.data.value?.items ?? []),
    total: computed(() => resource.data.value?.total ?? 0),
  }
}
