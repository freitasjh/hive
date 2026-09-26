import { ref } from 'vue'

export const breadcrumbCustomLabel = ref<string | null>(null)

export function setBreadcrumbLabel(label: string | null) {
  breadcrumbCustomLabel.value = label
}
