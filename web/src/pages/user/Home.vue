<template>
  <div class="user-home">
    <h2>{{ auth.user?.username }}</h2>

    <!-- Force banner -->
    <div v-if="auth.mustChangePassword" class="force-banner">{{ $t('auth.mustChangePwBanner') }}</div>
    <div v-else-if="!auth.isAdmin && auth.bindingStatus !== 'bound'" class="force-banner bind-banner">{{ $t('auth.mustBindBanner') }}</div>

    <!-- Player data -->
    <div class="section" v-if="auth.user?.playerName">
      <h3>{{ $t('admin.playerSection') }}: {{ auth.user.playerName }}</h3>
      <div v-if="!playerLoading">
        <div class="status-badge" :class="playerOnline?'online':'offline'">{{ playerOnline?'🟢 '+$t('admin.online'):'⚫ '+$t('admin.offline') }}</div>
        <div class="player-details" v-if="playerOnline&&playerData">
          <div class="ditem"><span class="lbl">{{ $t('admin.dimension') }}</span><span>{{ playerData.dimension }}</span></div>
          <div class="ditem"><span class="lbl">{{ $t('admin.gamemode') }}</span><span>{{ playerData.gamemode }}</span></div>
          <div class="ditem"><span class="lbl">{{ $t('admin.health') }}</span><span>{{ playerData.health }}</span></div>
          <div class="ditem"><span class="lbl">{{ $t('admin.position') }}</span><span>{{ playerData.pos }}</span></div>
        </div>
        <p v-if="!playerOnline" class="hint">{{ $t('admin.notOnline') }}</p>
      </div>
      <p v-else class="hint">{{ $t('admin.loadingPlayer') }}</p>
    </div>

    <!-- Account info -->
    <div class="section">
      <h3>{{ $t('admin.accountInfo') }}</h3>
      <div class="info-grid">
        <div class="iitem"><span class="lbl">{{ $t('admin.role') }}</span><span class="badge">{{ auth.user?.roleName }}</span></div>
        <div class="iitem"><span class="lbl">{{ $t('admin.binding') }}</span><span :class="auth.bindingStatus=='bound'?'tgreen':'tred'">{{ auth.bindingStatus }}</span></div>
      </div>
    </div>

    <!-- Quick actions -->
    <div class="actions">
      <button v-if="!auth.mustChangePassword" class="btn" @click="$router.push('/user/first-modify-password')">{{ $t('admin.changePassword') }}</button>
      <button v-if="auth.bindingStatus!=='bound'" class="btn btn-bind" @click="$router.push('/user/bind')">{{ $t('admin.playerBinding') }}</button>
      <router-link v-if="auth.hasPermission('admin.users.read')||auth.hasPermission('admin.roles.read')" to="/admin" class="btn btn-admin">{{ $t('admin.adminPanel') }}</router-link>
      <button class="btn btn-logout" @click="handleLogout">{{ $t('admin.logout') }}</button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { useAuthStore } from '../../stores/auth';
import api from '../../api/index.js';
const router=useRouter();const auth=useAuthStore();
const playerOnline=ref(false);const playerData=ref(null);const playerLoading=ref(false);
const fetchStatus=async()=>{if(!auth.user?.playerName)return;playerLoading.value=true;try{const{data}=await api.get('/status');const s=data.data;if(s?.players?.list){const p=s.players.list.find(p=>p.name===auth.user.playerName);if(p){playerOnline.value=true;playerData.value={dimension:p.dimension||'-',gamemode:p.gamemode||'-',health:p.health!=null?String(p.health):'-',pos:p.pos?`${p.pos.x},${p.pos.y},${p.pos.z}`:'-'}}else{playerOnline.value=false;playerData.value=null}}}catch{}finally{playerLoading.value=false}};
const handleLogout=async()=>{await auth.logout();router.push('/login')};
onMounted(fetchStatus);
</script>

<style scoped>
.user-home h2{color:var(--text-strong);margin:0 0 16px}.force-banner{padding:10px 16px;border-radius:8px;font-weight:600;font-size:.9rem;margin-bottom:16px;background:#fef3c7;border:1px solid #fcd34d;color:#92400e}.force-banner.bind-banner{background:#dbeafe;border:1px solid #93c5fd;color:#1e40af}.section{background:var(--bg-color);border:1px solid var(--border-color);border-radius:8px;padding:16px;margin-bottom:12px}.section h3{margin:0 0 10px;font-size:.95rem;color:var(--text-secondary)}.status-badge{display:inline-block;padding:4px 12px;border-radius:20px;font-weight:600;font-size:.85rem;margin-bottom:8px}.status-badge.online{background:#f0fdf4;color:#16a34a;border:1px solid #bbf7d0}.status-badge.offline{background:#f1f5f9;color:#64748b;border:1px solid #e2e8f0}.player-details{display:grid;grid-template-columns:1fr 1fr;gap:6px;margin-top:8px}.ditem{display:flex;flex-direction:column;gap:2px;font-size:.85rem}.lbl{font-size:.75rem;color:var(--text-secondary)}.info-grid{display:grid;grid-template-columns:1fr 1fr;gap:8px}.iitem{display:flex;flex-direction:column;gap:2px}.badge{background:var(--card-bg);padding:2px 8px;border-radius:4px;font-size:.8rem;display:inline-block;width:fit-content}.tgreen{color:#16a34a}.tred{color:#dc2626}.hint{font-size:.85rem;color:var(--text-secondary);margin:8px 0 0}.actions{display:flex;flex-direction:column;gap:8px;margin-top:16px}.btn{padding:10px 16px;border-radius:8px;border:1px solid var(--border-color);background:var(--card-bg);color:var(--text-strong);cursor:pointer;font-size:.9rem;text-align:center;text-decoration:none;display:block}.btn:hover{background:var(--bg-color)}.btn-bind{border-color:var(--primary-color);color:var(--primary-color)}.btn-admin{background:var(--primary-color)!important;color:#fff!important;border:none!important}.btn-logout{border-color:#fecaca;color:#dc2626}.btn-logout:hover{background:#fef2f2!important}
</style>
