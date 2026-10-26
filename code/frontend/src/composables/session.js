import { ref } from 'vue'

// Preserve Frontend A's public session contract: /auth/me 401 resolves to null.
export function createSessionStore(fetchUser, onChange = () => {}) {
  const session = ref(null)
  let sessionPromise
  let generation = 0

  function setSession(user) {
    generation += 1
    sessionPromise = undefined
    session.value = user
    onChange()
  }

  function clearSession() {
    generation += 1
    sessionPromise = undefined
    if (session.value !== null) {
      session.value = null
      onChange()
    }
  }

  function loadSession() {
    if (session.value) return Promise.resolve(session.value)
    if (sessionPromise) return sessionPromise
    const startedAt = generation
    const pending = Promise.resolve()
      .then(fetchUser)
      .then((user) => {
        // A request started before login/logout must not replace the newer session.
        if (generation !== startedAt) return session.value
        session.value = user
        return user
      })
      .catch((error) => {
        if (generation !== startedAt) return session.value
        if (error.status === 401) {
          session.value = null
          return null
        }
        throw error
      })
      .finally(() => {
        if (sessionPromise === pending) sessionPromise = undefined
      })
    sessionPromise = pending
    return pending
  }

  return { session, setSession, clearSession, loadSession }
}
