import { describe, expect, it } from 'vitest'
import { collectCommentEvents, createCommentEvent } from './collaborationEvents'

describe('comment collaboration events', () => {
  it('delivers a recent event once and ignores stale or repeated events', () => {
    const recent = createCommentEvent('updated', 12, 'client-a', 10_000, () => 'event-a')
    const stale = createCommentEvent('deleted', 13, 'client-b', 1_000, () => 'event-b')
    const seen = new Set()

    expect(collectCommentEvents([{ commentEvent: recent }, { commentEvent: stale }], seen, 12_000, 5_000))
      .toEqual([recent])
    expect(collectCommentEvents([{ commentEvent: recent }], seen, 12_100, 5_000)).toEqual([])
  })
})
