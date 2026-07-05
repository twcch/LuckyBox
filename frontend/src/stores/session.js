import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import {
  fetchAccountProfile,
  fetchCurrentUser,
  loginAccount,
  logoutAccount,
  registerAccount,
  updateProfileAccount,
} from '@/services/authApi'

export const useSessionStore = defineStore('session', () => {
  const user = ref(null)
  const initialized = ref(false)
  const loading = ref(false)
  let loadPromise = null

  const authenticated = computed(() => Boolean(user.value))

  async function load() {
    if (loadPromise) {
      return loadPromise
    }
    loading.value = true
    loadPromise = fetchCurrentUser()
      .then((currentUser) => {
        user.value = currentUser
        return user.value
      })
      .catch(() => {
        user.value = null
        return user.value
      })
      .finally(() => {
        initialized.value = true
        loading.value = false
        loadPromise = null
      })
    return loadPromise
  }

  async function register(payload) {
    user.value = await registerAccount(payload)
    initialized.value = true
    return user.value
  }

  async function login(payload) {
    user.value = await loginAccount(payload)
    initialized.value = true
    return user.value
  }

  async function logout() {
    await logoutAccount()
    user.value = null
    initialized.value = true
  }

  async function loadProfile() {
    user.value = await fetchAccountProfile()
    initialized.value = true
    return user.value
  }

  async function updateProfile(payload) {
    user.value = await updateProfileAccount(payload)
    initialized.value = true
    return user.value
  }

  return {
    user,
    initialized,
    loading,
    authenticated,
    load,
    loadProfile,
    register,
    login,
    logout,
    updateProfile,
  }
})
