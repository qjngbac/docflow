<template>
  <main class="login-page">
    <section class="login-panel" aria-labelledby="login-title">
      <div class="login-brand"><img src="/favicon.svg" alt="" /><strong>云笺</strong></div>
      <p class="subtitle">在线协作文档平台 <span>——</span> 邀请您的伙伴一起编辑</p>

      <div class="login-tabs" role="tablist" aria-label="登录方式">
        <button :class="['tab', { active: mode === 'login' }]" type="button" @click="mode = 'login'">登录</button>
        <button :class="['tab', { active: mode === 'register' }]" type="button" @click="mode = 'register'">注册</button>
      </div>

      <form class="login-form" @submit.prevent="handleSubmit">
        <h1 id="login-title">{{ mode === 'login' ? '欢迎回来' : '创建账号' }}</h1>
        <div class="form-group">
          <label for="username">用户名</label>
          <div class="input-wrapper">
            <User :size="18" />
            <input id="username" v-model.trim="form.username" class="input" autocomplete="username" placeholder="请输入用户名" required @blur="refreshLoginSecurity" />
          </div>
        </div>
        <button v-if="mode === 'login'" class="forgot-link" type="button" @click="resetOpen = true">忘记密码？</button>
        <div class="form-group">
          <label for="password">密码</label>
          <div class="input-wrapper">
            <Lock :size="18" />
            <input id="password" v-model="form.password" class="input" type="password" :autocomplete="mode === 'login' ? 'current-password' : 'new-password'" placeholder="请输入密码" required />
          </div>
        </div>
        <div v-if="mode === 'register'" class="form-group">
          <label for="email">邮箱 <span>可选</span></label>
          <div class="input-wrapper">
            <Mail :size="18" />
            <input id="email" v-model.trim="form.email" class="input" type="email" autocomplete="email" placeholder="name@example.com" />
          </div>
        </div>
        <div v-if="mode === 'login' && captcha.required" class="captcha-group">
          <label for="captcha-code">登录验证码</label>
          <div class="captcha-row">
            <div class="input-wrapper"><ShieldCheck :size="18" /><input id="captcha-code" v-model.trim="form.captchaCode" class="input" autocomplete="off" maxlength="5" placeholder="请输入图中字符" required /></div>
            <button class="captcha-image" type="button" title="看不清，换一张" @click="loadCaptcha"><img v-if="captcha.image" :src="captcha.image" alt="登录验证码" /></button>
          </div>
          <small>看不清可以点击图片更换</small>
        </div>
        <label v-if="mode === 'login'" class="remember-login"><input v-model="form.rememberMe" type="checkbox" />在这台设备上保持登录</label>
        <button class="btn btn-primary submit-btn" type="submit" :disabled="loading">
          <Loader2 v-if="loading" :size="18" class="spin" />
          {{ loading ? '处理中...' : mode === 'login' ? '登录' : '注册并登录' }}
        </button>
      </form>
    </section>
    <div v-if="resetOpen" class="modal-backdrop" @click.self="resetOpen = false"><form class="modal reset-modal" @submit.prevent="confirmReset"><header><h2>找回密码</h2><button class="btn btn-icon btn-ghost" type="button" @click="resetOpen = false"><X :size="18" /></button></header><label>注册邮箱<input v-model.trim="reset.email" class="input" type="email" required /></label><div class="reset-code"><input v-model.trim="reset.code" class="input" placeholder="验证码" maxlength="6" required /><button class="btn btn-secondary" type="button" @click="requestResetCode">获取验证码</button></div><p v-if="reset.debugCode">本地开发验证码：<strong>{{ reset.debugCode }}</strong></p><label>新密码<input v-model="reset.newPassword" class="input" type="password" minlength="8" maxlength="72" required /></label><footer><button class="btn btn-secondary" type="button" @click="resetOpen = false">取消</button><button class="btn btn-primary" type="submit">重置密码</button></footer></form></div>
  </main>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Loader2, Lock, Mail, ShieldCheck, User, X } from 'lucide-vue-next'
import { userApi } from '../api'
import { useUserStore } from '../store'
import { error, success } from '../utils/toast'

const router = useRouter()
const userStore = useUserStore()
const mode = ref('login')
const loading = ref(false)
const resetOpen = ref(false)
const form = reactive({ username: '', password: '', email: '', rememberMe: false, captchaId: '', captchaCode: '' })
const captcha = reactive({ required: false, image: '' })
const reset = reactive({ email: '', code: '', newPassword: '', debugCode: '' })

