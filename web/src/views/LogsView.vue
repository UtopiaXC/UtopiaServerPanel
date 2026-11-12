<template>
  <div class="logs-grid">
    <MonitorCard :title="$t('logs.metricTps')" type="tps" />
    <MonitorCard :title="$t('logs.metricCpu')" type="cpu" />
    <MonitorCard :title="$t('logs.metricMemory')" type="memory" />
    <MonitorCard :title="$t('logs.metricDisk')" type="disk" />
    <div class="full-span">
      <MonitorCard :title="$t('logs.metricPlayers')" type="player" />
    </div>
    <div class="full-span">
      <ServerLifecycleCard />
    </div>
  </div>
</template>

<script setup lang="ts">
import MonitorCard from '../components/MonitorCard.vue'
import ServerLifecycleCard from '../components/ServerLifecycleCard.vue'
</script>

<style scoped>
.logs-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.full-span {
  grid-column: 1 / -1;
}

@media (max-width: 1024px) {
  .logs-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>

