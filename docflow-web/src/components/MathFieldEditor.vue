<template>
  <math-field ref="field" class="math-field" virtual-keyboard-mode="manual" @input="emitValue"/>
</template>

<script setup>
import 'mathlive'
import { nextTick, onMounted, ref, watch } from 'vue'

const props = defineProps({ modelValue: { type: String, default: '' }, mathml: { type: String, default: '' } })
const emit = defineEmits(['update:modelValue', 'update:mathml'])
const field = ref(null)
let applying = false

function updateField(source = 'initial') {
  const mathfield = field.value
  if (!mathfield) return
  applying = true
  try {
    if (source === 'initial' && props.mathml?.trim()) {
      mathfield.setValue(props.mathml, { format: 'math-ml', silenceNotifications: true })
    } else {
      mathfield.setValue(props.modelValue || '', { silenceNotifications: true })
    }
  } catch {
    mathfield.setValue(props.modelValue || '', { silenceNotifications: true })
  } finally {
    applying = false
  }
  emit('update:modelValue', mathfield.getValue('latex-expanded'))
  emit('update:mathml', mathfield.getValue('math-ml'))
}

function emitValue() {
  if (applying || !field.value) return
  emit('update:modelValue', field.value.getValue('latex-expanded'))
  emit('update:mathml', field.value.getValue('math-ml'))
}

onMounted(() => nextTick(() => updateField('initial')))
watch(() => props.modelValue, value => {
  if (field.value && value !== field.value.getValue('latex-expanded')) updateField('latex')
})
</script>

<style scoped>
.math-field{display:block;width:100%;min-height:88px;border:1px solid var(--border);border-radius:5px;background:white;padding:10px;font-size:20px;--caret-color:var(--primary);--selection-background-color:#dbeafe;--primary:#2563eb}.math-field:focus-within{outline:2px solid #bfdbfe;border-color:#60a5fa}
</style>
