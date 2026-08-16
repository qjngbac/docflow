<template>
  <div v-if="open" class="modal-backdrop profile-backdrop" @click.self="emit('close')">
    <section class="modal profile-modal" role="dialog" aria-modal="true" aria-labelledby="profile-title">
      <header><div><h2 id="profile-title">个人中心</h2><p>管理个人资料、账号安全和登录设备</p></div><button class="btn btn-icon btn-ghost" type="button" title="关闭" @click="emit('close')"><X :size="18" /></button></header>
      <div class="profile-tabs">
        <button :class="{ active: tab === 'profile' }" type="button" @click="tab = 'profile'"><UserRound :size="16" />个人资料</button>
        <button :class="{ active: tab === 'security' }" type="button" @click="tab = 'security'"><ShieldCheck :size="16" />账号安全</button>
        <button :class="{ active: tab === 'devices' }" type="button" @click="openDevices"><MonitorSmartphone :size="16" />登录设备</button>
      </div>

      <form v-if="tab === 'profile'" class="profile-form" @submit.prevent="saveProfile">
        <div class="avatar-editor">
          <div class="avatar-preview"><img v-if="user?.avatar" :src="user.avatar" alt="当前头像" /><span v-else>{{ userInitial }}</span></div>
          <div><strong>个人头像</strong><p>JPG、PNG、GIF、WebP，最大 5 MB</p><div class="inline-actions"><button class="btn btn-secondary" type="button" :disabled="uploading" @click="fileInput?.click()"><Camera :size="16" />更换</button><button v-if="user?.avatar" class="btn btn-ghost danger-text" type="button" @click="removeAvatar"><Trash2 :size="16" />删除</button></div></div>
          <input ref="fileInput" class="visually-hidden" type="file" accept="image/jpeg,image/png,image/gif,image/webp" @change="prepareCrop" />
        </div>
        <label>昵称<input v-model.trim="profileForm.nickname" class="input" maxlength="50" required /></label>
        <label>用户名<input class="input" :value="user?.username || ''" disabled /></label>
        <div class="email-field"><label>邮箱<input class="input" :value="user?.email || '未填写'" disabled /></label><button class="btn btn-secondary" type="button" @click="emailOpen = !emailOpen"><Mail :size="15" />修改邮箱</button></div>
        <div v-if="emailOpen" class="email-verify">
          <label>新邮箱<input v-model.trim="emailForm.email" class="input" type="email" placeholder="name@example.com" required /></label>
          <div class="code-row"><input v-model.trim="emailForm.code" class="input" placeholder="6 位验证码" maxlength="6" /><button class="btn btn-secondary" type="button" :disabled="emailSending" @click="requestEmailCode">{{ emailSending ? '发送中' : '获取验证码' }}</button></div>
          <p v-if="emailForm.debugCode" class="debug-code">本地开发验证码：<strong>{{ emailForm.debugCode }}</strong></p>
          <button class="btn btn-primary" type="button" @click="confirmEmail">确认更换邮箱</button>
        </div>
        <footer><button class="btn btn-secondary" type="button" @click="emit('close')">取消</button><button class="btn btn-primary" type="submit" :disabled="saving">{{ saving ? '保存中...' : '保存资料' }}</button></footer>
      </form>

      <form v-else-if="tab === 'security'" class="profile-form" @submit.prevent="savePassword">
        <div class="security-summary"><ShieldCheck :size="22" /><div><strong>最近登录</strong><span>{{ formatDate(user?.lastLoginAt) }}</span></div></div>
        <label>当前密码<input v-model="passwordForm.currentPassword" class="input" type="password" autocomplete="current-password" required /></label>
        <label>新密码<input v-model="passwordForm.newPassword" class="input" type="password" autocomplete="new-password" minlength="8" maxlength="72" required /></label>
        <div class="strength"><span :class="strengthClass" :style="{ width: `${passwordStrength * 25}%` }" /><small>密码强度：{{ strengthLabel }}</small></div>
        <label>确认新密码<input v-model="passwordForm.confirmPassword" class="input" type="password" autocomplete="new-password" minlength="8" maxlength="72" required /></label>
        <p class="password-tip">建议至少 10 位，并混合大小写字母、数字和符号。</p>
        <footer><button class="btn btn-secondary" type="button" @click="emit('close')">取消</button><button class="btn btn-primary" type="submit" :disabled="saving">修改密码</button></footer>
      </form>

      <section v-else class="device-panel">
        <header class="device-toolbar">
          <div><strong>已登录设备</strong><span>发现陌生设备时，请立即将其退出并修改密码。</span></div>
          <button v-if="sessions.some(item => !item.current)" class="btn btn-secondary btn-sm" type="button" :disabled="deviceLoading" @click="revokeOtherSessions">退出其他设备</button>
        </header>
        <div v-if="deviceLoading" class="panel-state"><Loader2 :size="24" class="spin" /></div>
        <div v-else-if="sessions.length" class="device-list">
          <article v-for="session in sessions" :key="session.id">
            <Monitor :size="20" />
            <div class="device-copy">
              <strong>{{ session.deviceName || '未知设备' }}<em v-if="session.current">当前设备</em></strong>
              <span>{{ session.ipAddress || '未知 IP' }} · 最近活动 {{ formatDate(session.lastActiveAt) }}</span>
              <small>{{ session.rememberMe ? '已选择保持登录' : '关闭浏览器后退出' }} · {{ formatExpiry(session.expiresAt) }}</small>
            </div>
            <button class="btn btn-ghost btn-sm danger-text" type="button" :disabled="revokingId === session.id" @click="revokeSession(session)">{{ revokingId === session.id ? '退出中' : '退出' }}</button>
          </article>
        </div>
        <div v-else class="panel-state">暂无登录设备记录</div>
      </section>
    </section>
    <section v-if="crop.open" class="crop-dialog"><header><h3>裁剪头像</h3><button class="btn btn-icon btn-ghost" type="button" @click="closeCrop"><X :size="18" /></button></header><div class="crop-viewport"><img :src="crop.src" alt="待裁剪头像" :style="{ transform: `scale(${crop.zoom})` }" /></div><label>缩放<input v-model.number="crop.zoom" type="range" min="1" max="3" step="0.05" /></label><footer><button class="btn btn-secondary" type="button" @click="closeCrop">取消</button><button class="btn btn-primary" type="button" :disabled="uploading" @click="confirmCrop">{{ uploading ? '上传中...' : '裁剪并上传' }}</button></footer></section>
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { Camera, Loader2, Mail, Monitor, MonitorSmartphone, ShieldCheck, Trash2, UserRound, X } from 'lucide-vue-next'
import { userApi } from '../api'
import { error, success } from '../utils/toast'
import { confirmDialog } from '../utils/dialog'

