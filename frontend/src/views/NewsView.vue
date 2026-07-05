<script setup>
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { fetchNews } from '@/services/newsApi'

const news = ref([])
const loading = ref(true)
const errorMessage = ref('')

onMounted(async () => {
  await loadNews()
})

async function loadNews() {
  loading.value = true
  errorMessage.value = ''
  try {
    news.value = await fetchNews()
  } catch {
    errorMessage.value = '目前無法載入公告。'
  } finally {
    loading.value = false
  }
}

function formatTime(value) {
  if (!value) {
    return '尚未指定'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return '尚未指定'
  }
  return new Intl.DateTimeFormat('zh-TW', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}
</script>

<template>
  <main class="news-page">
    <section class="container content-section">
      <div class="page-title">
        <span class="eyebrow">News</span>
        <h1>公告與活動</h1>
        <p>平台公告、活動調整、出貨提醒與服務異動都會集中在這裡。</p>
      </div>

      <div v-if="errorMessage" class="state-panel" role="alert">
        <i class="bi bi-exclamation-triangle" aria-hidden="true"></i>
        <span>{{ errorMessage }}</span>
      </div>

      <div v-if="loading" class="news-list">
        <article v-for="index in 3" :key="index" class="news-card">
          <div class="skeleton-line w-50"></div>
          <div class="skeleton-line"></div>
          <div class="skeleton-line w-75"></div>
        </article>
      </div>

      <div v-else-if="news.length === 0" class="empty-state">
        <i class="bi bi-megaphone" aria-hidden="true"></i>
        <strong>目前沒有公告</strong>
      </div>

      <div v-else class="news-list">
        <RouterLink
          v-for="item in news"
          :key="item.id"
          class="news-card"
          :to="`/news/${item.slug}`"
        >
          <div>
            <span class="eyebrow">{{ formatTime(item.publishedAt) }}</span>
            <h2>{{ item.title }}</h2>
            <p>{{ item.excerpt }}</p>
          </div>
        </RouterLink>
      </div>
    </section>
  </main>
</template>
