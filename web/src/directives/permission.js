import { useAuthStore } from '../stores/auth';

export const vPermission = {
  mounted(el, binding) {
    const key = binding.value;
    if (!key) return;
    const auth = useAuthStore();
    if (!auth.hasPermission(key)) {
      el.style.display = 'none';
    }
  }
};
