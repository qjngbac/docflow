<template>
  <div :class="['layout', { 'sidebar-collapsed': sidebarCollapsed }]">
    <div v-if="sidebarOpen" class="sidebar-overlay" @click="sidebarOpen = false" />
    <aside :class="['sidebar', { open: sidebarOpen }]">
      <header class="sidebar-header">
        <button class="brand-lockup" type="button" title="返回文档列表" @click="router.push('/')"><img src="/favicon.svg" alt="" /><strong>云笺</strong></button>
        <button class="btn btn-icon btn-ghost close-sidebar" type="button" title="关闭菜单" @click="sidebarOpen = false"><X :size="18" /></button>
      </header>
      <div class="sidebar-actions">
        <button class="btn btn-primary" type="button" @click="createOpen = true"><Plus :size="18" />新建文档</button>
      </div>
      <div class="sidebar-search"><button type="button" title="全局搜索（Ctrl + K）" @click="globalSearchOpen = true"><Search :size="16" /><span>全局搜索</span><kbd>Ctrl K</kbd></button></div>
      <nav class="sidebar-nav" aria-label="文档导航">
        <button :class="['nav-item', { active: route.name !== 'Admin' && docStore.viewMode === 'documents' && docStore.currentFolderId === 0 }]" type="button" @click="handleSelectFolder(0)"><Files :size="17" /><span>全部文档</span></button>
        <button :class="['nav-item', { active: route.name !== 'Admin' && docStore.viewMode === 'recent' }]" type="button" @click="handleScope('recent')"><Clock3 :size="17" /><span>最近编辑</span></button>
        <button :class="['nav-item', { active: route.name !== 'Admin' && docStore.viewMode === 'favorites' }]" type="button" @click="handleScope('favorites')"><Star :size="17" /><span>我的收藏</span></button>
        <button :class="['nav-item', { active: route.name !== 'Admin' && docStore.viewMode === 'trash' }]" type="button" @click="handleTrash"><Trash2 :size="17" /><span>回收站</span></button>
        <button class="nav-item" type="button" @click="feedbackOpen = true"><MessageSquareWarning :size="17" /><span>问题反馈</span></button>
        <button v-if="userStore.user?.systemRole === 'ADMIN'" :class="['nav-item', { active: route.name === 'Admin' }]" type="button" @click="router.push('/admin')"><ShieldCheck :size="17" /><span>管理后台</span></button>
        <div class="folder-heading" title="可将文件夹拖到这里移至根目录" @dragover.prevent @drop.prevent="handleFolderRootDrop"><span>文件夹</span><button type="button" title="新建文件夹" @click="openFolderDialog('create')"><FolderPlus :size="16" /></button></div>
        <FolderTreeNode v-for="folder in docStore.folderTree" :key="folder.id" :folder="folder" @select="handleSelectFolder" @create="folder => openFolderDialog('create', null, folder.id)" @edit="folder => openFolderDialog('edit', folder)" @delete="handleDeleteFolder" @move="handleMoveFolder" />
      </nav>
      <footer class="sidebar-footer">
        <button class="btn btn-icon btn-ghost notification-button" type="button" title="通知" @click="toggleNotifications"><Bell :size="18"/><span v-if="unreadCount">{{unreadCount>99?'99+':unreadCount}}</span></button>
        <div v-if="notificationsOpen" class="notification-popover"><header><strong>通知</strong><button type="button" @click="markAllNotificationsRead">全部已读</button></header><div class="notification-list"><button v-for="item in notifications" :key="item.id" :class="{unread:!item.isRead}" type="button" @click="openNotification(item)"><strong>{{notificationActorLabel(item)}}</strong><span>{{item.content}}</span><small>{{notificationContextLabel(item)}}</small></button><p v-if="!notifications.length">暂无通知</p></div></div>
        <button class="user-info" type="button" title="打开个人资料" @click="profileOpen = true">
          <div class="avatar"><img v-if="userStore.user?.avatar" :src="userStore.user.avatar" alt="" /><span v-else>{{ userInitial }}</span></div>
          <div><strong>{{ displayName }}</strong><small>{{ userStore.user?.email || `用户名：${userStore.user?.username || ''}` }}</small></div>
        </button>
        <button class="btn btn-icon btn-ghost" type="button" title="退出登录" @click="handleLogout()"><LogOut :size="18" /></button>
      </footer>
    </aside>
    <button class="sidebar-collapse-btn" type="button" :title="sidebarCollapsed ? '展开左侧栏' : '收起左侧栏'" @click="toggleDesktopSidebar">
      <PanelLeftOpen v-if="sidebarCollapsed" :size="16" />
      <PanelLeftClose v-else :size="16" />
    </button>
    <div class="workspace-shell">
      <section v-if="currentAnnouncement" class="system-announcement" role="status" aria-live="polite">
        <Megaphone :size="18" />
        <div><strong>系统提示</strong><span>{{currentAnnouncement.content}}</span></div>
        <button class="btn btn-icon btn-ghost" type="button" title="关闭这条提示" @click="closeAnnouncement"><X :size="17" /></button>
      </section>
      <main class="main-content"><router-view /></main>
    </div>
    <button class="mobile-menu-btn" type="button" title="打开菜单" @click="sidebarOpen = true"><Menu :size="22" /></button>
    <ProfileDialog :open="profileOpen" :user="userStore.user" @close="profileOpen = false" @updated="handleUserUpdated" @session-revoked="handleCurrentSessionRevoked" />
    <GlobalSearchDialog :open="globalSearchOpen" @close="globalSearchOpen = false" @select="handleGlobalSearchSelect" />
    <CreateDocumentDialog :open="createOpen" :folder-id="docStore.currentFolderId" @close="createOpen = false" @created="handleCreatedDoc" />
    <FeedbackDialog :open="feedbackOpen" @close="feedbackOpen = false" />

    <div v-if="folderDialog.open" class="modal-backdrop" @click.self="closeFolderDialog">
      <form class="modal" @submit.prevent="submitFolder">
        <header><h2>{{ folderDialog.mode === 'edit' ? '编辑文件夹' : '新建文件夹' }}</h2><button class="btn btn-icon btn-ghost" type="button" @click="closeFolderDialog"><X :size="18" /></button></header>
        <label>名称<input v-model.trim="folderDialog.name" class="input" maxlength="200" required autofocus /></label>
        <label>上级文件夹<select v-model.number="folderDialog.parentId" class="input"><option :value="0">根目录</option><option v-for="folder in availableParents" :key="folder.id" :value="folder.id">{{ folder.name }}</option></select></label>
        <footer><button class="btn btn-secondary" type="button" @click="closeFolderDialog">取消</button><button class="btn btn-primary" type="submit" :disabled="folderDialog.saving">{{ folderDialog.saving ? '保存中...' : '保存' }}</button></footer>
      </form>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Bell, Clock3, Files, FolderPlus, LogOut, Megaphone, Menu, MessageSquareWarning, PanelLeftClose, PanelLeftOpen, Plus, Search, ShieldCheck, Star, Trash2, X } from 'lucide-vue-next'
