<template>
  <div class="settings-page">
    <h2>{{ $t('admin.monitorSettings') }}</h2>

    <div class="settings-card" v-if="loaded">
      <!-- Enable/Disable -->
      <div class="setting-row">
        <label>{{ $t('admin.monitorEnabled') }}</label>
        <div class="setting-control">
          <label class="toggle-switch">
            <input type="checkbox" v-model="config.enabled" @change="markDirty" />
            <span class="toggle-slider"></span>
          </label>
        </div>
      </div>

      <!-- Recording Interval -->
      <div class="setting-row">
        <label>{{ $t('admin.monitorInterval') }}</label>
        <div class="setting-control">
          <div class="custom-dropdown" @click="intervalDropdownOpen = !intervalDropdownOpen" v-click-outside="() => intervalDropdownOpen = false">
            <div class="custom-dropdown-btn">
              {{ intervalLabel }}
            </div>
            <ul class="custom-dropdown-menu" v-if="intervalDropdownOpen">
              <li @click="setInterval(60)" :class="{active: config.intervalSeconds === 60}">1 {{ $t('admin.minutes') }}</li>
              <li @click="setInterval(120)" :class="{active: config.intervalSeconds === 120}">2 {{ $t('admin.minutes') }}</li>
              <li @click="setInterval(300)" :class="{active: config.intervalSeconds === 300}">5 {{ $t('admin.minutes') }}</li>
              <li @click="setInterval(600)" :class="{active: config.intervalSeconds === 600}">10 {{ $t('admin.minutes') }}</li>
              <li @click="showCustomInterval = true; intervalDropdownOpen = false" :class="{active: isCustomInterval}">{{ $t('admin.custom') }}</li>
            </ul>
          </div>
          <div v-if="showCustomInterval" class="custom-input-row">
            <input type="number" v-model.number="customIntervalMin" min="1" class="setting-input small" /> {{ $t('admin.minutes') }}
            <button class="apply-btn" @click="applyCustomInterval">✓</button>
          </div>
        </div>
      </div>

      <!-- Retention -->
      <div class="setting-row">
        <label>{{ $t('admin.monitorRetention') }}</label>
        <div class="setting-control">
          <div class="custom-dropdown" @click="retentionDropdownOpen = !retentionDropdownOpen" v-click-outside="() => retentionDropdownOpen = false">
            <div class="custom-dropdown-btn">
              {{ retentionLabel }}
            </div>
            <ul class="custom-dropdown-menu" v-if="retentionDropdownOpen">
              <li @click="setRetention(7)" :class="{active: config.retentionDays === 7}">7 {{ $t('admin.days') }}</li>
              <li @click="setRetention(15)" :class="{active: config.retentionDays === 15}">15 {{ $t('admin.days') }}</li>
              <li @click="setRetention(30)" :class="{active: config.retentionDays === 30}">30 {{ $t('admin.days') }}</li>
              <li @click="setRetention(120)" :class="{active: config.retentionDays === 120}">120 {{ $t('admin.days') }}</li>
              <li @click="setRetention(365)" :class="{active: config.retentionDays === 365}">365 {{ $t('admin.days') }}</li>
              <li @click="showCustomRetention = true; retentionDropdownOpen = false" :class="{active: isCustomRetention}">{{ $t('admin.custom') }}</li>
            </ul>
          </div>
          <div v-if="showCustomRetention" class="custom-input-row">
            <input type="number" v-model.number="customRetentionDays" min="1" class="setting-input small" /> {{ $t('admin.days') }}
            <button class="apply-btn" @click="applyCustomRetention">✓</button>
          </div>
        </div>
      </div>

      <!-- Show Player Names -->
      <div class="setting-row">
        <label>{{ $t('admin.monitorShowPlayerNames') }}</label>
        <div class="setting-control">
          <label class="toggle-switch">
            <input type="checkbox" v-model="config.showPlayerNames" @change="markDirty" />
            <span class="toggle-slider"></span>
          </label>
        </div>
      </div>

      <div class="save-row">
        <button class="save-btn" @click="save" :disabled="saving || !dirty">{{ $t('common.save') }}</button>
        <span v-if="saveMsg" class="save-msg" :class="{ error: saveError }">{{ saveMsg }}</span>
      </div>
    </div>

    <div v-else class="settings-card loading-card">
      <p>{{ $t('common.loading') }}</p>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { adminAPI } from '../../api/admin';

const loaded = ref(false);
const config = ref({ enabled: true, intervalSeconds: 60, retentionDays: 30, showPlayerNames: true });
const dirty = ref(false);
const saving = ref(false);
const saveMsg = ref('');
const saveError = ref(false);

const intervalDropdownOpen = ref(false);
const retentionDropdownOpen = ref(false);
const showCustomInterval = ref(false);
const showCustomRetention = ref(false);
const customIntervalMin = ref(1);
const customRetentionDays = ref(30);

const vClickOutside = {
  mounted(el, binding) {
    el.clickOutsideEvent = (event) => {
      if (!(el === event.target || el.contains(event.target))) binding.value(event, el);
    };
    document.body.addEventListener('click', el.clickOutsideEvent);
  },
  unmounted(el) {
    document.body.removeEventListener('click', el.clickOutsideEvent);
  }
};

const presetIntervals = [60, 120, 300, 600];
const presetRetentions = [7, 15, 30, 120, 365];

const isCustomInterval = computed(() => !presetIntervals.includes(config.value.intervalSeconds));
const isCustomRetention = computed(() => !presetRetentions.includes(config.value.retentionDays));

const intervalLabel = computed(() => {
  const s = config.value.intervalSeconds;
  return (s / 60) + ' ' + 'min';
});

