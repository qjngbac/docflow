import { Redis } from '@hocuspocus/extension-redis'

export function redisExtensions() {
  if ((process.env.CRDT_REDIS_ENABLED || 'true').toLowerCase() !== 'true') return []
  return [new Redis({
    host: process.env.REDIS_HOST || '127.0.0.1',
    port: Number(process.env.REDIS_PORT || 6379),
    password: process.env.REDIS_PASSWORD || undefined
  })]
}
