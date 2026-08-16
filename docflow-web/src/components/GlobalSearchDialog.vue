<template>
  <div v-if="open" class="modal-backdrop global-search-backdrop" @click.self="close">
    <section class="global-search-dialog" role="dialog" aria-modal="true" aria-label="全局搜索">
      <header>
        <Search :size="19" />
        <input ref="inputRef" v-model.trim="keyword" placeholder="搜索文档、用户或文件夹" @input="scheduleSearch" @keydown="handleKeydown" />
        <kbd>Esc</kbd>
      </header>

      <div v-if="selectedUser" class="user-result-detail">
        <button type="button" class="back-result" @click="selectedUser = null"><ArrowLeft :size="16" />返回搜索结果</button>
        <div class="large-avatar"><img v-if="selectedUser.avatar" :src="selectedUser.avatar" alt="" /><span v-else>{{ selectedUser.title.charAt(0) }}</span></div>
        <h3>{{ selectedUser.title }}</h3>
        <p>{{ selectedUser.subtitle }}</p>
        <button class="btn btn-primary" type="button" @click="copyUsername"><Copy :size="15" />复制用户名</button>
        <small>复制后可在文档的“协作者”中添加该用户</small>
      </div>

      <div v-else class="search-results">
        <div v-if="loading" class="search-state"><Loader2 :size="22" class="spin" />正在搜索</div>
        <div v-else-if="!keyword" class="search-state"><Command :size="24" /><span>输入关键词，或按 Ctrl + K 随时打开</span></div>
        <div v-else-if="!flatResults.length" class="search-state"><SearchX :size="24" /><span>没有找到匹配结果</span></div>
        <template v-else>
          <section v-for="group in groupedResults" :key="group.type" class="result-group">
            <h3>{{ group.label }}</h3>
            <button v-for="item in group.items" :key="`${item.type}-${item.id}`" :class="{ active: flatResults[selectedIndex] === item }" type="button" @mouseenter="selectedIndex = flatResults.indexOf(item)" @click="select(item)">
              <span class="result-icon"><FileText v-if="item.type === 'DOCUMENT'" :size="17" /><Folder v-else-if="item.type === 'FOLDER'" :size="17" /><UserRound v-else :size="17" /></span>
              <span class="result-copy"><strong>{{ item.title }}</strong><small>{{ item.subtitle }}</small></span>
              <CornerDownLeft :size="14" />
            </button>
          </section>
        </template>
      </div>
      <footer><span><ArrowUp :size="12" /><ArrowDown :size="12" />选择</span><span><CornerDownLeft :size="12" />打开</span></footer>
    </section>
  </div>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { ArrowDown, ArrowLeft, ArrowUp, Command, Copy, CornerDownLeft, FileText, Folder, Loader2, Search, SearchX, UserRound } from 'lucide-vue-next'
import { globalSearchApi } from '../api'
import { error, success } from '../utils/toast'

const props = defineProps({ open: Boolean })
const emit = defineEmits(['close', 'select'])
const inputRef = ref(null)
const keyword = ref('')
const loading = ref(false)
const selectedIndex = ref(0)
const selectedUser = ref(null)
const results = ref({ documents: [], folders: [], users: [] })
let timer = 0
let requestId = 0

const groupedResults = computed(() => [
  { type: 'DOCUMENT', label: '文档', items: results.value.documents || [] },
  { type: 'FOLDER', label: '文件夹', items: results.value.folders || [] },
  { type: 'USER', label: '用户', items: results.value.users || [] }
].filter(group => group.items.length))
const flatResults = computed(() => groupedResults.value.flatMap(group => group.items))

watch(() => props.open, async value => {
  if (!value) return
  keyword.value = ''
  selectedUser.value = null
  selectedIndex.value = 0
  results.value = { documents: [], folders: [], users: [] }
  await nextTick()
  inputRef.value?.focus()
})

