<template>
  <div class="logs-container">
    <h2>{{ $t('logs.title') }}</h2>
    <div class="logs-content">
      <div class="empty-state">
        <svg xmlns="http://www.w3.org/2000/svg" width="48" height="48" fill="currentColor" viewBox="0 0 16 16" style="opacity:0.5;display:block;margin:0 auto 1rem"><path d="M4 11H2v3h2v-3zm5-4H7v7h2V7zm5-5h-2v12h2V2zm-2-1a1 1 0 0 0-1 1v12a1 1 0 0 0 1 1h2a1 1 0 0 0 1-1V2a1 1 0 0 0-1-1h-2zM6 7a1 1 0 0 1 1-1h2a1 1 0 0 1 1 1v7a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V7zm-5 4a1 1 0 0 1 1-1h2a1 1 0 0 1 1 1v3a1 1 0 0 1-1 1H2a1 1 0 0 1-1-1v-3z"/></svg>
        <p>{{ $t('logs.placeholder') }}</p>
        <span class="sub-text">{{ $t('logs.comingSoon') }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
</script>

<style scoped>
.logs-container {
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0,0,0,0.05);
  overflow: hidden;
  height: 500px;
  display: flex;
  flex-direction: column;
}

.logs-container h2 {
  margin: 0;
  padding: 15px 20px;
  background: #f8fafc;
  color: #334155;
  font-size: 1.1rem;
  border-bottom: 1px solid #e2e8f0;
}

.logs-content {
  flex-grow: 1;
  display: flex;
  justify-content: center;
  align-items: center;
  background: #fdfdfd;
}

.empty-state {
  text-align: center;
  color: #94a3b8;
}

.empty-state .icon {
  font-size: 4rem;
  display: block;
  margin-bottom: 1rem;
  opacity: 0.5;
}

.empty-state p {
  font-size: 1.5rem;
  margin: 0 0 0.5rem 0;
  color: #64748b;
}

.empty-state .sub-text {
  font-size: 0.9rem;
}
</style>
