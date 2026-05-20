<template>
  <div class="profile-page">
    <div class="page-header">
      <h2>{{ $t('admin.profile') }}</h2>
      <div class="header-actions">
        <router-link v-if="auth.hasPermission('admin.read')" to="/admin" class="btn-admin">&#9881; {{ $t('admin.adminPanel') }}</router-link>
        <button class="btn-logout" @click="handleLogout">{{ $t('admin.logout') }}</button>
      </div>
    </div>

    <!-- Force action banner -->
    <div v-if="auth.mustChangePassword" class="force-banner">{{ $t('auth.mustChangePwBanner') }}</div>
    <div v-else-if="!auth.isAdmin && auth.bindingStatus !== 'bound'" class="force-banner bind-banner">{{ $t('auth.mustBindBanner') }}</div>

    <!-- Player Server Data -->
    <div class="section" v-if="auth.user?.playerName">
      <h3>{{ $t('admin.playerSection') }}: {{ auth.user.playerName }}</h3>
      <div v-if="!playerLoading">
        <div class="status-badge" :class="playerOnline?'online':'offline'">{{ playerOnline?'&#x1F7E2; '+$t('admin.online'):'&#x26AB; '+$t('admin.offline') }}</div>
        <div class="player-details" v-if="playerOnline&&playerData">
          <div class="detail-item"><span class="label">{{ $t('admin.dimension') }}</span><span>{{ playerData.dimension }}</span></div>
          <div class="detail-item"><span class="label">{{ $t('admin.gamemode') }}</span><span>{{ playerData.gamemode }}</span></div>
          <div class="detail-item"><span class="label">{{ $t('admin.health') }}</span><span>{{ playerData.health }}</span></div>
          <div class="detail-item"><span class="label">{{ $t('admin.position') }}</span><span>{{ playerData.pos }}</span></div>
        </div>
        <p v-if="!playerOnline" class="offline-hint">{{ $t('admin.notOnline') }}</p>
      </div>
      <p v-else class="loading-text">{{ $t('admin.loadingPlayer') }}</p>
    </div>

    <!-- Account Info -->
    <div class="section" v-if="auth.user">
      <h3>{{ $t('admin.accountInfo') }}</h3>
      <div class="info-grid">
        <div class="info-item"><span class="label">{{ $t('admin.username') }}</span><span>{{ auth.user.username }}</span></div>
        <div class="info-item"><span class="label">{{ $t('admin.role') }}</span><span class="badge">{{ auth.user.roleName }}</span></div>
        <div class="info-item"><span class="label">{{ $t('admin.binding') }}</span><span :class="auth.user.bindingStatus=='bound'?'text-green':'text-red'">{{ auth.user.bindingStatus }}</span></div>
        <div class="info-item" v-if="auth.user.playerName"><span class="label">{{ $t('admin.playerSection') }}</span><span>{{ auth.user.playerName }}</span></div>
      </div>
    </div>

    <!-- Change Username -->
    <div class="section">
      <h3>{{ $t('admin.changeUsername') || 'Change Username' }}</h3>
      <form @submit.prevent="changeUsername">
        <div class="form-group"><label>{{ $t('admin.newUsername') || 'New Username' }}</label><input v-model="un.newUsername" type="text" required minlength="3" /></div>
        <div v-if="unError" class="error-msg">{{ unError }}</div>
        <div v-if="unSuccess" class="success-msg">{{ unSuccess }}</div>
        <button class="btn-primary" :disabled="unLoading">{{ unLoading?'...':($t('admin.changeBtn') || 'Change') }}</button>
      </form>
    </div>

    <!-- Change Password -->
    <div class="section" :class="{'highlight-section':auth.mustChangePassword}">
      <h3>{{ $t('admin.changePassword') }}</h3>
      <form @submit.prevent="changePassword">
        <div class="form-group"><label>{{ $t('admin.currentPassword') }}</label><input v-model="pw.old" type="password" required /></div>
        <div class="form-group"><label>{{ $t('admin.newPassword') }}</label><input v-model="pw.new1" type="password" required minlength="4" /></div>
        <div class="form-group"><label>{{ $t('admin.confirmNewPassword') }}</label><input v-model="pw.new2" type="password" required /></div>
        <div v-if="pwError" class="error-msg">{{ pwError }}</div>
        <div v-if="pwSuccess" class="success-msg">{{ pwSuccess }}</div>
        <button class="btn-primary" :disabled="pwLoading">{{ pwLoading?'...':$t('admin.changePasswordBtn') }}</button>
      </form>
    </div>

    <!-- Player Binding -->
    <div class="section" :class="{'highlight-section':!auth.isAdmin&&auth.bindingStatus!=='bound'}">
      <h3>{{ $t('admin.playerBinding') }}</h3>
      <div v-if="auth.bindingStatus=='bound'" class="bound-info">
        <p class="text-green">&check; {{ $t('admin.boundTo') }} {{ auth.user?.playerName }}</p>
        <button class="btn-danger" @click="handleUnbind" :disabled="unbindLoading">{{ $t('admin.unbind') }}</button>
        <div v-if="unbindMsg" class="info-msg">{{ unbindMsg }}</div>
      </div>
      <div v-else>
        <p class="text-red">{{ $t('admin.notBound') }} {{ $t('admin.bindPrompt') }}</p>
        <form @submit.prevent="handleBind">
          <div class="form-group"><label>{{ $t('admin.bindingCode') }}</label>
            <div class="bind-input-row">
              <input v-model="bindCode" type="text" placeholder="" maxlength="6" required style="text-transform:uppercase;letter-spacing:4px;text-align:center;font-size:1.2rem;font-family:monospace;width:140px" />
              <button class="btn-primary" :disabled="bindLoading">{{ bindLoading?'...':$t('admin.bind') }}</button>
            </div>
          </div>
        </form>
        <div v-if="bindError" class="error-msg">{{ bindError }}</div>
        <div v-if="bindSuccess" class="success-msg">{{ bindSuccess }}</div>
        <p class="hint">{{ $t('admin.getCodeHint') }} <code>/usp bind</code></p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { useAuthStore } from '../../stores/auth';
