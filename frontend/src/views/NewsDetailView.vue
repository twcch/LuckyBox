<script setup>
import { onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { fetchNewsDetail } from '@/services/newsApi'

const route = useRoute()
const news = ref(null)
const loading = ref(true)
const errorMessage = ref('')

onMounted(async () => {
  await loadNews()
})

async function loadNews() {
  loading.value = true
  errorMessage.value = ''
  try {
    news.value = await fetchNewsDetail(route.params.slug)
  } catch {
    errorMessage.value = '找不到公告，或公告尚未發布。'
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
      <RouterLink class="back-link" to="/news">
        <i class="bi bi-arrow-left" aria-hidden="true"></i>
        回公告列表
      </RouterLink>

      <div v-if="loading" class="news-detail">
        <div class="skeleton-line w-25"></div>
        <div class="skeleton-line"></div>
        <div class="skeleton-line w-75"></div>
        <div class="skeleton-block"></div>
      </div>

      <div v-else-if="errorMessage" class="state-panel" role="alert">
        <i class="bi bi-exclamation-triangle" aria-hidden="true"></i>
        <span>{{ errorMessage }}</span>
      </div>

      <article v-else class="news-detail">
        <span class="eyebrow">{{ formatTime(news.publishedAt) }}</span>
        <h1>{{ news.title }}</h1>
        <div class="news-detail__content">{{ news.content }}</div>
      </article>
    </section>
  </main>
</template>