function close() { emit('close') }
function scheduleSearch() {
  window.clearTimeout(timer)
  selectedIndex.value = 0
  const query = keyword.value
  if (!query) { results.value = { documents: [], folders: [], users: [] }; loading.value = false; return }
  timer = window.setTimeout(() => runSearch(query), 220)
}
async function runSearch(query) {
  const currentRequest = ++requestId
  loading.value = true
  try {
    const response = await globalSearchApi.search(query)
    if (currentRequest === requestId) results.value = response.data || { documents: [], folders: [], users: [] }
  } catch (exception) {
    if (currentRequest === requestId) error(exception.message || '搜索失败，请稍后重试')
  } finally {
    if (currentRequest === requestId) loading.value = false
  }
}
function select(item) {
  if (!item) return
  if (item.type === 'USER') { selectedUser.value = item; return }
  emit('select', item)
  close()
}
function handleKeydown(event) {
  if (event.key === 'Escape') { event.preventDefault(); selectedUser.value ? selectedUser.value = null : close(); return }
  if (!flatResults.value.length) return
  if (event.key === 'ArrowDown') { event.preventDefault(); selectedIndex.value = (selectedIndex.value + 1) % flatResults.value.length }
  if (event.key === 'ArrowUp') { event.preventDefault(); selectedIndex.value = (selectedIndex.value - 1 + flatResults.value.length) % flatResults.value.length }
  if (event.key === 'Enter') { event.preventDefault(); select(flatResults.value[selectedIndex.value]) }
}
async function copyUsername() {
  const username = selectedUser.value?.subtitle?.replace(/^用户名：/, '') || ''
  try { await navigator.clipboard.writeText(username); success('用户名已复制') }
  catch { error('复制失败，请手动选择用户名') }
}
</script>

<style scoped>
.global-search-backdrop{z-index:240;align-items:flex-start;padding-top:min(14vh,120px);background:rgba(15,23,42,.4)}
.global-search-dialog{width:min(680px,calc(100vw - 28px));overflow:hidden;border:1px solid #dbe3ee;border-radius:8px;background:white;box-shadow:0 24px 80px rgba(15,23,42,.28)}
.global-search-dialog>header{height:58px;display:grid;grid-template-columns:auto minmax(0,1fr) auto;align-items:center;gap:10px;padding:0 16px;border-bottom:1px solid var(--border)}
.global-search-dialog>header svg{color:var(--text-muted)}.global-search-dialog>header input{height:100%;min-width:0;border:0;outline:0;color:var(--text);font-size:16px}.global-search-dialog kbd{padding:2px 6px;border:1px solid var(--border);border-radius:4px;background:#f8fafc;color:var(--text-muted);font-size:10px}
.search-results{min-height:260px;max-height:min(58vh,520px);overflow:auto;padding:10px}.search-state{min-height:250px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:9px;color:var(--text-muted);font-size:13px}.spin{animation:spin 1s linear infinite}@keyframes spin{to{transform:rotate(360deg)}}
.result-group h3{margin:7px 8px 5px;color:var(--text-muted);font-size:11px;font-weight:600}.result-group button{width:100%;min-height:54px;display:grid;grid-template-columns:34px minmax(0,1fr) 20px;align-items:center;gap:8px;padding:7px 9px;border:0;border-radius:5px;background:transparent;color:var(--text-secondary);cursor:pointer;text-align:left}.result-group button.active{background:var(--primary-light);color:var(--primary)}.result-icon{width:32px;height:32px;display:grid;place-items:center;border-radius:5px;background:#eef4ff}.result-copy{min-width:0;display:grid;gap:2px}.result-copy strong,.result-copy small{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.result-copy strong{color:var(--text);font-size:13px}.result-copy small{color:var(--text-muted);font-size:11px}.result-group button>svg{color:var(--text-muted)}
.global-search-dialog>footer{height:34px;display:flex;justify-content:flex-end;align-items:center;gap:14px;padding:0 12px;border-top:1px solid var(--border);background:#fafbfd;color:var(--text-muted);font-size:10px}.global-search-dialog>footer span{display:flex;align-items:center;gap:3px}
.user-result-detail{min-height:300px;display:flex;flex-direction:column;align-items:center;padding:18px 24px 24px}.back-result{align-self:flex-start;display:flex;align-items:center;gap:5px;border:0;background:transparent;color:var(--primary);cursor:pointer}.large-avatar{width:72px;height:72px;display:grid;place-items:center;overflow:hidden;margin-top:15px;border-radius:50%;background:#dbeafe;color:#1d4ed8;font-size:25px;font-weight:700}.large-avatar img{width:100%;height:100%;object-fit:cover}.user-result-detail h3{margin:10px 0 2px}.user-result-detail p{margin:0 0 16px;color:var(--text-muted);font-size:12px}.user-result-detail .btn{gap:6px}.user-result-detail>small{margin-top:8px;color:var(--text-muted)}
</style>
