import test from 'node:test'
import assert from 'node:assert/strict'
import { CrdtStore } from '../src/store.js'

function legacyPool({ exists = false, baseFailure, queueFailure, createFailure } = {}) {
  const calls = []
  const state = { exists, pending: exists ? ['already-persisted-work'] : [] }
  return {
    calls,
    state,
    async execute(sql) {
      calls.push(sql)
      if (sql.startsWith('CREATE TABLE')) {
        if (createFailure) throw createFailure
        assert.match(sql, /IF NOT EXISTS\s+crdt_materialization/i)
        state.exists = true
        return [{}]
      }
      if (sql.includes('FROM crdt_materialization')) {
        if (queueFailure) throw queueFailure
        if (!state.exists) throw Object.assign(new Error("Table 'docflow.crdt_materialization' doesn't exist"), { code: 'ER_NO_SUCH_TABLE', errno: 1146 })
      } else if (baseFailure) {
        throw baseFailure
      }
      return [[]]
    }
  }
}

test('startup adds the missing empty outbox only after validating legacy collaboration tables', async () => {
  const pool = legacyPool()
  await new CrdtStore(pool).verify()
  assert.equal(pool.state.exists, true)
  assert.deepEqual(pool.calls.slice(0, 3), [
    'SELECT 1 FROM crdt_checkpoint LIMIT 1',
    'SELECT 1 FROM crdt_update LIMIT 1',
    'SELECT 1 FROM crdt_checkpoint_history LIMIT 1'
  ])
  assert.equal(pool.calls.filter(sql => sql.startsWith('CREATE TABLE')).length, 1)
  const ddl = pool.calls.find(sql => sql.startsWith('CREATE TABLE'))
  assert.match(ddl, /generation\s+BIGINT\s+NOT NULL\s+AUTO_INCREMENT\s+UNIQUE/i)
  assert.match(ddl, /payload\s+LONGBLOB\s+NOT NULL/i)
  assert.equal(pool.calls.at(-1), 'SELECT 1 FROM crdt_materialization LIMIT 1')
})

test('restarting with an existing outbox needs no CREATE privilege and preserves queued work', async () => {
  const pool = legacyPool({ exists: true, createFailure: new Error('CREATE command denied') })
  await new CrdtStore(pool).verify()
  await new CrdtStore(pool).verify()
  assert.equal(pool.calls.some(sql => sql.startsWith('CREATE TABLE')), false)
  assert.deepEqual(pool.state.pending, ['already-persisted-work'])
})

test('missing base collaboration tables never trigger automatic schema creation', async () => {
  const failure = Object.assign(new Error('Missing crdt_checkpoint'), { code: 'ER_NO_SUCH_TABLE', errno: 1146 })
  const pool = legacyPool({ baseFailure: failure })
  await assert.rejects(new CrdtStore(pool).verify(), error => error === failure)
  assert.equal(pool.calls.some(sql => sql.startsWith('CREATE TABLE')), false)
})

test('an outbox permission error is not mistaken for a missing table', async () => {
  const failure = Object.assign(new Error('SELECT command denied'), { code: 'ER_TABLEACCESS_DENIED_ERROR', errno: 1142 })
  const pool = legacyPool({ queueFailure: failure })
  await assert.rejects(new CrdtStore(pool).verify(), error => error === failure)
  assert.equal(pool.calls.some(sql => sql.startsWith('CREATE TABLE')), false)
})

test('failure to initialize an outbox explains the required privilege or migration', async () => {
  const pool = legacyPool({ createFailure: new Error('CREATE command denied') })
  await assert.rejects(new CrdtStore(pool).verify(), error =>
    error.message.includes('crdt_materialization') && /CREATE|迁移/.test(error.message) && error.cause?.message === 'CREATE command denied')
})
