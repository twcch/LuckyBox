<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { createWish, fetchMyWishes, fetchPublicWishes } from '@/services/wishApi'
import { useSessionStore } from '@/stores/session'

const session = useSessionStore()
const wishes = ref([])
const myWishes = ref([])
const loading = ref(true)
const errorMessage = ref('')

const form = reactive({ content: '' })
const submitting = ref(false)
const formMessage = ref('')
const formError = ref('')

const MAX_LENGTH = 200
const remaining = computed(() => MAX_LENGTH - form.content.length)
const canSubmit = computed(
  () => !submitting.value && form.content.trim().length >= 4 && form.content.length <= MAX_LENGTH,
)

onMounted(async () => {
  await session.load()
  await loadPublicWishes()
  if (session.authenticated) {
    await loadMyWishes()
  }
})

async function loadPublicWishes() {
  loading.value = true
  errorMessage.value = ''
  try {
    wishes.value = await fetchPublicWishes()
  } catch {
    errorMessage.value = '目前無法載入許願牆。'
  } finally {
    loading.value = false
  }
}

async function loadMyWishes() {
  try {
    myWishes.value = await fetchMyWishes()
  } catch {
    // 個人願望載入失敗時靜默處理，不影響公開牆顯示。
  }
}

async function submitWish() {
  if (!canSubmit.value) {
    return
  }
  submitting.value = true
  formMessage.value = ''
  formError.value = ''
  try {
    const created = await createWish({ content: form.content.trim() })
    form.content = ''
    formMessage.value =
      created.status === 'APPROVED'
        ? '願望已送出並上牆，感謝你的許願！'
        : '願望已送出，審核通過後就會出現在許願牆。'
    await loadMyWishes()
    if (created.status === 'APPROVED') {
      await loadPublicWishes()
    }
  } catch (error) {
    formError.value = error.response?.data?.message || '送出失敗，請稍後再試。'
  } finally {
    submitting.value = false
  }
}

function statusLabel(status) {
  switch (status) {
    case 'APPROVED':
      return '已上牆'
    case 'PENDING':
      return '審核中'
    case 'REJECTED':
      return '未採用'
    case 'HIDDEN':
      return '已隱藏'
    default:
      return status
  }
}

function statusClass(status) {
  switch (status) {
    case 'APPROVED':
      return 'is-approved'
    case 'PENDING':
      return 'is-pending'
    default:
      return 'is-muted'
  }
}

function formatDate(value) {
  if (!value) {
    return ''
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return ''
  }
  return new Intl.DateTimeFormat('zh-TW', {
    month: '2-digit',
    day: '2-digit',
  }).format(date)
}
</script>

<template>
  <main class="wishes-page">
    <section class="container content-section">
      <div class="page-title">
        <span class="eyebrow">Wish Wall</span>
        <h1>許願牆</h1>
        <p>告訴我們你想抽到哪些 IP 與系列，熱門願望將成為我們選品與上架的參考。</p>
      </div>

      <div class="wishes-layout">
        <section class="status-panel wishes-board">
          <div class="section-heading">
            <div>
              <span class="eyebrow">Community</span>
              <h2>大家的願望</h2>
            </div>
            <span class="wishes-count">{{ wishes.length }} 則願望</span>
          </div>

          <div v-if="errorMessage" class="state-panel" role="alert">
            <i class="bi bi-exclamation-triangle" aria-hidden="true"></i>
            <span>{{ errorMessage }}</span>
          </div>

          <div v-if="loading" class="wishes-list">
            <article v-for="index in 4" :key="index" class="wish-card">
              <div class="skeleton-line w-75"></div>
              <div class="skeleton-line w-50"></div>
            </article>
          </div>

          <div v-else-if="wishes.length === 0" class="empty-state">
            <i class="bi bi-stars" aria-hidden="true"></i>
            <strong>還沒有公開的願望</strong>
            <span>成為第一個許願的人，分享你想抽到的系列吧！</span>
          </div>

          <ul v-else class="wishes-list">
            <li v-for="wish in wishes" :key="wish.id" class="wish-card">
              <p class="wish-card__content">{{ wish.content }}</p>
              <div class="wish-card__meta">
                <span
                  ><i class="bi bi-person-circle" aria-hidden="true"></i>
                  {{ wish.authorName }}</span
                >
                <span v-if="formatDate(wish.createdAt)">{{ formatDate(wish.createdAt) }}</span>
              </div>
            </li>
          </ul>
        </section>

        <aside class="wishes-aside">
          <section v-if="session.authenticated" class="status-panel">
            <span class="eyebrow">Make a Wish</span>
            <h2>我要許願</h2>
            <form class="wish-form" @submit.prevent="submitWish">
              <label class="form-label" for="wishContent">願望內容</label>
              <textarea
                id="wishContent"
                v-model="form.content"
                class="form-control"
                rows="4"
                :maxlength="MAX_LENGTH"
                placeholder="例如：希望能上架更多咒術迴戰、間諜家家酒限定週邊抽賞"
              ></textarea>
              <div class="wish-form__foot">
                <span class="wish-form__count" :class="{ 'is-warn': remaining < 0 }">
                  還可輸入 {{ remaining }} 字
                </span>
                <button class="btn btn-danger" type="submit" :disabled="!canSubmit">
                  <i class="bi bi-send me-1" aria-hidden="true"></i>
                  {{ submitting ? '送出中…' : '送出願望' }}
                </button>
              </div>
              <p v-if="formMessage" class="wish-form__ok" role="status">{{ formMessage }}</p>
              <p v-if="formError" class="wish-form__err" role="alert">{{ formError }}</p>
            </form>
          </section>

          <section v-else class="status-panel wishes-login-cta">
            <span class="eyebrow">Make a Wish</span>
            <h2>登入後即可許願</h2>
            <p>登入會員帳號，就能投稿你想抽到的系列，並追蹤審核狀態。</p>
            <RouterLink class="btn btn-danger" to="/login">
              <i class="bi bi-box-arrow-in-right me-1" aria-hidden="true"></i>
              登入 / 註冊
            </RouterLink>
          </section>

          <section v-if="session.authenticated && myWishes.length > 0" class="status-panel">
            <span class="eyebrow">My Wishes</span>
            <h2>我的願望</h2>
            <ul class="my-wishes-list">
              <li v-for="wish in myWishes" :key="wish.id" class="my-wish-card">
                <p>{{ wish.content }}</p>
                <span class="wish-status" :class="statusClass(wish.status)">
                  {{ statusLabel(wish.status) }}
                </span>
              </li>
            </ul>
          </section>
        </aside>
      </div>
    </section>
  </main>
