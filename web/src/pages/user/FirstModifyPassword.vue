<template>
  <div class="fmp-page">
    <h2>{{ $t('admin.changePassword') }}</h2>
    <p class="desc">{{ $t('auth.mustChangePwBanner') }}</p>
    <form @submit.prevent="changePassword">
      <div class="fg"><label>{{ $t('admin.currentPassword') }}</label><input v-model="oldPw" type="password" required /></div>
      <div class="fg"><label>{{ $t('admin.newPassword') }}</label><input v-model="newPw1" type="password" required minlength="4" /></div>
      <div class="fg"><label>{{ $t('admin.confirmNewPassword') }}</label><input v-model="newPw2" type="password" required /></div>
      <div v-if="err" class="err">{{ err }}</div>
      <div v-if="ok" class="ok">{{ ok }}</div>
      <button class="btn" :disabled="loading">{{ loading?'...':$t('admin.changePasswordBtn') }}</button>
    </form>
    <button class="btn-logout" @click="handleLogout">{{ $t('admin.logout') }}</button>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { useAuthStore } from '../../stores/auth';
const router=useRouter();const auth=useAuthStore();
const oldPw=ref('');const newPw1=ref('');const newPw2=ref('');const err=ref('');const ok=ref('');const loading=ref(false);
const changePassword=async()=>{err.value='';ok.value='';if(newPw1.value!==newPw2.value){err.value='Passwords do not match';return}loading.value=true;try{await auth.changePassword(oldPw.value,newPw1.value);ok.value='Password changed! Redirecting...';setTimeout(()=>{router.push('/user/home')},1500)}catch(e){err.value=e.response?.data?.message||'Failed'}finally{loading.value=false}};
const handleLogout=async()=>{await auth.logout();router.push('/login')};
</script>

<style scoped>
.fmp-page h2{color:var(--text-strong);margin:0 0 8px}.desc{font-size:.85rem;color:var(--text-secondary);margin:0 0 16px}.fg{margin-bottom:12px}.fg label{display:block;margin-bottom:4px;font-size:.85rem;color:var(--text-secondary)}.fg input{width:100%;padding:8px 10px;border:1px solid var(--border-color);border-radius:6px;background:var(--bg-color);color:var(--text-strong);box-sizing:border-box}.btn{width:100%;padding:12px;background:var(--primary-color);color:#fff;border:none;border-radius:8px;font-size:1rem;font-weight:600;cursor:pointer}.btn:disabled{opacity:.6}.btn-logout{width:100%;margin-top:10px;padding:10px;background:transparent;border:1px solid #fecaca;border-radius:8px;color:#dc2626;cursor:pointer;font-size:.9rem}.btn-logout:hover{background:#fef2f2}.err{background:#fef2f2;border:1px solid #fecaca;color:#dc2626;padding:8px 12px;border-radius:6px;font-size:.85rem;margin-bottom:8px}.ok{background:#f0fdf4;border:1px solid #bbf7d0;color:#16a34a;padding:8px 12px;border-radius:6px;font-size:.85rem;margin-bottom:8px}
</style>
