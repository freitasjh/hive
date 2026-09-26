<template>
  <div class="auth-container min-h-screen flex align-items-center justify-content-center p-4">
    <div class="auth-card surface-card p-6 shadow-8 border-round-3xl w-full sm:w-10 md:w-8 lg:w-5 xl:w-4 fade-in-up">
      <div class="text-center mb-6">
        <div class="flex justify-content-center mb-4">
           <div class="logo-container w-5rem h-5rem bg-primary border-circle flex align-items-center justify-content-center shadow-4">
              <i :class="['text-4xl', icon]" class="text-white"></i>
           </div>
        </div>
        <h1 class="text-900 text-5xl mb-2 font-bold tracking-tight">{{ title }}</h1>
        <p class="text-surface-600 font-medium text-lg">{{ subtitle }}</p>
      </div>

      <slot></slot>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  title: string
  subtitle: string
  icon: string
}>()
</script>

<style scoped>
.auth-container {
  background: linear-gradient(135deg, var(--p-primary-900) 0%, var(--p-primary-700) 100%);
  position: relative;
  overflow: hidden;
}

.auth-container::before {
  content: '';
  position: absolute;
  width: 150%;
  height: 150%;
  background: radial-gradient(circle, rgba(255, 193, 7, 0.05) 0%, transparent 70%);
  top: -25%;
  left: -25%;
  pointer-events: none;
}

.auth-card {
  backdrop-filter: blur(10px);
}

.logo-container {
  transition: transform 0.3s ease;
}

.logo-container:hover {
  transform: scale(1.05) rotate(5deg);
}

.tracking-tight {
  letter-spacing: -0.025em;
}

@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.fade-in-up {
  animation: fadeInUp 0.6s cubic-bezier(0.22, 1, 0.36, 1) forwards;
}
</style>