import { bindingAPI } from '../../api/admin';
import api from '../../api/index.js';

const router=useRouter();const route=useRoute();const auth=useAuthStore();
const pw=reactive({old:'',new1:'',new2:''});const pwError=ref('');const pwSuccess=ref('');const pwLoading=ref(false);
const un=reactive({newUsername:''});const unError=ref('');const unSuccess=ref('');const unLoading=ref(false);
const bindCode=ref('');const bindError=ref('');const bindSuccess=ref('');const bindLoading=ref(false);
const unbindLoading=ref(false);const unbindMsg=ref('');
const playerOnline=ref(false);const playerData=ref(null);const playerLoading=ref(false);

const fetchPlayerStatus=async()=>{if(!auth.user?.playerName)return;playerLoading.value=true;try{const{data}=await api.get('/status');const s=data.data;if(s?.players?.list){const p=s.players.list.find(p=>p.name===auth.user.playerName);if(p){playerOnline.value=true;playerData.value={dimension:p.dimension||'-',gamemode:p.gamemode||'-',health:p.health!=null?String(p.health):'-',pos:p.pos?`${p.pos.x}, ${p.pos.y}, ${p.pos.z}`:'-'}}else{playerOnline.value=false;playerData.value=null}}}catch{}finally{playerLoading.value=false}};

const changePassword=async()=>{pwError.value='';pwSuccess.value='';if(pw.new1!==pw.new2){pwError.value='Passwords do not match';return}pwLoading.value=true;try{await auth.changePassword(pw.old,pw.new1);pwSuccess.value='Password changed!';pw.old='';pw.new1='';pw.new2='';await auth.fetchUser()}catch(e){pwError.value=e.response?.data?.message||'Failed'}finally{pwLoading.value=false}};

const changeUsername=async()=>{unError.value='';unSuccess.value='';unLoading.value=true;try{await auth.changeUsername(un.newUsername);unSuccess.value='Username changed!';un.newUsername='';await auth.fetchUser()}catch(e){unError.value=e.response?.data?.message||'Failed'}finally{unLoading.value=false}};

const handleBind=async()=>{bindError.value='';bindSuccess.value='';bindLoading.value=true;try{const{data}=await bindingAPI.bind(bindCode.value.toUpperCase());bindSuccess.value=data.data.message;bindCode.value='';await auth.fetchUser();fetchPlayerStatus()}catch(e){bindError.value=e.response?.data?.message||'Binding failed'}finally{bindLoading.value=false}};

