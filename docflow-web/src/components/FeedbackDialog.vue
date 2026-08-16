<template>
  <div v-if="open" class="modal-backdrop feedback-backdrop" @click.self="emit('close')">
    <section class="modal feedback-dialog" role="dialog" aria-modal="true" aria-labelledby="feedback-title">
      <header>
        <div><h2 id="feedback-title">问题反馈</h2><p>描述遇到的问题，图片可以帮助管理员更快定位。</p></div>
        <button class="btn btn-icon btn-ghost" type="button" title="关闭" @click="emit('close')"><X :size="18" /></button>
      </header>
      <div class="feedback-tabs">
        <button :class="{ active: tab === 'submit' }" type="button" @click="tab = 'submit'"><MessageSquareWarning :size="16" />提交反馈</button>
        <button :class="{ active: tab === 'mine' }" type="button" @click="openMine"><ListChecks :size="16" />我的反馈</button>
      </div>

      <form v-if="tab === 'submit'" class="feedback-form" @submit.prevent="submit">
        <div v-if="docId" class="related-document"><FileText :size="16" /><span>将关联当前文档（ID：{{ docId }}）</span></div>
        <label>反馈类型<select v-model="form.type" class="input"><option value="BUG">功能异常</option><option value="UI">页面显示问题</option><option value="SUGGESTION">改进建议</option><option value="OTHER">其他问题</option></select></label>
        <label>问题描述<textarea v-model.trim="form.description" class="input" rows="6" maxlength="5000" placeholder="请说明操作步骤、预期结果和实际结果" required /></label>
        <div class="image-toolbar"><div><strong>问题截图</strong><small>最多 6 张，每张不超过 5 MB</small></div><button class="btn btn-secondary btn-sm" type="button" :disabled="previews.length >= 6" @click="imageInput?.click()"><ImagePlus :size="15" />添加图片</button></div>
        <input ref="imageInput" class="visually-hidden" type="file" accept="image/jpeg,image/png,image/gif,image/webp" multiple @change="selectImages" />
        <div v-if="previews.length" class="feedback-images"><figure v-for="(item,index) in previews" :key="item.url"><img :src="item.url" :alt="item.file.name" /><button type="button" title="移除图片" @click="removeImage(index)"><X :size="14" /></button></figure></div>
        <footer><button class="btn btn-secondary" type="button" @click="emit('close')">取消</button><button class="btn btn-primary" type="submit" :disabled="submitting || !form.description">{{ submitting ? '提交中...' : '提交反馈' }}</button></footer>
      </form>

      <section v-else class="mine-panel">
        <div v-if="loading" class="feedback-state"><Loader2 :size="24" class="spin" /></div>
        <div v-else-if="items.length" class="mine-list">
          <article v-for="item in items" :key="item.id">
            <header><strong>{{ typeLabel(item.type) }} #{{ item.id }}</strong><span :class="`status-${item.status.toLowerCase()}`">{{ statusLabel(item.status) }}</span></header>
            <p>{{ item.description }}</p>
            <div v-if="item.images?.length" class="mine-images"><a v-for="image in item.images" :key="image.id" :href="image.fileUrl" target="_blank" rel="noopener"><img :src="image.fileUrl" alt="反馈图片" /></a></div>
            <blockquote v-if="item.adminReply"><strong>管理员回复</strong>{{ item.adminReply }}</blockquote>
            <footer><span v-if="item.documentTitle">关联文档：{{ item.documentTitle }}</span><time>{{ formatDate(item.createdAt) }}</time></footer>
          </article>
        </div>
        <div v-else class="feedback-state">还没有提交过反馈</div>
      </section>
    </section>
  </div>
</template>

<script setup>
import { reactive, ref, watch } from 'vue'
import { FileText, ImagePlus, ListChecks, Loader2, MessageSquareWarning, X } from 'lucide-vue-next'
import { feedbackApi } from '../api'
import { error, success } from '../utils/toast'

const props = defineProps({ open: Boolean, docId: { type: Number, default: null } })
const emit = defineEmits(['close'])
const tab = ref('submit'); const submitting = ref(false); const loading = ref(false); const items = ref([]); const imageInput = ref(null)
const form = reactive({ type: 'BUG', description: '' }); const previews = ref([])

