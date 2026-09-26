import { onBeforeUnmount, ref, watch } from 'vue'

export function useDebouncedSearch(delayMs = 400) {
  const query = ref('')
  const debounced = ref('')

  let timer: ReturnType<typeof setTimeout> | null = null
  watch(query, (next) => {
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => {
      debounced.value = next
    }, delayMs)
  })

  function flush() {
    if (timer) clearTimeout(timer)
    timer = null
    debounced.value = query.value
  }

  onBeforeUnmount(() => {
    if (timer) clearTimeout(timer)
    timer = null
  })

  return { query, debounced, flush }
}