const props = defineProps({ open: Boolean, user: { type: Object, default: null } })
const emit = defineEmits(['close', 'updated', 'session-revoked'])
const tab = ref('profile'); const saving = ref(false); const uploading = ref(false); const fileInput = ref(null); const emailOpen = ref(false); const emailSending = ref(false); const sessions = ref([]); const deviceLoading = ref(false); const revokingId = ref(null)
const profileForm = reactive({ nickname: '' }); const emailForm = reactive({ email: '', code: '', debugCode: '' }); const passwordForm = reactive({ currentPassword: '', newPassword: '', confirmPassword: '' })
const crop = reactive({ open: false, src: '', zoom: 1, fileName: 'avatar.jpg' })
const userInitial = computed(() => (props.user?.nickname || props.user?.username || 'U').charAt(0).toUpperCase())
const passwordStrength = computed(() => { const value = passwordForm.newPassword; let score = 0; if (value.length >= 8) score++; if (value.length >= 12) score++; if (/[a-z]/.test(value) && /[A-Z]/.test(value)) score++; if (/\d/.test(value) && /[^\w]/.test(value)) score++; return score })
const strengthLabel = computed(() => ['未输入', '较弱', '一般', '较强', '强'][passwordStrength.value])
const strengthClass = computed(() => `level-${passwordStrength.value}`)