</template>

<style scoped>
.wishes-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(0, 1fr);
  gap: 1.5rem;
  align-items: start;
}

.wishes-count {
  font-size: 0.85rem;
  color: var(--bs-secondary-color, #6c757d);
}

.wishes-list {
  display: grid;
  gap: 0.75rem;
  margin: 0;
  padding: 0;
  list-style: none;
}

.wish-card {
  padding: 1rem 1.1rem;
  border: 1px solid var(--bs-border-color, #e3e3e8);
  border-radius: 0.9rem;
  background: var(--bs-body-bg, #fff);
  transition:
    transform 0.18s ease,
    box-shadow 0.18s ease;
}

.wish-card:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 18px rgba(15, 23, 42, 0.07);
}

.wish-card__content {
  margin: 0 0 0.6rem;
  font-size: 1rem;
  line-height: 1.5;
  color: var(--bs-emphasis-color, #1f2933);
  word-break: break-word;
}

.wish-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 0.25rem 1rem;
  font-size: 0.8rem;
  color: var(--bs-secondary-color, #6c757d);
}

.wishes-aside {
  display: grid;
  gap: 1.25rem;
}

.wish-form {
  display: grid;
  gap: 0.6rem;
}

.wish-form__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  flex-wrap: wrap;
}

.wish-form__count {
  font-size: 0.78rem;
  color: var(--bs-secondary-color, #6c757d);
}

.wish-form__count.is-warn {
  color: #b91c1c;
}

.wish-form__ok {
  margin: 0;
  font-size: 0.85rem;
  color: #0f766e;
}

.wish-form__err {
  margin: 0;
  font-size: 0.85rem;
  color: #b91c1c;
}

.wishes-login-cta p {
  color: var(--bs-secondary-color, #6c757d);
}

.my-wishes-list {
  display: grid;
  gap: 0.5rem;
  margin: 0;
  padding: 0;
  list-style: none;
}

.my-wish-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  padding: 0.65rem 0.85rem;
  border: 1px solid var(--bs-border-color, #e3e3e8);
  border-radius: 0.7rem;
}

.my-wish-card p {
  margin: 0;
  font-size: 0.9rem;
  word-break: break-word;
}

.wish-status {
  flex-shrink: 0;
  font-size: 0.72rem;
  font-weight: 600;
  padding: 0.15rem 0.55rem;
  border-radius: 999px;
}

.wish-status.is-approved {
  background: rgba(13, 148, 136, 0.14);
  color: #0f766e;
}

.wish-status.is-pending {
  background: rgba(217, 119, 6, 0.15);
  color: #b45309;
}

.wish-status.is-muted {
  background: rgba(100, 116, 139, 0.15);
  color: #475569;
}

@media (max-width: 991.98px) {
  .wishes-layout {
    grid-template-columns: 1fr;
  }
}
</style>
