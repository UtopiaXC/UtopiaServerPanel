<template>
  <div class="logs-page">
    <div class="logs-header">
      <h2>{{ $t('logs.title') }}</h2>
    </div>

    <div v-if="monitorDisabled" class="empty-state-card">
      <p class="disabled-msg">{{ $t('logs.monitorDisabled') }}</p>
    </div>

    <div v-else class="charts-grid">
      <MonitorCard :title="$t('logs.cpuUsage')" type="cpu" />
      <MonitorCard :title="$t('logs.memoryUsage')" type="memory" />
      <MonitorCard :title="$t('logs.tpsChart')" type="tps" />
      <MonitorCard :title="$t('logs.diskUsage')" type="disk" />
      <MonitorCard :title="$t('logs.playerOnline')" type="player" />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { monitorAPI } from '../api/admin';
import MonitorCard from './MonitorCard.vue';

const monitorDisabled = ref(false);

onMounted(async () => {
  try {
    const configRes = await monitorAPI.getDisplayConfig();
    monitorDisabled.value = !configRes.data.data.enabled;
  } catch (e) {
    console.error("Failed to load monitor config", e);
  }
});
</script>

<style scoped>
.logs-page {
  animation: fadeIn 0.3s ease;
}

.logs-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1.5rem;
}

.logs-header h2 {
  font-size: 1.5rem;
  font-weight: 600;
  margin: 0;
}

.empty-state-card {
  background: var(--card-bg, #fff);
  border-radius: 10px;
  padding: 4rem 2rem;
  text-align: center;
  color: var(--text-secondary, #6b7280);
  box-shadow: 0 2px 4px rgba(0,0,0,0.05);
  border: 1px solid var(--border-color, #e5e7eb);
}

.charts-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 1.25rem;
}
</style>