watch(() => props.open, value => { if (value) tab.value = 'submit'; else clearPreviews() })
function selectImages(event) {
  const files = [...(event.target.files || [])]; event.target.value = ''
  for (const file of files) {
    if (previews.value.length >= 6) return error('一次最多上传 6 张图片')
    if (!file.type.startsWith('image/')) { error(`${file.name} 不是图片文件`); continue }
    if (file.size > 5 * 1024 * 1024) { error(`${file.name} 超过 5 MB`); continue }
    previews.value.push({ file, url: URL.createObjectURL(file) })
  }
}
function removeImage(index) { URL.revokeObjectURL(previews.value[index].url); previews.value.splice(index, 1) }
function clearPreviews() { previews.value.forEach(item => URL.revokeObjectURL(item.url)); previews.value = [] }
async function submit() {
  submitting.value = true
  try {
    await feedbackApi.create({ ...form, docId: props.docId, images: previews.value.map(item => item.file) })
    form.description = ''; form.type = 'BUG'; clearPreviews(); success('反馈已提交，管理员处理后会通知您'); await openMine()
  } catch (exception) { error(exception.message || '反馈提交失败，请稍后重试') }
  finally { submitting.value = false }
}
async function openMine() { tab.value = 'mine'; loading.value = true; try { const response = await feedbackApi.mine(); items.value = response.data || [] } catch (exception) { error(exception.message || '反馈记录加载失败') } finally { loading.value = false } }
const typeLabel = value => ({ BUG: '功能异常', UI: '页面显示', SUGGESTION: '改进建议', OTHER: '其他问题' }[value] || '问题反馈')
const statusLabel = value => ({ OPEN: '待处理', PROCESSING: '处理中', RESOLVED: '已解决', CLOSED: '已关闭' }[value] || value)
const formatDate = value => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : ''
</script>

<style scoped>
.feedback-backdrop{z-index:1250}.feedback-dialog{width:min(650px,calc(100vw - 28px));padding:0;overflow:hidden}.feedback-dialog>header{padding:20px 22px 14px}.feedback-dialog h2{font-size:19px}.feedback-dialog header p{margin-top:3px;color:var(--text-muted);font-size:12px}.feedback-tabs{display:grid;grid-template-columns:repeat(2,1fr);margin:0 22px;padding:3px;border-radius:6px;background:#eef2f7}.feedback-tabs button{min-height:38px;display:flex;align-items:center;justify-content:center;gap:6px;border:0;border-radius:4px;background:transparent;color:var(--text-secondary);cursor:pointer}.feedback-tabs button.active{background:white;color:var(--primary);box-shadow:0 1px 3px rgba(15,23,42,.12);font-weight:600}.feedback-form{display:grid;gap:13px;max-height:min(680px,calc(100vh - 170px));overflow:auto;padding:18px 22px 22px}.feedback-form label{display:grid;gap:6px;color:var(--text-secondary);font-size:13px;font-weight:600}.feedback-form textarea{resize:vertical;line-height:1.6}.related-document{display:flex;align-items:center;gap:7px;padding:9px 10px;border-radius:6px;background:#eff6ff;color:#1d4ed8;font-size:12px}.image-toolbar{display:flex;align-items:center;justify-content:space-between;gap:10px}.image-toolbar>div{display:grid}.image-toolbar small{color:var(--text-muted);font-size:11px}.feedback-images{display:grid;grid-template-columns:repeat(3,1fr);gap:8px}.feedback-images figure{position:relative;aspect-ratio:16/10;overflow:hidden;border:1px solid var(--border);border-radius:6px;background:#f8fafc}.feedback-images img{width:100%;height:100%;object-fit:cover}.feedback-images button{position:absolute;top:4px;right:4px;width:24px;height:24px;display:grid;place-items:center;border:0;border-radius:50%;background:rgba(15,23,42,.72);color:white;cursor:pointer}.feedback-form>footer{display:flex;justify-content:flex-end;gap:8px}.mine-panel{min-height:320px;max-height:min(680px,calc(100vh - 170px));overflow:auto;padding:18px 22px 22px}.mine-list{display:grid;gap:9px}.mine-list article{padding:12px;border:1px solid var(--border);border-radius:7px;background:white}.mine-list article>header,.mine-list article>footer{display:flex;align-items:center;justify-content:space-between;gap:8px}.mine-list header span{padding:2px 7px;border-radius:4px;background:#e2e8f0;color:#475569;font-size:10px}.mine-list header .status-processing{background:#fef3c7;color:#a16207}.mine-list header .status-resolved{background:#dcfce7;color:#15803d}.mine-list>article>p{margin:8px 0;color:var(--text-secondary);font-size:13px;white-space:pre-wrap}.mine-list blockquote{display:grid;gap:3px;margin:8px 0;padding:8px 10px;border-left:3px solid #22c55e;background:#f0fdf4;color:#166534;font-size:12px;white-space:pre-wrap}.mine-list footer{color:var(--text-muted);font-size:10px}.mine-images{display:flex;gap:6px;overflow:auto}.mine-images img{width:66px;height:48px;border-radius:4px;object-fit:cover}.feedback-state{min-height:280px;display:grid;place-items:center;color:var(--text-muted)}.visually-hidden{position:absolute;width:1px;height:1px;overflow:hidden;clip:rect(0 0 0 0)}.spin{animation:spin 1s linear infinite}@keyframes spin{to{transform:rotate(360deg)}}
@media(max-width:560px){.feedback-form,.mine-panel{padding-left:14px;padding-right:14px}.feedback-tabs{margin:0 14px}.feedback-images{grid-template-columns:repeat(2,1fr)}}
</style>
