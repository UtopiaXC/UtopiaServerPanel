<template>
  <div class="user-home">
    <h2>{{ $t('user.home.title') }}</h2>

    <!-- Panel User Info -->
    <div class="card-grid">
      <div class="info-card">
        <div class="card-header">
          <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" fill="currentColor" viewBox="0 0 16 16"><path d="M8 8a3 3 0 1 0 0-6 3 3 0 0 0 0 6zm2-3a2 2 0 1 1-4 0 2 2 0 0 1 4 0zm4 8c0 1-1 1-1 1H3s-1 0-1-1 1-4 6-4 6 3 6 4zm-1-.004c-.001-.246-.154-.986-.832-1.664C11.516 10.68 10.289 10 8 10c-2.29 0-3.516.68-4.168 1.332-.678.678-.83 1.418-.832 1.664h10z"/></svg>
          <h3>{{ $t('user.home.accountInfo') }}</h3>
        </div>
        <div class="card-body">
          <div class="info-row">
            <span class="label">{{ $t('user.home.username') }}</span>
            <span class="value">{{ auth.user?.username }}</span>
          </div>
          <div class="info-row">
            <span class="label">{{ $t('user.home.role') }}</span>
            <span class="value badge">{{ auth.user?.roleName }}</span>
          </div>
          <div class="info-row">
            <span class="label">{{ $t('user.home.bindingStatus') }}</span>
            <span class="value" :class="auth.user?.bindingStatus === 'bound' ? 'status-ok' : 'status-warn'">
              {{ $t('user.home.' + (auth.user?.bindingStatus || 'unbound')) }}
            </span>
          </div>
          <div class="info-row">
            <span class="label">{{ $t('user.home.createdAt') }}</span>
            <span class="value">{{ formatDate(auth.user?.createdAt) }}</span>
          </div>
        </div>
      </div>

      <div class="info-card">
        <div class="card-header">
          <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" fill="currentColor" viewBox="0 0 16 16"><path d="M5 10.5a.5.5 0 0 1 .5-.5h2a.5.5 0 0 1 0 1h-2a.5.5 0 0 1-.5-.5zm0-2a.5.5 0 0 1 .5-.5h5a.5.5 0 0 1 0 1h-5a.5.5 0 0 1-.5-.5zm0-2a.5.5 0 0 1 .5-.5h5a.5.5 0 0 1 0 1h-5a.5.5 0 0 1-.5-.5zm0-2a.5.5 0 0 1 .5-.5h5a.5.5 0 0 1 0 1h-5a.5.5 0 0 1-.5-.5z"/><path d="M3 0h10a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2v-1h1v1a1 1 0 0 0 1 1h10a1 1 0 0 0 1-1V2a1 1 0 0 0-1-1H3a1 1 0 0 0-1 1v1H1V2a2 2 0 0 1 2-2z"/></svg>
          <h3>{{ $t('user.home.permissions') }}</h3>
        </div>
        <div class="card-body">
          <div class="info-row" v-for="key in permKeys" :key="key">
            <span class="label">{{ $t('permissions.' + key) }}</span>
            <span class="value badge" :class="'perm-' + levelLabel(auth.permissions[key] || 0)">
              {{ $t('permissions.levels.' + levelLabel(auth.permissions[key] || 0)) }}
            </span>
          </div>
        </div>
      </div>
    </div>

    <!-- Player Data (if bound) -->
    <div v-if="auth.user?.bindingStatus === 'bound'" class="section-title">
      <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" fill="currentColor" viewBox="0 0 16 16"><path d="M6.5 1A1.5 1.5 0 0 0 5 2.5V3H1.5A1.5 1.5 0 0 0 0 4.5v8A1.5 1.5 0 0 0 1.5 14h13a1.5 1.5 0 0 0 1.5-1.5v-8A1.5 1.5 0 0 0 14.5 3H11v-.5A1.5 1.5 0 0 0 9.5 1h-3zm0 1h3a.5.5 0 0 1 .5.5V3H6v-.5a.5.5 0 0 1 .5-.5zm1.886 6.914L15 7.151V12.5a.5.5 0 0 1-.5.5h-13a.5.5 0 0 1-.5-.5V7.15l6.614 1.764a1.5 1.5 0 0 0 .772 0zM1.5 4h13a.5.5 0 0 1 .5.5v1.616l-6.614 1.764a.5.5 0 0 1-.772 0L1 6.116V4.5a.5.5 0 0 1 .5-.5z"/></svg>
      <h3>{{ $t('user.home.playerData') }}</h3>
    </div>

    <div v-if="loading" class="loading-state">
      <div class="spinner"></div>
    </div>

    <div v-else-if="auth.user?.bindingStatus === 'bound' && playerData" class="card-grid">
      <div class="info-card player-card">
        <div class="card-header">
          <h3>{{ playerData.name || auth.user?.playerName || '--' }}</h3>
          <span class="status-badge" :class="playerData.online ? 'online' : 'offline'">
            {{ playerData.online ? $t('user.home.online') : $t('user.home.offline') }}
          </span>
        </div>
        <div class="card-body">
          <div class="info-row" v-if="playerData.dimension || playerData.lastDimension">
            <span class="label">{{ $t('user.home.dimension') }}</span>
            <span class="value mono">{{ playerData.dimension || playerData.lastDimension || '--' }}</span>
          </div>
          <div class="info-row" v-if="playerData.position || playerData.lastPosition">
            <span class="label">{{ $t('user.home.position') }}</span>
            <span class="value mono">{{ formatPos(playerData.position || playerData.lastPosition) }}</span>
          </div>
          <div class="info-row" v-if="playerData.gamemode || playerData.lastGamemode">
            <span class="label">{{ $t('user.home.gamemode') }}</span>
            <span class="value badge">{{ playerData.gamemode || playerData.lastGamemode || '--' }}</span>
          </div>
        </div>
      </div>

      <div class="info-card player-card">
        <div class="card-header"><h3>{{ $t('user.home.stats') }}</h3></div>
        <div class="card-body">
          <div class="info-row">
            <span class="label">{{ $t('user.home.health') }}</span>
            <span class="value">
              <span v-if="playerData.online" class="health-bar">
                <span class="health-fill" :style="{ width: ((playerData.health || 0) / (playerData.maxHealth || 20) * 100) + '%' }"></span>
                <span class="health-text">{{ Math.round(playerData.health || 0) }} / {{ playerData.maxHealth || 20 }}</span>
              </span>
              <span v-else-if="playerData.lastHealth != null">{{ Math.round(playerData.lastHealth) }}</span>
              <span v-else>--</span>
            </span>
          </div>
          <div class="info-row">
            <span class="label">{{ $t('user.home.deathCount') }}</span>
            <span class="value">{{ playerData.deathCount != null ? playerData.deathCount : '--' }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- Not bound -->
    <div v-else-if="auth.user?.bindingStatus !== 'bound'" class="unbound-card">
      <p>{{ $t('user.home.notBound') }}</p>
      <router-link to="/user/bind" class="btn-primary">{{ $t('user.home.bindPlayer') }}</router-link>
    </div>

    <!-- Login History -->
    <div v-if="loginHistory.length > 0" class="history-section">
      <div class="section-title">
        <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" fill="currentColor" viewBox="0 0 16 16"><path d="M8.515 1.019A7 7 0 0 0 8 1V0a8 8 0 0 1 .589.022l-.074.997zm2.004.45a7.003 7.003 0 0 0-.985-.299l.219-.976c.383.086.76.2 1.126.342l-.36.933zm1.37.71a7.01 7.01 0 0 0-.439-.27l.493-.87a8.025 8.025 0 0 1 .979.654l-.615.789a6.996 6.996 0 0 0-.418-.302zm1.834 1.79a6.99 6.99 0 0 0-.653-.796l.724-.69c.27.285.52.59.747.91l-.818.576zm.744 1.352a7.08 7.08 0 0 0-.214-.468l.893-.45a7.976 7.976 0 0 1 .45 1.088l-.95.313a7.023 7.023 0 0 0-.179-.483zm.53 2.507a6.991 6.991 0 0 0-.1-1.025l.985-.17c.067.386.106.778.116 1.17l-1 .025zm-.131 1.538c.033-.17.06-.339.081-.51l.993.123a7.957 7.957 0 0 1-.23 1.155l-.964-.267c.046-.165.086-.332.12-.501zm-.952 2.379c.184-.29.346-.594.486-.908l.914.405c-.16.36-.345.706-.555 1.038l-.845-.535zm-.964 1.205c.122-.122.239-.248.35-.378l.758.653a8.073 8.073 0 0 1-.401.432l-.707-.707z"/><path d="M8 1a7 7 0 1 0 4.95 11.95l.707.707A8.001 8.001 0 1 1 8 0v1z"/><path d="M7.5 3a.5.5 0 0 1 .5.5v5.21l3.248 1.856a.5.5 0 0 1-.496.868l-3.5-2A.5.5 0 0 1 7 9V3.5a.5.5 0 0 1 .5-.5z"/></svg>
        <h3>{{ $t('user.home.loginHistory') }}</h3>
      </div>
      <div class="history-table">
        <div class="history-row header">
          <span>{{ $t('user.home.loginTime') }}</span>
          <span>{{ $t('user.home.ipAddress') }}</span>
        </div>
        <div class="history-row" v-for="(entry, i) in loginHistory" :key="i">
          <span>{{ formatDate(entry.loginTime) }}</span>
          <span class="mono">{{ entry.ipAddress || '--' }}</span>
        </div>
      </div>
    </div>

    <!-- Actions -->
    <div class="actions">
      <router-link to="/user/profile" class="btn-primary">{{ $t('user.home.editProfile') }}</router-link>
      <button class="btn-logout" @click="handleLogout">{{ $t('auth.logout') }}</button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { useAuthStore } from '../../stores/auth';
import { authAPI } from '../../api/auth';

const router = useRouter();
const auth = useAuthStore();
const playerData = ref(null);
const loginHistory = ref([]);
const loading = ref(false);

const handleLogout = async () => {
  await auth.logout();
  router.push('/login');
};

const permKeys = ['admin', 'dashboard', 'terminal', 'logs'];

const levelLabel = (level) => {
  if (level >= 2) return 'full';
  if (level >= 1) return 'readonly';
  return 'deny';
};

const formatDate = (ts) => {
  if (!ts) return '--';
  const d = new Date(ts * 1000);
  return d.toLocaleString();
};

const formatPos = (pos) => {
  if (!pos) return '--';
  return `X: ${pos.x}, Y: ${pos.y}, Z: ${pos.z}`;
};

onMounted(async () => {
  // Fetch player data if bound
  if (auth.user?.bindingStatus === 'bound') {
    loading.value = true;
    try {
      const { data } = await authAPI.getPlayerData();
      if (data.data && data.data.bound !== false) {
        playerData.value = data.data;
      }
    } catch (e) {
      console.error('Failed to load player data', e);
    }
    loading.value = false;
  }

  // Fetch login history
  try {
    const { data } = await authAPI.me();
    // Login history would be fetched separately; for now we use the user endpoint
  } catch { /* ignore */ }
});
</script>

<style scoped>
.user-home { max-width: 900px; }
.user-home h2 { margin: 0 0 24px; color: var(--text-strong); font-size: 1.4rem; }

.card-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(340px, 1fr)); gap: 16px; margin-bottom: 24px; }