import FolderTreeNode from '../components/FolderTreeNode.vue'
import ProfileDialog from '../components/ProfileDialog.vue'
import CreateDocumentDialog from '../components/CreateDocumentDialog.vue'
import GlobalSearchDialog from '../components/GlobalSearchDialog.vue'
import FeedbackDialog from '../components/FeedbackDialog.vue'
import { notificationApi, userApi } from '../api'
import { useDocStore, useUserStore } from '../store'
import { error, success } from '../utils/toast'
import { confirmDialog } from '../utils/dialog'
import { getAuthMeta, getLastActivityAt, getRefreshToken, setLastActivityAt } from '../utils/auth'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const docStore = useDocStore()
const sidebarOpen = ref(false)
const sidebarCollapsed = ref(localStorage.getItem('docflow:sidebar-collapsed') === 'true')
const profileOpen = ref(false)
const createOpen = ref(false)
const globalSearchOpen = ref(false)
const feedbackOpen = ref(false)
const notificationsOpen=ref(false),notifications=ref([]),announcements=ref([]),unreadCount=ref(0)
let sessionTimer = 0
let announcementTimer = 0
let lastActivityWrite = 0
let lastHeartbeat = 0
let loggingOut = false
const activityEvents = ['pointerdown', 'keydown', 'touchstart', 'scroll']
const folderDialog = reactive({ open: false, mode: 'create', folder: null, name: '', parentId: 0, saving: false })
const displayName = computed(() => userStore.user?.nickname || userStore.user?.username || '用户')
const userInitial = computed(() => displayName.value.charAt(0).toUpperCase())
const currentAnnouncement = computed(() => announcements.value[0] || null)
const availableParents = computed(() => docStore.folders.filter(item => {
  const editedId = folderDialog.folder?.id
  if (!editedId || item.id === editedId) return item.id !== editedId
  let current = item
  while (current?.parentId) {
    if (current.parentId === editedId) return false
    current = docStore.folders.find(folder => folder.id === current.parentId)
  }
  return true
}))

