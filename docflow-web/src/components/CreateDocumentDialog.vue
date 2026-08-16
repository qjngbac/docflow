<template>
  <div v-if="open" class="modal-backdrop create-backdrop" @click.self="emit('close')">
    <section class="modal create-modal" role="dialog" aria-modal="true" aria-labelledby="create-title">
      <header><div><h2 id="create-title">新建文档</h2><p>从空白、模板或本地文件开始</p></div><button class="btn btn-icon btn-ghost" type="button" @click="emit('close')"><X :size="18" /></button></header>
      <div class="create-tabs">
        <button v-for="item in modes" :key="item.value" :class="{ active: mode === item.value }" type="button" @click="mode = item.value"><component :is="item.icon" :size="17" />{{ item.label }}</button>
      </div>
      <div class="common-create-options">
        <label class="cover-toggle"><input v-model="form.withCover" type="checkbox" />插入封面图</label>
        <div v-if="form.withCover" class="cover-picker"><input ref="coverInput" class="visually-hidden" type="file" accept="image/*" @change="chooseCover"/><button type="button" @click="coverInput?.click()"><img v-if="coverPreview" :src="coverPreview" alt="封面预览"/><ImagePlus v-else :size="24"/><span>{{coverFile?'更换封面':'选择封面图片'}}</span></button></div>
      </div>

      <form v-if="mode === 'blank'" class="create-content" @submit.prevent="createBlank">
        <label>标题<input v-model.trim="form.title" class="input" maxlength="500" placeholder="无标题文档" /></label>
        <label>分类<input v-model.trim="form.category" class="input" maxlength="100" placeholder="例如：工作、学习" /></label>
        <footer><button class="btn btn-secondary" type="button" @click="emit('close')">取消</button><button class="btn btn-primary" type="submit" :disabled="creating">创建</button></footer>
      </form>

      <div v-else-if="mode === 'template'" class="create-content">
        <div v-if="loading" class="template-state"><Loader2 :size="24" class="spin" /></div>
        <div v-else class="template-grid">
          <button v-for="template in templates" :key="template.id" type="button" @click="createFromTemplate(template)"><FileCheck2 :size="22" /><strong>{{ template.name }}</strong><span>{{ template.description || '自定义模板' }}</span><small>{{ template.category || '未分类' }}</small></button>
          <p v-if="!templates.length">暂无模板</p>
        </div>
      </div>

      <div v-else class="create-content import-content">
        <input ref="fileInput" class="visually-hidden" type="file" accept=".md,.markdown,.txt,.doc,.docx" @change="importFile" />
        <button class="import-drop" type="button" :disabled="creating" @click="fileInput?.click()" @dragover.prevent @drop.prevent="handleDrop"><Loader2 v-if="creating" :size="30" class="spin"/><Upload v-else :size="30" /><strong>{{ creating ? '正在导入文档' : '选择或拖入文件' }}</strong><span>支持 Markdown、TXT、DOC 和 DOCX（最大 10 MB）</span></button>
        <label>导入到分类<input v-model.trim="form.category" class="input" maxlength="100" placeholder="可选" /></label>
      </div>
    </section>
  </div>
</template>

<script setup>
import { markRaw, reactive, ref, watch } from 'vue'
import { FileCheck2, FilePlus2, FileUp, ImagePlus, Loader2, Upload, X } from 'lucide-vue-next'
import { docApi, fileApi, templateApi } from '../api'
import { useDocStore } from '../store'
import { error, success } from '../utils/toast'
import { markdownToHtml, sanitizeHtml } from '../utils/contentConversion'

const props = defineProps({ open: Boolean, folderId: { type: Number, default: 0 } })
const emit = defineEmits(['close', 'created'])
const docStore = useDocStore()
const modes = [{ value: 'blank', label: '空白文档', icon: markRaw(FilePlus2) }, { value: 'template', label: '从模板', icon: markRaw(FileCheck2) }, { value: 'import', label: '导入文件', icon: markRaw(FileUp) }]
const mode = ref('blank'); const creating = ref(false); const loading = ref(false); const templates = ref([]); const fileInput = ref(null); const coverInput=ref(null); const coverFile=ref(null); const coverPreview=ref('')
const form = reactive({ title: '', category: '', withCover: false })