watch(() => props.open, value => { if (!value) return; tab.value = 'profile'; profileForm.nickname = props.user?.nickname || props.user?.username || ''; emailOpen.value = false; Object.assign(emailForm, { email: '', code: '', debugCode: '' }); Object.assign(passwordForm, { currentPassword: '', newPassword: '', confirmPassword: '' }) })
async function saveProfile() { saving.value = true; try { const response = await userApi.updateProfile({ nickname: profileForm.nickname }); emit('updated', response.data); success('个人资料已更新') } catch (exception) { error(exception.message || '保存失败') } finally { saving.value = false } }
function prepareCrop(event) { const file = event.target.files?.[0]; event.target.value = ''; if (!file) return; if (file.size > 5 * 1024 * 1024) return error('头像不能超过 5 MB'); if (!file.type.startsWith('image/')) return error('请选择图片文件'); crop.src = URL.createObjectURL(file); crop.fileName = file.name; crop.zoom = 1; crop.open = true }
function closeCrop() { if (crop.src) URL.revokeObjectURL(crop.src); Object.assign(crop, { open: false, src: '', zoom: 1 }) }
async function confirmCrop() { uploading.value = true; try { const image = await loadImage(crop.src); const side = Math.min(image.naturalWidth, image.naturalHeight) / crop.zoom; const canvas = document.createElement('canvas'); canvas.width = 512; canvas.height = 512; canvas.getContext('2d').drawImage(image, (image.naturalWidth - side) / 2, (image.naturalHeight - side) / 2, side, side, 0, 0, 512, 512); const blob = await new Promise(resolve => canvas.toBlob(resolve, 'image/jpeg', .9)); const response = await userApi.uploadAvatar(new File([blob], 'avatar.jpg', { type: 'image/jpeg' })); emit('updated', response.data); success('头像已更新'); closeCrop() } catch (exception) { error(exception.message || '头像处理失败') } finally { uploading.value = false } }
function loadImage(src) { return new Promise((resolve, reject) => { const image = new Image(); image.onload = () => resolve(image); image.onerror = reject; image.src = src }) }
async function removeAvatar() { if (!(await confirmDialog('删除当前头像？', { title: '删除头像', confirmText: '删除', danger: true }))) return; try { const response = await userApi.deleteAvatar(); emit('updated', response.data); success('头像已删除') } catch (exception) { error(exception.message || '删除失败') } }
async function requestEmailCode() { if (!emailForm.email) return error('请输入新邮箱'); emailSending.value = true; try { const response = await userApi.requestEmailChange(emailForm.email); emailForm.debugCode = response.data?.debugCode || ''; success('验证码已生成') } catch (exception) { error(exception.message || '验证码发送失败') } finally { emailSending.value = false } }
async function confirmEmail() { if (!emailForm.code) return error('请输入验证码'); try { const response = await userApi.confirmEmailChange({ email: emailForm.email, code: emailForm.code }); emit('updated', response.data); emailOpen.value = false; success('邮箱已更新') } catch (exception) { error(exception.message === 'Invalid or expired verification code' ? '验证码无效或已过期' : exception.message || '邮箱修改失败') } }
async function savePassword() { if (passwordStrength.value < 2) return error('新密码强度过低'); if (passwordForm.newPassword !== passwordForm.confirmPassword) return error('两次输入的新密码不一致'); saving.value = true; try { await userApi.changePassword({ currentPassword: passwordForm.currentPassword, newPassword: passwordForm.newPassword }); success('密码修改成功'); emit('close') } catch (exception) { error(exception.message === 'Current password is incorrect' ? '当前密码不正确' : exception.message || '密码修改失败') } finally { saving.value = false } }
async function openDevices() { tab.value = 'devices'; deviceLoading.value = true; try { const response = await userApi.sessions(); sessions.value = response.data || [] } catch (exception) { error(exception.message || '设备记录加载失败') } finally { deviceLoading.value = false } }
async function revokeSession(session) {
  const label = session.current ? '当前设备' : (session.deviceName || '该设备')
  if (!(await confirmDialog(`确定退出${label}吗？`, { title: '退出登录设备', confirmText: '退出', danger: true }))) return
  revokingId.value = session.id
  try {
    const response = await userApi.revokeSession(session.id)
    if (response.data?.current || session.current) return emit('session-revoked')
    sessions.value = sessions.value.filter(item => item.id !== session.id)
    success('该设备已退出登录')
  } catch (exception) { error(exception.message || '设备退出失败，请稍后重试') }
  finally { revokingId.value = null }
}
async function revokeOtherSessions() {
  if (!(await confirmDialog('确定退出除当前设备外的所有设备吗？', { title: '退出其他设备', confirmText: '全部退出', danger: true }))) return
  deviceLoading.value = true
  try {
    await userApi.revokeOtherSessions()
    sessions.value = sessions.value.filter(item => item.current)
    success('其他设备已全部退出登录')
  } catch (exception) { error(exception.message || '其他设备退出失败，请稍后重试') }
  finally { deviceLoading.value = false }
}
function formatDate(value) { return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '暂无记录' }
function formatExpiry(value) { return value ? `有效期至 ${formatDate(value)}` : '有效期未知' }
</script>

