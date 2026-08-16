<template>
  <main class="share-page">
    <section v-if="loading" class="share-state"><Loader2 :size="32" class="spin" /><p>正在打开分享文档</p></section>
    <section v-else-if="needPassword" class="password-panel">
      <div class="lock-icon"><Lock :size="28" /></div><h1>此文档需要访问密码</h1><p>输入分享者提供的密码后继续</p>
      <form @submit.prevent="loadDoc"><input v-model="password" class="input" type="password" placeholder="访问密码" required autofocus /><span v-if="passwordError" class="password-error">{{ passwordError }}</span><button class="btn btn-primary" type="submit">访问文档</button></form>
    </section>
    <article v-else-if="doc" class="shared-document">
      <header><div class="brand"><img src="/favicon.svg" alt="" /><strong>云笺</strong></div><div class="document-heading"><FileText :size="20" /><h1>{{ doc.title }}</h1></div><span>只读分享</span></header>
      <div class="preview-wrap">
        <div v-if="doc.contentFormat === 'HTML'" class="rich-preview" v-html="safeHtml"></div>
        <MdPreview v-else :modelValue="doc.content || ''" language="zh-CN" />
      </div>
    </article>
    <section v-else class="share-state error-panel"><img src="../assets/error-state.svg" alt="无法访问" /><h1>无法访问此文档</h1><p>{{ errorMessage }}</p><button class="btn btn-primary" type="button" @click="router.push('/')">返回云笺</button></section>
  </main>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { FileText, Loader2, Lock } from 'lucide-vue-next'
import { MdPreview } from 'md-editor-v3'
import { sanitizeHtml } from '../utils/contentConversion'
import 'md-editor-v3/lib/style.css'
import { shareApi } from '../api'

const route = useRoute(); const router = useRouter()
const doc = ref(null); const loading = ref(true); const needPassword = ref(false); const password = ref(''); const passwordError = ref(''); const errorMessage = ref('')
const safeHtml = computed(() => sanitizeHtml(doc.value?.content || ''))
onMounted(loadDoc)

async function loadDoc() {
  loading.value = true; passwordError.value = ''
  try { const response = await shareApi.access(route.params.token, password.value); doc.value = response.data; needPassword.value = false }
  catch (exception) {
    if (exception.message === '分享密码不正确') { needPassword.value = true; passwordError.value = password.value ? '密码不正确，请重新输入' : '' }
    else { needPassword.value = false; errorMessage.value = exception.message || '分享链接无效或已过期' }
  } finally { loading.value = false }
}
</script>

<style scoped>
.share-page { min-height: 100vh; padding: 32px; background: #edf5ff url('/imgs/share-bg.jpg') center / cover fixed; }
.share-state, .password-panel { min-height: calc(100vh - 64px); display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 10px; color: var(--text-secondary); }
.password-panel { width: min(420px, 100%); min-height: auto; margin: 10vh auto 0; padding: 36px; border: 1px solid rgba(255,255,255,.9); border-radius: 8px; background: rgba(255,255,255,.94); box-shadow: 0 20px 60px rgba(61,83,130,.16); text-align: center; }.password-panel h1 { font-size: 20px; color: var(--text); }.password-panel p { font-size: 14px; }.password-panel form { width: 100%; display: grid; gap: 10px; margin-top: 8px; }.lock-icon { width: 54px; height: 54px; display: grid; place-items: center; border-radius: 50%; background: var(--primary-light); color: var(--primary); }.password-error { color: var(--danger); font-size: 12px; text-align: left; }
.shared-document { width: min(1000px, 100%); min-height: calc(100vh - 64px); margin: 0 auto; border: 1px solid rgba(216,226,241,.9); border-radius: 8px; background: white; box-shadow: 0 12px 40px rgba(61,83,130,.12); overflow: hidden; }.shared-document > header { min-height: 64px; display: grid; grid-template-columns: 150px minmax(0,1fr) auto; align-items: center; gap: 16px; padding: 10px 20px; border-bottom: 1px solid var(--border); }.brand { display: flex; align-items: center; gap: 8px; }.brand img { width: 30px; height: 30px; }.brand strong { font-size: 18px; }.document-heading { min-width: 0; display: flex; align-items: center; gap: 8px; }.document-heading h1 { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 18px; }.shared-document header > span { padding: 3px 8px; border-radius: 4px; background: #ecfdf5; color: #047857; font-size: 11px; font-weight: 700; }.preview-wrap { min-height: calc(100vh - 130px); padding: 12px 28px 40px; }
.rich-preview { max-width: 860px; margin: 0 auto; color: var(--text); line-height: 1.75; overflow-wrap: anywhere; }.rich-preview :deep(img) { max-width: 100%; height: auto; }.rich-preview :deep(table) { width: 100%; border-collapse: collapse; }.rich-preview :deep(th),.rich-preview :deep(td) { padding: 8px; border: 1px solid var(--border); }
.error-panel img { width: 190px; height: 190px; }.error-panel h1 { color: var(--text); font-size: 20px; }.spin { animation: spin 1s linear infinite; }@keyframes spin { to { transform: rotate(360deg); } }
@media (max-width: 640px) { .share-page { padding: 12px; }.password-panel { margin-top: 8vh; padding: 28px 20px; }.shared-document { min-height: calc(100vh - 24px); }.shared-document > header { grid-template-columns: 1fr auto; }.brand { display: none; }.preview-wrap { padding: 4px 10px 30px; } }
</style>
