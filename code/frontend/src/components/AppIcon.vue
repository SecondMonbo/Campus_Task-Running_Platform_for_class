<script setup>
defineProps({ name: String, size: { type: Number, default: 20 } })
const paths = {
  tasks:
    'M9 5H5a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2h-4M9 3h6v4H9zM8 12h8m-8 4h5',
  wallet: 'M20 8V5a2 2 0 0 0-2-2H5a3 3 0 0 0 0 6h15v11H5a3 3 0 0 1-3-3V6m18 6h-5v5h5m-4-2.5h.01',
  bell: 'M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9m-9 12a3 3 0 0 0 6 0',
  shield: 'M12 3 3 7v5c0 5 9 9 9 9s9-4 9-9V7zM8 12l3 3 5-6',
  dispute: 'M21 11a8 8 0 0 1-8 8H5l-3 3V11a9 9 0 0 1 18 0ZM12 7v6m0 3h.01',
  arrow: 'M5 12h14m-6-6 6 6-6 6',
  refresh: 'M20 7v5h-5m5-1a8 8 0 1 0-2 7',
  pin: 'M20 10c0 6-8 11-8 11S4 16 4 10a8 8 0 0 1 16 0ZM15 10a3 3 0 1 1-6 0 3 3 0 0 1 6 0Z',
  clock: 'M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0ZM12 7v5l3 2',
  chevron: 'm9 5 7 7-7 7',
  close: 'm6 6 12 12M6 18 18 6',
  check: 'm5 12 4 4L19 6',
  inbox: 'M3 13h5l2 3h4l2-3h5M3 13l3-9h12l3 9v7H3z',
  plus: 'M12 5v14M5 12h14',
  campus: 'm2 9 10-6 10 6-10 6zM6 12v6l6 3 6-3v-6M22 9v9',
}
</script>

<template>
  <svg
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.65"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <path :d="paths[name] || paths.tasks" />
  </svg>
</template>