.info-card { background: var(--card-bg); border: 1px solid var(--border-color); border-radius: 12px; overflow: hidden; }
.card-header { display: flex; align-items: center; gap: 10px; padding: 14px 18px; background: var(--bg-color); border-bottom: 1px solid var(--border-color); }
.card-header h3 { margin: 0; font-size: 1rem; color: var(--text-strong); }
.card-header svg { color: var(--primary-color); flex-shrink: 0; }
.card-body { padding: 14px 18px; }

.info-row { display: flex; justify-content: space-between; align-items: center; padding: 8px 0; border-bottom: 1px solid var(--border-color); }
.info-row:last-child { border-bottom: none; }
.label { color: var(--text-secondary); font-size: 0.9rem; }
.value { color: var(--text-strong); font-size: 0.9rem; font-weight: 500; }
.value.mono, .mono { font-family: 'SF Mono', 'Cascadia Code', monospace; font-size: 0.85rem; }

.badge { padding: 2px 8px; border-radius: 4px; font-size: 0.8rem; background: var(--tab-bg); }
.status-ok { color: #16a34a; }
.status-warn { color: #d97706; }

.perm-full { background: #dcfce7; color: #166534; }
.perm-readonly { background: #fef9c3; color: #854d0e; }
.perm-deny { background: #fef2f2; color: #991b1b; }
html.dark .perm-full { background: #14532d; color: #86efac; }
html.dark .perm-readonly { background: #422006; color: #fde047; }
html.dark .perm-deny { background: #450a0a; color: #fca5a5; }

.status-badge { padding: 3px 10px; border-radius: 20px; font-size: 0.75rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.5px; }
.status-badge.online { background: #dcfce7; color: #166534; }
.status-badge.offline { background: #f3f4f6; color: #6b7280; }
html.dark .status-badge.online { background: #14532d; color: #86efac; }
html.dark .status-badge.offline { background: #374151; color: #9ca3af; }

.health-bar { display: inline-flex; align-items: center; position: relative; width: 120px; height: 18px; background: var(--border-color); border-radius: 9px; overflow: hidden; }
.health-fill { position: absolute; left: 0; top: 0; height: 100%; background: linear-gradient(90deg, #ef4444, #22c55e); border-radius: 9px; transition: width 0.3s; }
.health-text { position: relative; z-index: 1; width: 100%; text-align: center; font-size: 0.7rem; font-weight: 600; color: var(--text-strong); }

.section-title { display: flex; align-items: center; gap: 10px; margin-bottom: 16px; }
.section-title svg { color: var(--primary-color); }
.section-title h3 { margin: 0; font-size: 1.1rem; color: var(--text-strong); }

.unbound-card { background: var(--card-bg); border: 1px solid var(--border-color); border-radius: 12px; padding: 32px; text-align: center; margin-bottom: 24px; }
.unbound-card p { color: var(--text-secondary); margin: 0 0 16px; }

.btn-primary { display: inline-block; padding: 10px 24px; background: var(--primary-color); color: #fff; border: none; border-radius: 8px; font-size: 0.95rem; font-weight: 500; text-decoration: none; cursor: pointer; transition: opacity 0.2s; }
.btn-primary:hover { opacity: 0.9; }

.btn-logout { display: inline-block; padding: 10px 24px; background: transparent; color: #dc2626; border: 1px solid #fecaca; border-radius: 8px; font-size: 0.95rem; font-weight: 500; cursor: pointer; transition: background 0.2s; }
.btn-logout:hover { background: #fef2f2; }
html.dark .btn-logout:hover { background: #450a0a; border-color: #fca5a5; }

.history-section { margin-bottom: 24px; }
.history-table { background: var(--card-bg); border: 1px solid var(--border-color); border-radius: 12px; overflow: hidden; }
.history-row { display: grid; grid-template-columns: 1fr 1fr; padding: 10px 18px; border-bottom: 1px solid var(--border-color); }
.history-row:last-child { border-bottom: none; }
.history-row.header { background: var(--bg-color); font-weight: 600; font-size: 0.85rem; color: var(--text-secondary); }
.history-row span { font-size: 0.9rem; color: var(--text-primary); }

.actions { display: flex; gap: 12px; margin-top: 8px; }

.loading-state { display: flex; justify-content: center; padding: 40px; }
.spinner { width: 32px; height: 32px; border: 3px solid var(--border-color); border-top-color: var(--primary-color); border-radius: 50%; animation: spin 0.8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

.player-card .card-header { justify-content: space-between; }
</style>
