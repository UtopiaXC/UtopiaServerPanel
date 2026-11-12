<template>
  <div class="auth-viewport">
    <n-card class="auth-card" :bordered="false">
      <n-result
        status="403"
        :title="$t('forbidden.title')"
        :description="$t('forbidden.desc')"
      >
        <template #footer>
          <n-button type="primary" @click="router.push('/')">
            {{ $t('forbidden.backHome') }}
          </n-button>
        </template>
      </n-result>
    </n-card>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

