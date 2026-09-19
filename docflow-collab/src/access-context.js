/** 把 Java 返回的权限写入服务端连接上下文，客户端不能覆盖 user 或 readOnly。 */
export function applyInitialAccess(data, access) {
  data.connectionConfig.readOnly = !access.writable
  return {
    protocolVersion: access.protocolVersion,
    documentId: access.documentId,
    clientId: data.requestParameters.get('clientId') || data.socketId,
    token: data.token,
    user: { id: access.userId, name: access.displayName, avatar: access.avatar },
    initialContent: access.initialContent,
    initialContentFormat: access.initialContentFormat,
    readOnly: !access.writable
  }
}
