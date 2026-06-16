<template>
  <router-view />
</template>

<script setup>
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()

onMounted(() => {
  // 如果已登录且在当前根路径，跳转到仪表盘
  const token = localStorage.getItem('token')
  if (token && router.currentRoute.value.path === '/') {
    router.push('/dashboard')
  }
})
</script>