<style scoped>
.profile-backdrop { z-index: 220; }.profile-modal { width: min(560px, calc(100vw - 28px)); padding: 0; overflow: hidden; }.profile-modal > header { padding: 20px 22px 14px; }.profile-modal h2 { font-size: 19px; }.profile-modal header p { margin-top: 3px; color: var(--text-muted); font-size: 12px; }
.profile-tabs { display: grid; grid-template-columns: repeat(3,1fr); margin: 0 22px; padding: 3px; border-radius: 6px; background: #eef2f7; }.profile-tabs button { min-height: 38px; display: flex; align-items: center; justify-content: center; gap: 6px; border: 0; border-radius: 4px; background: transparent; color: var(--text-secondary); cursor: pointer; }.profile-tabs button.active { background: white; color: var(--primary); box-shadow: 0 1px 3px rgba(15,23,42,.12); font-weight: 600; }
.profile-form { display: grid; gap: 13px; max-height: min(650px, calc(100vh - 180px)); overflow-y: auto; padding: 20px 22px 22px; }.profile-form label { display: grid; gap: 6px; color: var(--text-secondary); font-size: 13px; font-weight: 600; }.profile-form .input:disabled { background: #f6f8fb; color: var(--text-muted); }.profile-form footer { display: flex; justify-content: flex-end; gap: 8px; }
.avatar-editor { display: grid; grid-template-columns: 70px minmax(0,1fr); align-items: center; gap: 14px; padding-bottom: 14px; border-bottom: 1px solid var(--border); }.avatar-preview { width: 68px; height: 68px; display: grid; place-items: center; overflow: hidden; border-radius: 50%; background: #dbeafe; color: #1d4ed8; font-size: 24px; font-weight: 700; }.avatar-preview img { width: 100%; height: 100%; object-fit: cover; }.avatar-editor p { margin: 3px 0 8px; color: var(--text-muted); font-size: 11px; }.inline-actions { display: flex; gap: 6px; }.danger-text { color: var(--danger); }
.email-field { display: grid; grid-template-columns: 1fr auto; align-items: end; gap: 8px; }.email-verify { display: grid; gap: 9px; padding: 12px; border: 1px solid var(--border); border-radius: 7px; background: #f8fafc; }.code-row { display: grid; grid-template-columns: 1fr auto; gap: 8px; }.debug-code { color: #9a3412; font-size: 12px; }.security-summary { display: flex; align-items: center; gap: 10px; padding: 12px; border-radius: 7px; background: #f0f7ff; color: var(--primary); }.security-summary div { display: flex; flex-direction: column; }.security-summary span { color: var(--text-secondary); font-size: 12px; }.strength { height: 5px; position: relative; overflow: visible; border-radius: 3px; background: #e2e8f0; }.strength > span { display: block; height: 100%; transition: width .2s; }.strength small { position: absolute; top: 8px; right: 0; color: var(--text-muted); }.level-1 { background: #ef4444; }.level-2 { background: #f59e0b; }.level-3 { background: #3b82f6; }.level-4 { background: #16a34a; }.password-tip { margin-top: 10px; color: var(--text-muted); font-size: 12px; }
.device-panel { min-height: 300px; max-height: min(580px, calc(100vh - 180px)); overflow-y: auto; padding: 18px 22px 22px; }.device-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 12px; }.device-toolbar>div { display: grid; gap: 2px; }.device-toolbar span { color: var(--text-muted); font-size: 11px; }.device-list { display: grid; gap: 8px; }.device-list article { display: grid; grid-template-columns: 32px minmax(0,1fr) auto; align-items: center; gap: 8px; padding: 12px; border: 1px solid var(--border); border-radius: 7px; }.device-list>article>svg { color: var(--primary); }.device-copy { min-width: 0; display: grid; gap: 2px; }.device-copy strong { display: flex; align-items: center; gap: 7px; }.device-copy em { padding: 2px 5px; border-radius: 3px; background: #dcfce7; color: #15803d; font-size: 9px; font-style: normal; font-weight: 600; }.device-copy span,.device-copy small { overflow: hidden; color: var(--text-muted); font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }.panel-state { min-height: 240px; display: grid; place-items: center; color: var(--text-muted); }
.crop-dialog { position: fixed; z-index: 230; left: 50%; top: 50%; width: min(380px, calc(100vw - 28px)); transform: translate(-50%,-50%); padding: 18px; border-radius: 8px; background: white; box-shadow: 0 24px 70px rgba(15,23,42,.28); }.crop-dialog header,.crop-dialog footer { display: flex; align-items: center; justify-content: space-between; }.crop-dialog footer { justify-content: flex-end; gap: 8px; margin-top: 14px; }.crop-dialog>label { display: grid; gap: 5px; margin-top: 12px; font-size: 12px; }.crop-viewport { width: 250px; height: 250px; margin: 14px auto 0; overflow: hidden; border-radius: 50%; background: #e2e8f0; }.crop-viewport img { width: 100%; height: 100%; object-fit: cover; transition: transform .12s; }
.visually-hidden { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); }.spin { animation: spin 1s linear infinite; }@keyframes spin { to { transform: rotate(360deg); } }
@media (max-width: 520px) { .profile-tabs { margin: 0 14px; }.profile-tabs button { flex-direction: column; font-size: 10px; }.profile-form,.device-panel { padding-left: 14px; padding-right: 14px; }.email-field { grid-template-columns: 1fr; }.device-toolbar { align-items: flex-start; flex-direction: column; }.device-list article { grid-template-columns: 28px 1fr; }.device-list article>button { grid-column: 2; justify-self: start; } }
</style>