const retentionLabel = computed(() => {
  return config.value.retentionDays + ' days';
});

const markDirty = () => { dirty.value = true; };

const setInterval = (sec) => {
  config.value.intervalSeconds = sec;
  showCustomInterval.value = false;
  dirty.value = true;
};

const setRetention = (days) => {
  config.value.retentionDays = days;
  showCustomRetention.value = false;
  dirty.value = true;
};

const applyCustomInterval = () => {
  const sec = Math.max(1, customIntervalMin.value) * 60;
  config.value.intervalSeconds = sec;
  showCustomInterval.value = false;
  dirty.value = true;
};

const applyCustomRetention = () => {
  config.value.retentionDays = Math.max(1, customRetentionDays.value);
  showCustomRetention.value = false;
  dirty.value = true;
};

onMounted(async () => {
  try {
    const { data } = await adminAPI.getMonitorConfig();
    config.value = data.data;
    customIntervalMin.value = Math.round(config.value.intervalSeconds / 60);
    customRetentionDays.value = config.value.retentionDays;
  } catch { /* ignore */ }
  loaded.value = true;
});

const save = async () => {
  saving.value = true;
  saveMsg.value = '';
  try {
    const { data } = await adminAPI.setMonitorConfig(config.value);
    config.value = data.data;
    dirty.value = false;
    saveMsg.value = '✓';
    saveError.value = false;
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
  padding: 0.75rem 0;
  border-bottom: 1px solid var(--border-color);
}
.setting-row:last-of-type { border-bottom: none; }
.setting-row > label {
  font-weight: 500;
  color: var(--text-secondary);
  min-width: 140px;
  flex-shrink: 0;
}
.setting-control {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex: 1;
  flex-wrap: wrap;
}
.custom-input-row {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  color: var(--text-secondary);
  font-size: 0.9rem;
}
.setting-input {
  padding: 6px 10px;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  background: var(--bg-color);
  color: var(--input-text);
  font-size: 0.9rem;
  outline: none;
  transition: border-color 0.2s;
}
.setting-input.small { width: 80px; }
.setting-input:focus { border-color: var(--primary-color); }
.apply-btn {
  padding: 4px 10px;
  background: #22c55e;
  color: #fff;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-weight: 600;
}

/* Toggle Switch */
.toggle-switch { position: relative; display: inline-block; width: 44px; height: 24px; }
.toggle-switch input { opacity: 0; width: 0; height: 0; }
.toggle-slider {
  position: absolute; cursor: pointer; top: 0; left: 0; right: 0; bottom: 0;
  background-color: var(--border-color); transition: .3s; border-radius: 24px;
}
.toggle-slider:before {
  content: ""; position: absolute; height: 18px; width: 18px; left: 3px; bottom: 3px;
  background-color: white; transition: .3s; border-radius: 50%;
}
.toggle-switch input:checked + .toggle-slider { background-color: var(--primary-color); }
.toggle-switch input:checked + .toggle-slider:before { transform: translateX(20px); }

/* Dropdown */
.custom-dropdown { position: relative; display: inline-block; user-select: none; }
.custom-dropdown-btn {
  background: var(--bg-color); color: var(--input-text); border: 1px solid var(--border-color);
  border-radius: 6px; padding: 6px 30px 6px 12px; font-size: 0.9rem; cursor: pointer; white-space: nowrap;
  background-image: url("data:image/svg+xml;charset=US-ASCII,%3Csvg%20xmlns%3D%22http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%22%20width%3D%22292.4%22%20height%3D%22292.4%22%3E%3Cpath%20fill%3D%22%2364748b%22%20d%3D%22M287%2069.4a17.6%2017.6%200%200%200-13-5.4H18.4c-5%200-9.3%201.8-12.9%205.4A17.6%2017.6%200%200%200%200%2082.2c0%205%201.8%209.3%205.4%2012.9l128%20127.9c3.6%203.6%207.8%205.4%2012.8%205.4s9.2-1.8%2012.8-5.4L287%2095c3.5-3.5%205.4-7.8%205.4-12.8%200-5-1.9-9.2-5.5-12.8z%22%2F%3E%3C%2Fsvg%3E");
  background-repeat: no-repeat; background-position: right 8px center; background-size: 8px auto;
}
.custom-dropdown-btn:hover { border-color: var(--border-hover); }
.custom-dropdown-menu {
  position: absolute; top: 100%; left: 0; margin-top: 4px;
  background: var(--tooltip-bg); border: 1px solid var(--border-color);
  border-radius: 6px; list-style: none; padding: 4px 0; z-index: 200;
  min-width: 120px; box-shadow: 0 4px 12px var(--tooltip-shadow);
}
.custom-dropdown-menu li {
  padding: 6px 14px; font-size: 0.85rem; color: var(--text-strong);
  cursor: pointer; white-space: nowrap; transition: background 0.15s;
}
.custom-dropdown-menu li:hover { background: var(--bg-color); }
.custom-dropdown-menu li.active { color: var(--primary-color); font-weight: 600; }

.save-row { display: flex; align-items: center; gap: 0.75rem; margin-top: 1.5rem; }
.save-btn {
  padding: 8px 24px; background: var(--primary-color); color: #fff;
  border: none; border-radius: 6px; cursor: pointer; font-weight: 500; transition: opacity 0.2s;
}
.save-btn:hover { opacity: 0.9; }
.save-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.save-msg { font-size: 0.85rem; color: #22c55e; }
.save-msg.error { color: #ef4444; }
.loading-card { text-align: center; color: var(--text-secondary); }
</style>