onMounted(async () => {
  activityEvents.forEach(name => window.addEventListener(name, recordActivity, { passive: true }))
  window.addEventListener('keydown', handleGlobalShortcut)
  window.addEventListener('docflow:auth-cleared', handleAuthCleared)
  sessionTimer = window.setInterval(checkSession, 30000)
  checkSession()
  try {
    const response = await userApi.getMe()
    userStore.updateUser(response.data)
  } catch {}
  try { await Promise.all([docStore.loadFolders(), docStore.loadDocs()]) }
  catch (exception) { error(exception.message || '数据加载失败') }
  await refreshNotificationState()
  announcementTimer = window.setInterval(refreshNotificationState, 30000)
})
onBeforeUnmount(() => {
  window.clearInterval(sessionTimer)
  window.clearInterval(announcementTimer)
  activityEvents.forEach(name => window.removeEventListener(name, recordActivity))
  window.removeEventListener('keydown', handleGlobalShortcut)
  window.removeEventListener('docflow:auth-cleared', handleAuthCleared)
})

async function showDocumentList(load) {
  if (route.name !== 'DocList') await router.push('/')
  await load()
  sidebarOpen.value = false
}
async function handleSelectFolder(folderId) { await showDocumentList(() => docStore.loadDocs(folderId)) }
async function handleScope(scope) { await showDocumentList(() => docStore.loadScope(scope)) }
async function handleTrash() { await showDocumentList(() => docStore.loadTrash()) }
async function handleLogout(message = '已退出登录') {
  if (loggingOut) return
  loggingOut = true
  const refreshToken = getRefreshToken()
  try { await userApi.logout(refreshToken) } catch {}
  userStore.logout()
  await router.push('/login')
  success(message)
}
function handleAuthCleared() { userStore.logout() }
function handleCurrentSessionRevoked() { profileOpen.value = false; userStore.logout(); router.push('/login'); success('当前设备已退出登录') }
function recordActivity() {
  const now = Date.now()
  if (now - lastActivityWrite < 30000) return
  lastActivityWrite = now
  setLastActivityAt(now)
}
function handleGlobalShortcut(event) {
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
    event.preventDefault()
    globalSearchOpen.value = true
  }
}
async function checkSession() {
  if (loggingOut || !userStore.isLoggedIn) return
  const now = Date.now()
  const meta = getAuthMeta()
  if (meta.sessionExpiresAt && now >= Number(meta.sessionExpiresAt)) return handleLogout('登录已到期，请重新登录')
  const idleTimeout = Number(meta.idleTimeoutSeconds || 1800) * 1000
  if (now - getLastActivityAt() >= idleTimeout) return handleLogout('长时间未操作，已安全退出登录')
  if (now - lastHeartbeat >= 5 * 60 * 1000 && now - getLastActivityAt() < 5 * 60 * 1000) {
    lastHeartbeat = now
    try { await userApi.heartbeat() } catch {}
  }
}
async function handleGlobalSearchSelect(item) {
  if (item.type === 'DOCUMENT') await router.push(`/doc/${item.id}`)
  if (item.type === 'FOLDER') { await router.push('/'); await handleSelectFolder(item.id) }
}
function toggleDesktopSidebar() {
  sidebarCollapsed.value = !sidebarCollapsed.value
  localStorage.setItem('docflow:sidebar-collapsed', String(sidebarCollapsed.value))
}
function handleUserUpdated(user) { userStore.updateUser(user) }
async function handleCreatedDoc(doc) { await router.push(`/doc/${doc.id}`) }
async function toggleNotifications(){notificationsOpen.value=!notificationsOpen.value;if(!notificationsOpen.value)return;try{const response=await notificationApi.list(false);notifications.value=response.data||[]}catch(exception){error(exception.message||'通知加载失败')}}
async function refreshNotificationState(){try{const [countResponse,announcementResponse]=await Promise.all([notificationApi.unreadCount(),notificationApi.list(true,'ADMIN_MESSAGE')]);unreadCount.value=countResponse.data?.count||0;announcements.value=announcementResponse.data||[]}catch{}}
function removeAnnouncement(id){announcements.value=announcements.value.filter(item=>item.id!==id)}
async function closeAnnouncement(){const item=currentAnnouncement.value;if(!item)return;try{await notificationApi.markRead(item.id);removeAnnouncement(item.id);unreadCount.value=Math.max(0,unreadCount.value-1)}catch(exception){error(exception.message||'系统提示关闭失败')}}
async function openNotification(item){try{if(!item.isRead){await notificationApi.markRead(item.id);item.isRead=1;removeAnnouncement(item.id);unreadCount.value=Math.max(0,unreadCount.value-1)}}finally{notificationsOpen.value=false;if(item.docId)router.push(`/doc/${item.docId}`)}}
async function markAllNotificationsRead(){try{await notificationApi.markAllRead();notifications.value.forEach(item=>item.isRead=1);announcements.value=[];unreadCount.value=0}catch(exception){error(exception.message||'操作失败')}}
function notificationActorLabel(item){return item.type==='ADMIN_MESSAGE'?'系统管理员':(item.actorName||'协作者')}
function notificationContextLabel(item){return item.type==='ADMIN_MESSAGE'?'系统提示':(item.documentTitle||'文档通知')}
async function handleMoveFolder({ sourceId, parentId }) { const folder = docStore.folders.find(item => item.id === sourceId); if (!folder) return; try { await docStore.updateFolder(folder, { parentId }); success('文件夹已移动') } catch (exception) { error(exception.message || '文件夹移动失败') } }
function handleFolderRootDrop(event) { const sourceId = Number(event.dataTransfer.getData('text/folder-id')); if (sourceId) handleMoveFolder({ sourceId, parentId: 0 }) }

