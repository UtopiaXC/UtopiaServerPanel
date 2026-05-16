<template>
  <div class="bind-page">
    <h2>{{ $t('admin.playerBinding') }}</h2>
    <p class="desc">{{ $t('auth.mustBindBanner') }}</p>
    <form @submit.prevent="handleBind">
      <div class="fg"><label>{{ $t('admin.bindingCode') }}</label>
        <div class="row">
          <input v-model="code" type="text" placeholder="" maxlength="6" required style="text-transform:uppercase;letter-spacing:4px;text-align:center;font-size:1.2rem;font-family:monospace;width:140px" />
          <button class="btn" :disabled="loading">{{ loading?'...':$t('admin.bind') }}</button>
        </div>
      </div>
    </form>
    <div v-if="err" class="err">{{ err }}</div>
    <div v-if="ok" class="ok">{{ ok }}</div>
    <p class="hint">{{ $t('admin.getCodeHint') }} <code>/usp bind</code></p>
    <button class="btn-logout" @click="handleLogout">{{ $t('admin.logout') }}</button>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { useAuthStore } from '../../stores/auth';
import { bindingAPI } from '../../api/admin';
const router=useRouter();const auth=useAuthStore();
const code=ref('');const err=ref('');const ok=ref('');const loading=ref(false);
const handleBind=async()=>{err.value='';ok.value='';loading.value=true;try{const{data}=await bindingAPI.bind(code.value.toUpperCase());ok.value=data.data.message+' Redirecting...';code.value='';setTimeout(async()=>{await auth.fetchUser();router.push('/user/home')},1500)}catch(e){err.value=e.response?.data?.message||'Binding failed'}finally{loading.value=false}};
const handleLogout=async()=>{await auth.logout();router.push('/login')};
</script>

<style scoped>
.bind-page h2{color:var(--text-strong);margin:0 0 8px}.desc{font-size:.85rem;color:var(--text-secondary);margin:0 0 16px}.fg{margin-bottom:12px}.fg label{display:block;margin-bottom:4px;font-size:.85rem;color:var(--text-secondary)}.row{display:flex;gap:10px;align-items:center}.btn{padding:10px 20px;background:var(--primary-color);color:#fff;border:none;border-radius:8px;font-size:.95rem;font-weight:600;cursor:pointer;white-space:nowrap}.btn:disabled{opacity:.6}.btn-logout{width:100%;margin-top:10px;padding:10px;background:transparent;border:1px solid #fecaca;border-radius:8px;color:#dc2626;cursor:pointer;font-size:.9rem}.btn-logout:hover{background:#fef2f2}.err{background:#fef2f2;border:1px solid #fecaca;color:#dc2626;padding:8px 12px;border-radius:6px;font-size:.85rem;margin-bottom:8px}.ok{background:#f0fdf4;border:1px solid #bbf7d0;color:#16a34a;padding:8px 12px;border-radius:6px;font-size:.85rem;margin-bottom:8px}.hint{font-size:.85rem;color:var(--text-secondary);margin-top:8px}.hint code{background:var(--bg-color);padding:2px 6px;border-radius:3px}
</style>