watch(() => props.open, async value => { if (!value) return; mode.value = 'blank'; form.title = '';form.category='';form.withCover=false;coverFile.value=null;if(coverPreview.value)URL.revokeObjectURL(coverPreview.value);coverPreview.value='';if (!templates.value.length) await loadTemplates() })
async function loadTemplates() { loading.value = true; try { const response = await templateApi.list(); templates.value = response.data || [] } catch (exception) { error(exception.message || '模板加载失败') } finally { loading.value = false } }
async function create(payload) { creating.value = true; try { let coverImage=null;if(form.withCover&&coverFile.value){const uploaded=await fileApi.uploadImage(coverFile.value);coverImage=uploaded.data.url}const response = await docApi.create({ folderId: props.folderId || 0, category: form.category || null, coverImage, ...payload }); docStore.rememberCreatedDoc(response.data);success('文档创建成功'); emit('created', response.data); emit('close') } catch (exception) { error(exception.message || '创建失败') } finally { creating.value = false } }
const createBlank = () => create({ title: form.title || '无标题文档', content: '<p></p>', contentFormat: 'HTML' })
const createFromTemplate = template => create({
  title: template.name,
  content: template.contentFormat === 'MARKDOWN' ? markdownToHtml(template.content) : sanitizeHtml(template.content),
  contentFormat: 'HTML',
  category: template.category || form.category
})
function handleDrop(event) { const file = event.dataTransfer.files?.[0]; if (file) processFile(file) }
function importFile(event) { const file = event.target.files?.[0]; event.target.value = ''; if (file) processFile(file) }
function chooseCover(event){const file=event.target.files?.[0];event.target.value='';if(!file)return;if(!file.type.startsWith('image/'))return error('请选择图片文件');if(file.size>10*1024*1024)return error('封面图片不能超过 10 MB');if(coverPreview.value)URL.revokeObjectURL(coverPreview.value);coverFile.value=file;coverPreview.value=URL.createObjectURL(file)}
async function processFile(file) {
  const extension = file.name.split('.').pop()?.toLowerCase()
  if (!['md', 'markdown', 'txt', 'doc', 'docx'].includes(extension)) { error('仅支持 Markdown、TXT、DOC 或 DOCX 文件'); return }
  if (file.size > 10 * 1024 * 1024) { error('导入文件不能超过 10 MB'); return }
  if (extension === 'doc'||extension === 'docx') {
    creating.value = true
    try {
      const response = await docApi.importWord(file, props.folderId || 0, form.category || '')
      let imported=response.data
      if(form.withCover&&coverFile.value){const uploaded=await fileApi.uploadImage(coverFile.value);const updated=await docApi.update(imported.id,{coverImage:uploaded.data.url});imported=updated.data}
      docStore.rememberCreatedDoc(imported)
      success(`${extension.toUpperCase()} 文档导入成功`)
      emit('created', imported)
      emit('close')
    } catch (exception) {
      error(exception.message || 'Word 文档导入失败')
    } finally {
      creating.value = false
    }
    return
  }
  try { const content = await file.text(); await create({ title: file.name.replace(/\.[^.]+$/, ''), content: markdownToHtml(content), contentFormat: 'HTML' }) } catch { error('读取文件失败') }
}
</script>

<style scoped>
.create-backdrop { z-index: 210; }.create-modal { width: min(650px, calc(100vw - 28px)); padding: 0; overflow: hidden; }.create-modal > header { padding: 20px 22px 14px; }.create-modal > header div { min-width: 0; }.create-modal h2 { font-size: 19px; }.create-modal header p { margin-top: 3px; color: var(--text-muted); font-size: 12px; }
.create-tabs { display: grid; grid-template-columns: repeat(3,1fr); margin: 0 22px; padding: 3px; border-radius: 6px; background: #eef2f7; }.create-tabs button { min-height: 38px; display: flex; align-items: center; justify-content: center; gap: 6px; border: 0; border-radius: 4px; background: transparent; color: var(--text-secondary); cursor: pointer; }.create-tabs button.active { background: white; color: var(--primary); box-shadow: 0 1px 3px rgba(15,23,42,.12); font-weight: 600; }
.common-create-options{display:flex;align-items:flex-start;gap:14px;margin:14px 22px 0;padding:12px;border:1px solid var(--border-light);border-radius:6px;background:#f8fafc}.common-create-options .cover-toggle{padding-top:8px;white-space:nowrap}
.create-content { display: grid; gap: 14px; min-height: 240px; padding: 20px 22px 22px; }.create-content label { display: grid; gap: 6px; color: var(--text-secondary); font-size: 13px; font-weight: 600; }.create-content footer { display: flex; justify-content: flex-end; gap: 8px; margin-top: auto; }
.template-grid { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 10px; }.template-grid button { min-height: 118px; display: flex; flex-direction: column; align-items: flex-start; gap: 5px; padding: 14px; border: 1px solid var(--border); border-radius: 7px; background: white; color: var(--text); cursor: pointer; text-align: left; }.template-grid button:hover { border-color: var(--primary); background: #f8fbff; }.template-grid svg { color: var(--primary); }.template-grid span { color: var(--text-secondary); font-size: 12px; }.template-grid small { margin-top: auto; color: var(--text-muted); }.template-state { display: grid; place-items: center; min-height: 180px; }
.import-content { align-content: start; }.import-drop { min-height: 160px; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 7px; border: 1px dashed #9db7dd; border-radius: 7px; background: #f7faff; color: var(--text-secondary); cursor: pointer; }.import-drop:hover { border-color: var(--primary); }.import-drop svg { color: var(--primary); }.import-drop strong { color: var(--text); }.import-drop span { font-size: 12px; }.visually-hidden { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); }.spin { animation: spin 1s linear infinite; }@keyframes spin { to { transform: rotate(360deg); } }
.cover-toggle{display:flex!important;grid-auto-flow:column;align-items:center;justify-content:start;gap:7px!important}.cover-picker button{width:220px;height:116px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:6px;overflow:hidden;border:1px dashed #9db7dd;border-radius:5px;background:#f8fbff;color:var(--primary);cursor:pointer}.cover-picker img{width:100%;height:88px;object-fit:cover}.cover-picker span{font-size:11px}
@media (max-width: 540px) { .create-tabs { margin: 0 14px; }.create-tabs button { flex-direction: column; font-size: 11px; }.create-content { padding: 16px 14px; }.template-grid { grid-template-columns: 1fr; } }
</style>