const handleUnbind=async()=>{unbindMsg.value='';unbindLoading.value=true;try{await bindingAPI.unbind();unbindMsg.value='Unbound successfully.';playerOnline.value=false;playerData.value=null;await auth.fetchUser()}catch(e){unbindMsg.value=e.response?.data?.message||'Unbind failed'}finally{unbindLoading.value=false}};

const handleLogout=async()=>{await auth.logout();router.push('/login')};
onMounted(fetchPlayerStatus);
</script>

<style scoped>
.profile-page{max-width:600px}.page-header{display:flex;justify-content:space-between;align-items:center;margin-bottom:20px}.page-header h2{color:var(--text-strong);margin:0}.header-actions{display:flex;gap:8px}.btn-admin{display:inline-flex;align-items:center;gap:4px;padding:6px 14px;background:var(--primary-color);color:#fff;border-radius:6px;text-decoration:none;font-size:.85rem;font-weight:500}.btn-admin:hover{opacity:.9}.btn-logout{padding:6px 14px;background:transparent;border:1px solid var(--border-color);border-radius:6px;color:var(--text-secondary);cursor:pointer;font-size:.85rem}.btn-logout:hover{border-color:#ef4444;color:#ef4444}.force-banner{padding:10px 16px;border-radius:8px;font-weight:600;font-size:.9rem;margin-bottom:16px}.force-banner{background:#fef3c7;border:1px solid #fcd34d;color:#92400e}.force-banner.bind-banner{background:#dbeafe;border:1px solid #93c5fd;color:#1e40af}.highlight-section{border-color:var(--primary-color)!important;box-shadow:0 0 0 2px rgba(59,130,246,.2)}.section{background:var(--card-bg);border:1px solid var(--border-color);border-radius:10px;padding:20px;margin-bottom:16px}.section h3{margin:0 0 14px;color:var(--text-strong);font-size:1rem}.info-grid{display:grid;grid-template-columns:1fr 1fr;gap:10px}.info-item{display:flex;flex-direction:column;gap:2px}.label{font-size:.8rem;color:var(--text-secondary)}.badge{background:var(--bg-color);padding:2px 8px;border-radius:4px;font-size:.8rem;display:inline-block;width:fit-content}.text-green{color:#16a34a}.text-red{color:#dc2626}.status-badge{display:inline-block;padding:4px 12px;border-radius:20px;font-weight:600;font-size:.85rem;margin-bottom:10px}.status-badge.online{background:#f0fdf4;color:#16a34a;border:1px solid #bbf7d0}.status-badge.offline{background:#f1f5f9;color:#64748b;border:1px solid #e2e8f0}.player-details{display:grid;grid-template-columns:1fr 1fr;gap:8px;margin-top:8px}.detail-item{display:flex;flex-direction:column;gap:2px;font-size:.9rem}.offline-hint{font-size:.85rem;color:var(--text-secondary);margin:8px 0 0}.loading-text{font-size:.85rem;color:var(--text-secondary)}.form-group{margin-bottom:12px}.form-group label{display:block;margin-bottom:4px;font-size:.85rem;color:var(--text-secondary)}.form-group input{width:100%;padding:8px 10px;border:1px solid var(--border-color);border-radius:6px;background:var(--bg-color);color:var(--text-strong);box-sizing:border-box}.bind-input-row{display:flex;gap:10px;align-items:center}.btn-primary{padding:8px 18px;background:var(--primary-color);color:#fff;border:none;border-radius:6px;cursor:pointer;font-size:.9rem}.btn-primary:disabled{opacity:.6}.btn-danger{padding:8px 18px;border:1px solid #fecaca;background:#fef2f2;color:#dc2626;border-radius:6px;cursor:pointer;font-size:.9rem}.btn-danger:disabled{opacity:.6}.error-msg{background:#fef2f2;border:1px solid #fecaca;color:#dc2626;padding:8px 12px;border-radius:6px;font-size:.85rem;margin-bottom:8px}.success-msg{background:#f0fdf4;border:1px solid #bbf7d0;color:#16a34a;padding:8px 12px;border-radius:6px;font-size:.85rem;margin-bottom:8px}.info-msg{background:var(--bg-color);padding:8px 12px;border-radius:6px;font-size:.85rem;margin-top:8px;color:var(--text-secondary)}.hint{font-size:.85rem;color:var(--text-secondary);margin-top:8px}.hint code{background:var(--bg-color);padding:2px 6px;border-radius:3px}.bound-info p{margin:0 0 10px}
</style>