async function handleSubmit() {
  loading.value = true
  try {
    const payload = { username: form.username, password: form.password, rememberMe: form.rememberMe,
      captchaId: captcha.required ? form.captchaId : undefined,
      captchaCode: captcha.required ? form.captchaCode : undefined }
    const response = mode.value === 'login'
      ? await userApi.login(payload)
      : await userApi.register({ ...payload, email: form.email || undefined })
    userStore.setAuth(response.data)
    success(mode.value === 'login' ? '登录成功' : '注册成功')
    await router.push('/')
  } catch (exception) {
    const messages = {
      'Invalid username or password': '用户名或密码错误',
      'Username already exists': '用户名已存在',
      'Email already exists': '邮箱已被使用'
    }
    error(messages[exception?.message] || exception?.message || '操作失败')
    if (mode.value === 'login') await refreshLoginSecurity(true)
  } finally {
    loading.value = false
  }
}
async function refreshLoginSecurity(forceCaptcha = false) {
  if (mode.value !== 'login' || !form.username) return
  try {
    const response = await userApi.loginSecurity(form.username)
    captcha.required = forceCaptcha || Boolean(response.data?.captchaRequired)
    if (captcha.required) await loadCaptcha()
  } catch { /* 登录仍可由服务端完成最终校验 */ }
}
async function loadCaptcha() {
  try {
    const response = await userApi.captcha(form.username)
    form.captchaId = response.data?.captchaId || ''
    form.captchaCode = ''
    captcha.image = response.data?.imageDataUrl || ''
  } catch (exception) { error(exception.message || '验证码加载失败，请稍后重试') }
}
async function requestResetCode() { if (!reset.email) return error('请输入注册邮箱'); try { const response = await userApi.requestPasswordReset(reset.email); reset.debugCode = response.data?.debugCode || ''; success('验证码已生成') } catch (exception) { error(exception.message === 'Email is not registered' ? '该邮箱尚未注册' : exception.message || '请求失败') } }
async function confirmReset() { try { await userApi.confirmPasswordReset({ email: reset.email, code: reset.code, newPassword: reset.newPassword }); success('密码已重置，请使用新密码登录'); resetOpen.value = false; Object.assign(reset, { email: '', code: '', newPassword: '', debugCode: '' }) } catch (exception) { error(exception.message === 'Invalid or expired verification code' ? '验证码无效或已过期' : exception.message || '重置失败') } }
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 24px;
  background: #071431 url('/imgs/login-bg.jpg') center / cover no-repeat;
}
.login-panel {
  width: min(420px, 100%);
  padding: 34px;
  background: rgba(255, 255, 255, 0.97);
  border: 1px solid rgba(255, 255, 255, 0.8);
  border-radius: 8px;
  box-shadow: 0 24px 70px rgba(3, 10, 30, 0.36);
}
.login-brand { display: flex; align-items: center; justify-content: center; gap: 10px; }.login-brand img { width: 44px; height: 44px; }.login-brand strong { color: var(--text); font-size: 26px; letter-spacing: 0; }
.subtitle { margin: 6px 0 24px; text-align: center; color: var(--text-secondary); font-size: 14px; }
.login-tabs { display: grid; grid-template-columns: 1fr 1fr; padding: 3px; background: #edf1f7; border-radius: 6px; }
.tab { height: 36px; border: 0; border-radius: 4px; background: transparent; color: var(--text-secondary); cursor: pointer; font-weight: 600; }
.tab.active { background: white; color: var(--primary); box-shadow: 0 1px 3px rgba(15, 23, 42, .12); }
.login-form h1 { margin: 24px 0 18px; font-size: 20px; letter-spacing: 0; }
.form-group { margin-bottom: 16px; }
.form-group label { display: block; margin-bottom: 6px; font-size: 13px; font-weight: 600; }
.form-group label span { color: var(--text-muted); font-weight: 400; }
.input-wrapper { position: relative; }
.input-wrapper svg { position: absolute; left: 12px; top: 50%; transform: translateY(-50%); color: var(--text-muted); }
.input-wrapper .input { padding-left: 40px; }
.submit-btn { width: 100%; justify-content: center; min-height: 42px; margin-top: 4px; }
.forgot-link { display: block; margin: -8px 0 10px auto; border: 0; background: transparent; color: var(--primary); cursor: pointer; font-size: 12px; }.reset-modal { width: min(420px, calc(100vw - 28px)); }.reset-modal > label { display: grid; gap: 6px; }.reset-code { display: grid; grid-template-columns: 1fr auto; gap: 8px; }.reset-modal > p { color: #9a3412; font-size: 12px; }
.remember-login { display: flex; align-items: center; gap: 7px; margin: -3px 0 13px; color: var(--text-secondary); font-size: 12px; cursor: pointer; }.remember-login input { width: 15px; height: 15px; accent-color: var(--primary); }
.captcha-group{display:grid;gap:6px;margin:0 0 14px}.captcha-group>label{font-size:13px;font-weight:600}.captcha-group>small{color:var(--text-muted);font-size:11px}.captcha-row{display:grid;grid-template-columns:minmax(0,1fr) 126px;gap:8px}.captcha-image{height:42px;overflow:hidden;border:1px solid var(--border);border-radius:6px;background:#eef4ff;cursor:pointer}.captcha-image img{display:block;width:100%;height:100%;object-fit:cover}
.spin { animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
@media (max-width: 480px) { .login-page { padding: 14px; } .login-panel { padding: 26px 20px; } }
</style>