function openFolderDialog(mode, folder = null, parentId = 0) {
  Object.assign(folderDialog, { open: true, mode, folder, name: folder?.name || '', parentId: folder?.parentId ?? parentId, saving: false })
}
function closeFolderDialog() { folderDialog.open = false }
async function submitFolder() {
  folderDialog.saving = true
  try {
    if (folderDialog.mode === 'edit') await docStore.updateFolder(folderDialog.folder, { name: folderDialog.name, parentId: folderDialog.parentId })
    else await docStore.createFolder(folderDialog.name, folderDialog.parentId)
    success(folderDialog.mode === 'edit' ? '文件夹已更新' : '文件夹已创建')
    closeFolderDialog()
  } catch (exception) { error(exception.message || '文件夹保存失败') }
  finally { folderDialog.saving = false }
}
async function handleDeleteFolder(folder) {
  if (!(await confirmDialog(`确定删除文件夹“${folder.name}”吗？`, { title: '删除文件夹', confirmText: '删除', danger: true }))) return
  try { await docStore.deleteFolder(folder.id); success('文件夹已删除') }
  catch (exception) { error(exception.message || '删除失败') }
}
</script>

<style scoped>
.sidebar-overlay { display: none; }
.layout { position: relative; }
.sidebar { overflow: hidden; transition: width .2s ease, border-color .2s ease; }
.layout.sidebar-collapsed .sidebar { width: 0; border-right-color: transparent; }
.workspace-shell { min-width: 0; flex: 1; display: flex; flex-direction: column; }
.main-content { min-width: 0; min-height: 0; width: 100%; }
.system-announcement { min-height: 46px; display: grid; grid-template-columns: 22px minmax(0, 1fr) 34px; align-items: center; gap: 10px; padding: 8px 16px; border-bottom: 1px solid #bfdbfe; background: #eff6ff; color: #1e3a8a; }
.system-announcement > svg { color: #2563eb; }.system-announcement > div { min-width: 0; display: flex; align-items: baseline; gap: 10px; }.system-announcement strong { flex: 0 0 auto; font-size: 13px; }.system-announcement span { min-width: 0; overflow-wrap: anywhere; color: #1e40af; font-size: 13px; line-height: 1.5; }.system-announcement button { color: #1d4ed8; }
.sidebar-collapse-btn { position: absolute; top: 50%; left: 280px; z-index: 45; width: 26px; height: 58px; display: grid; place-items: center; padding: 0; border: 1px solid var(--border); border-left: 0; border-radius: 0 6px 6px 0; background: white; color: var(--text-muted); cursor: pointer; transform: translateY(-50%); transition: left .2s ease, color .15s ease, background .15s ease; box-shadow: 2px 0 7px rgba(15,23,42,.08); }
.sidebar-collapse-btn:hover { background: var(--primary-light); color: var(--primary); }
.layout.sidebar-collapsed .sidebar-collapse-btn { left: 0; }
.sidebar-header { height: 64px; display: flex; align-items: center; justify-content: space-between; padding: 10px 16px; border-bottom: 1px solid var(--border-light); }
.brand-lockup { display: flex; align-items: center; gap: 9px; padding: 0; border: 0; background: transparent; color: var(--text); cursor: pointer; }.brand-lockup img { width: 34px; height: 34px; }.brand-lockup strong { font-size: 20px; letter-spacing: 0; }
.close-sidebar { display: none; }
.sidebar-actions { padding: 14px 14px 10px; }.sidebar-actions .btn { width: 100%; justify-content: center; }
.sidebar-search { padding: 0 14px 12px; }.sidebar-search button { width: 100%; height: 36px; display: grid; grid-template-columns: 18px minmax(0,1fr) auto; align-items: center; gap: 7px; padding: 0 9px; border: 1px solid var(--border); border-radius: 5px; background: white; color: var(--text-muted); cursor: pointer; text-align: left; }.sidebar-search button:hover { border-color: #b9c9de; background: #fbfdff; }.sidebar-search kbd { padding: 2px 5px; border: 1px solid var(--border); border-radius: 3px; background: #f8fafc; color: var(--text-muted); font-size: 9px; }
.sidebar-nav { flex: 1; overflow-y: auto; padding: 4px 8px; }
.nav-item { width: 100%; min-height: 38px; display: flex; align-items: center; gap: 9px; padding: 7px 10px; border: 0; border-radius: 5px; background: transparent; color: var(--text-secondary); cursor: pointer; font-size: 14px; text-align: left; }
.nav-item:hover { background: var(--border-light); color: var(--text); }.nav-item.active { background: var(--primary-light); color: var(--primary); font-weight: 600; }
.folder-heading { display: flex; align-items: center; justify-content: space-between; margin: 18px 8px 6px; color: var(--text-muted); font-size: 12px; font-weight: 700; text-transform: uppercase; }
.folder-heading button { width: 28px; height: 28px; display: grid; place-items: center; border: 0; border-radius: 4px; background: transparent; color: inherit; cursor: pointer; }.folder-heading button:hover { background: var(--border-light); color: var(--primary); }
.sidebar-footer { position:relative;display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 12px 14px; border-top: 1px solid var(--border); }.notification-button{position:relative;flex:0 0 auto}.notification-button>span{position:absolute;top:-4px;right:-5px;min-width:16px;height:16px;padding:0 4px;border-radius:8px;background:#dc2626;color:white;font-size:9px;line-height:16px}.notification-popover{position:absolute;left:10px;bottom:66px;z-index:80;width:300px;max-height:390px;overflow:hidden;border:1px solid var(--border);border-radius:7px;background:white;box-shadow:var(--shadow-lg)}.notification-popover>header{display:flex;align-items:center;justify-content:space-between;padding:10px 12px;border-bottom:1px solid var(--border)}.notification-popover header button{border:0;background:transparent;color:var(--primary);cursor:pointer;font-size:11px}.notification-list{max-height:330px;overflow:auto}.notification-list>button{width:100%;display:grid;gap:2px;padding:10px 12px;border:0;border-bottom:1px solid var(--border-light);background:white;color:var(--text);cursor:pointer;text-align:left}.notification-list>button.unread{background:#eff6ff}.notification-list span{color:var(--text-secondary);font-size:12px}.notification-list small{color:var(--text-muted)}.notification-list>p{padding:24px;text-align:center;color:var(--text-muted)}
.user-info { min-width: 0; flex: 1; display: flex; align-items: center; gap: 9px; padding: 5px; border: 0; border-radius: 6px; background: transparent; color: inherit; cursor: pointer; text-align: left; }.user-info:hover { background: var(--border-light); }.avatar { width: 34px; height: 34px; flex: 0 0 auto; display: grid; place-items: center; overflow: hidden; border-radius: 50%; background: #dbeafe; color: #1d4ed8; font-weight: 700; }.avatar img { width: 100%; height: 100%; object-fit: cover; }.user-info > div:last-child { min-width: 0; display: flex; flex-direction: column; }.user-info strong, .user-info small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.user-info strong { font-size: 13px; }.user-info small { color: var(--text-muted); font-size: 11px; }
.mobile-menu-btn { display: none; }
@media (max-width: 768px) {
  .sidebar-overlay { display: block; position: fixed; inset: 0; z-index: 90; background: rgba(15, 23, 42, .42); }
  .sidebar { z-index: 100; width: min(86vw, 300px); }.close-sidebar { display: inline-grid; }
  .layout.sidebar-collapsed .sidebar { width: min(86vw, 300px); border-right-color: var(--border); }
  .sidebar-collapse-btn { display: none; }
  .mobile-menu-btn { display: grid; place-items: center; position: fixed; left: 14px; bottom: 14px; z-index: 70; width: 44px; height: 44px; border: 0; border-radius: 50%; background: var(--primary); color: white; box-shadow: var(--shadow-lg); }
  .system-announcement { padding-left: 12px; }.system-announcement > div { align-items: flex-start; flex-direction: column; gap: 1px; }
}
</style>
