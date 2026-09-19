import { Redis } from '@hocuspocus/extension-redis'

/** 为多个 Hocuspocus 节点转发房间更新；本地开发可显式关闭。 */
export function redisExtensions() {
  if ((process.env.CRDT_REDIS_ENABLED || 'true').toLowerCase() !== 'true') return []
  return [new Redis({
    host: process.env.REDIS_HOST || '127.0.0.1',
    port: Number(process.env.REDIS_PORT || 6379),
    // 4.x 会把这里的 options 原样作为第三个参数交给 ioredis（new RedisClient(port, host, options)），
    // 顶层写 password 会被静默忽略、导致带密码的 Redis 连不上。
    options: {
      password: process.env.REDIS_PASSWORD || undefined
    }
  })]
}
