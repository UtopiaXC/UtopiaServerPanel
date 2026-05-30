<template>
  <div class="settings-page">
    <h2>{{ $t('admin.systemSettings') }}</h2>

    <div class="settings-card">
      <div class="setting-row">
        <label for="siteName">{{ $t('admin.siteName') }}</label>
        <div class="setting-control">
          <input id="siteName" type="text" v-model="siteName" :placeholder="$t('admin.siteNamePlaceholder')" class="setting-input" />
          <button class="save-btn" @click="saveSiteName" :disabled="saving">{{ $t('common.save') }}</button>
        </div>
      </div>
      <div v-if="saveMsg" class="save-msg" :class="{ error: saveError }">{{ saveMsg }}</div>
    </div>

    <div class="settings-card placeholder-card">
      <p class="placeholder-text">{{ $t('admin.moreSettingsComingSoon') }}</p>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { adminAPI } from '../../api/admin';

import { useSiteStore } from '../../stores/site';

const siteName = ref('');
const saving = ref(false);
const saveMsg = ref('');
const saveError = ref(false);
const siteStore = useSiteStore();

onMounted(async () => {
  try {
    const { data } = await adminAPI.getSiteName();
    siteName.value = data.data?.name || '';
  } catch { /* ignore */ }
});

const saveSiteName = async () => {
  saving.value = true;
  saveMsg.value = '';
  try {
    const { data } = await adminAPI.setSiteName(siteName.value);
    siteName.value = data.data?.name || siteName.value;
    saveMsg.value = '✓';
    saveError.value = false;
    // Update global site name and document title
    siteStore.setSiteName(siteName.value);
  } catch (e) {
    saveMsg.value = e.response?.data?.message || 'Error';
    saveError.value = true;
  } finally {
    saving.value = false;
    setTimeout(() => { saveMsg.value = ''; }, 2000);
  }
};
</script>

<style scoped>
.settings-page h2 { margin: 0 0 1.5rem 0; color: var(--text-strong); }
.settings-card {
  background: var(--card-bg);
  border: 1px solid var(--border-color);
  border-radius: 10px;
  padding: 1.5rem;
  margin-bottom: 1rem;
}
.setting-row {
  display: flex;
  align-items: center;
  gap: 1rem;
}
.setting-row label {
  font-weight: 500;
  color: var(--text-secondary);
  min-width: 100px;
  flex-shrink: 0;
}
.setting-control {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex: 1;
}
.setting-input {
  flex: 1;
  padding: 8px 12px;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  background: var(--bg-color);
  color: var(--input-text);
  font-size: 0.95rem;
  outline: none;
  transition: border-color 0.2s;
}
.setting-input:focus { border-color: var(--primary-color); }
.save-btn {
  padding: 8px 20px;
  background: var(--primary-color);
  color: #fff;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-weight: 500;
  transition: opacity 0.2s;
}
.save-btn:hover { opacity: 0.9; }
.save-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.save-msg { margin-top: 0.5rem; font-size: 0.85rem; color: #22c55e; }
.save-msg.error { color: #ef4444; }
.placeholder-card { text-align: center; }
.placeholder-text { color: var(--text-secondary); font-style: italic; margin: 0; }
</style>
