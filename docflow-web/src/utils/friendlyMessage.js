const exactMessages = new Map([
  ['No document admin permission', '你没有管理此文档的权限'],
  ['No document edit permission', '你没有编辑此文档的权限'],
  ['No document read permission', '你没有查看此文档的权限'],
  ['Document permission denied', '你没有访问此文档的权限'],
  ['Permission denied', '你没有执行此操作的权限'],
  ['Access denied', '你没有执行此操作的权限'],
  ['Document not found', '文档不存在或已被删除'],
  ['User not found', '没有找到该用户'],
  ['Collaborator not found', '没有找到该协作者'],
  ['Invalid username or password', '用户名或密码错误'],
  ['Refresh token is required', '登录凭证不完整，请重新登录'],
  ['Login session has expired', '登录已过期，请重新登录'],
  ['Login session not found', '该登录设备已退出'],
  ['Search keyword must be 1 to 100 characters', '搜索内容需要为 1 至 100 个字符'],
  ['Invalid share password', '分享密码不正确'],
  ['Share link expired', '分享链接已经过期'],
  ['Email is not registered', '该邮箱尚未注册'],
  ['Invalid or expired verification code', '验证码错误或已过期'],
  ['Current password is incorrect', '当前密码不正确'],
  ['Please login first', '请先登录后再操作'],
  ['Username already exists', '该用户名已被使用'],
  ['Email already exists', '该邮箱已被使用'],
  ['Username and password are required', '请输入用户名和密码'],
  ['Username is required', '请输入用户名'],
  ['Invalid email', '邮箱格式不正确'],
  ['Nickname must be between 1 and 50 characters', '昵称需要为 1 至 50 个字符'],
  ['New password must be 8 to 72 bytes', '新密码长度需要为 8 至 72 个字符'],
  ['New password must be different from current password', '新密码不能与当前密码相同'],
  ['No document comment permission', '你没有批注此文档的权限'],
  ['Document owner already has full permission', '文档所有者已经拥有全部权限'],
  ['Share link not found', '分享链接无效或已被删除'],
  ['Attachment not found', '附件不存在或已被删除'],
  ['Comment not found', '批注不存在或已被删除'],
  ['Folder not found', '文件夹不存在或已被删除'],
  ['No folder permission', '你没有操作此文件夹的权限'],
  ['Folder cannot be its own parent', '文件夹不能移入自己'],
  ['Folder cannot be moved into its descendant', '文件夹不能移入自己的子文件夹'],
  ['Only the owner can permanently delete this document', '只有文档所有者可以彻底删除此文档'],
  ['Document must be in trash before permanent deletion', '请先将文档移到回收站，再彻底删除'],
  ['Page margins must be between 5 and 60 mm', '页边距需要设置为 5 至 60 毫米'],
  ['File is empty', '上传的文件是空的'],
  ['Unsupported file type', '暂不支持这种文件格式'],
  ['Avatar must not exceed 5 MB', '头像文件不能超过 5 MB'],
  ['Avatar must be a JPG, PNG, GIF, or WebP image', '头像只支持 JPG、PNG、GIF 或 WebP 图片'],
  ['Word document has no importable content', 'Word 文档中没有可导入的内容'],
  ['Word file is damaged or unsupported', 'Word 文档已损坏或格式不受支持'],
  ['Word file is empty', 'Word 文档是空的'],
  ['Word file must not exceed 10 MB', 'Word 文档不能超过 10 MB'],
  ['Only .doc and .docx files can be imported here', '这里只能导入 .doc 或 .docx 文档'],
  ['Citation key already exists in this document', '当前文档中已存在相同的文献引用标识'],
  ['A valid DOI is required', '请输入有效的 DOI'],
  ['DOI metadata is unavailable', '暂时无法获取该 DOI 的文献信息'],
  ['Reference not found', '文献条目不存在或已被删除'],
  ['Email delivery is disabled', '邮件发送功能尚未开启'],
  ['Email delivery is not configured', '邮件发送功能尚未配置'],
  ['Unable to send verification email', '验证码邮件发送失败，请稍后重试'],
  ['Please wait before requesting another verification code', '验证码发送过于频繁，请稍后再试'],
  ['Verification code daily limit exceeded', '今日验证码发送次数已达上限'],
  ['Version not found', '该历史版本不存在或已被删除'],
  ['Request failed', '请求未成功，请稍后重试'],
  ['Network Error', '无法连接到服务器，请检查后端服务是否已启动'],
  ['文件安全检查服务暂时不可用，请稍后重试', '文件安全检查服务暂时不可用，请稍后重试'],
  ['文件未通过安全检查，请更换文件后重试', '文件未通过安全检查，请更换文件后重试'],
  ['文件内容与扩展名不一致', '文件内容与扩展名不一致，请确认文件没有被错误改名'],
  ['不允许上传可执行文件', '该文件可能包含可执行内容，系统已拒绝上传']
])

const messageRules = [
  [/admin permission/i, '你没有管理此内容的权限'],
  [/(edit|write) permission/i, '你没有编辑此内容的权限'],
  [/(read|view) permission/i, '你没有查看此内容的权限'],
  [/(permission denied|forbidden|access denied)/i, '你没有执行此操作的权限'],
  [/(document).*(not found)/i, '文档不存在或已被删除'],
  [/(user).*(not found)/i, '没有找到该用户'],
  [/(already exists|duplicate)/i, '该内容已经存在，请勿重复添加'],
  [/(file).*(too large|size limit)/i, '文件过大，请选择符合大小限制的文件'],
  [/(unsupported).*(file|format|type)/i, '暂不支持这种文件格式'],
  [/(invalid).*(token|jwt)|token.*expired/i, '登录状态已失效，请重新登录'],
  [/(login session|refresh token).*(expired|invalid|required|not found)/i, '登录状态已失效，请重新登录'],
  [/(timeout|timed out)/i, '请求等待时间过长，请稍后重试'],
  [/malware|virus|trojan/i, '文件未通过安全检查，请更换文件后重试']
]

export function friendlyMessage(message, fallback = '操作未完成，请稍后重试') {
  const text = String(message || '').trim()
  if (!text) return fallback
  if (exactMessages.has(text)) return exactMessages.get(text)
  for (const [pattern, translated] of messageRules) {
    if (pattern.test(text)) return translated
  }
  if (/[一-鿿]/.test(text)) return text
  return fallback
}

export function httpErrorMessage(status, message) {
  const fallbacks = {
    400: '提交的内容有误，请检查后重试',
    401: '登录状态已失效，请重新登录',
    403: '你没有执行此操作的权限',
    404: '请求的内容不存在或已被删除',
    409: '内容已发生变化，请刷新后重试',
    413: '上传的文件过大',
    415: '暂不支持这种文件格式',
    429: '操作过于频繁，请稍后再试',
    500: '服务器处理失败，请稍后重试',
    502: '服务器暂时不可用，请稍后重试',
    503: '服务正在忙，请稍后重试'
  }
  return friendlyMessage(message, fallbacks[status] || (status ? '请求未成功，请稍后重试' : '无法连接到服务器，请确认后端服务已启动'))
}
