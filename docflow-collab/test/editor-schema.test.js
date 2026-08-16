import test from 'node:test'
import assert from 'node:assert/strict'
import { createInitialDocument, documentToHtml } from '../src/initial-document.js'

test('math and comment metadata survive CRDT materialization', () => {
  const document = createInitialDocument({
    initialContentFormat: 'HTML',
    initialContent: '<p><span data-type="inline-math" data-latex="E=mc^2"></span> <span data-comment-id="42">review</span></p><div data-type="block-math" data-latex="x^2+y^2=z^2"></div>'
  })
  const html = documentToHtml(document)
  assert.match(html, /data-type="inline-math"/)
  assert.match(html, /data-latex="E=mc\^2"/)
  assert.match(html, /data-comment-id="42"/)
  assert.match(html, /data-type="block-math"/)
})

test('page breaks, review marks and paragraph attribution survive CRDT materialization', () => {
  const document = createInitialDocument({
    initialContentFormat: 'HTML',
    initialContent: '<p data-last-edit-by="7" data-last-edit-name="Alice"><span data-change-id="c1" data-change-type="insert" data-author-id="7" data-author-name="Alice">new text</span></p><div data-type="page-break"></div>'
  })
  const html = documentToHtml(document)
  assert.match(html, /data-last-edit-by="7"/)
  assert.match(html, /data-change-id="c1"/)
  assert.match(html, /data-change-type="insert"/)
  assert.match(html, /data-type="page-break"/)
})

test('advanced formatting, numbered formulas, references and notes survive materialization', () => {
  const document = createInitialDocument({
    initialContentFormat: 'HTML',
    initialContent: '<h2 data-collapsed="true" data-text-align="center">Section</h2><p data-line-height="1.5" data-indent="2"><span data-docflow-style="true" data-font-family="serif" data-font-size="18" data-text-color="blue">styled</span> <sup data-type="footnote" data-footnote-id="1" data-footnote-text="note">[1]</sup> <span data-type="citation" data-citation-key="RFC" data-citation-title="Reference">[RFC]</span></p><div data-type="block-math" data-latex="\\ce{H2O}" data-equation-label="eq-water" data-equation-number="1" data-formula-kind="chem"></div><p><span data-type="equation-reference" data-equation-label="eq-water" data-equation-number="1">式（1）</span></p>'
  })
  const html = documentToHtml(document)
  assert.match(html, /data-collapsed="true"/)
  assert.match(html, /data-font-family="serif"/)
  assert.match(html, /data-equation-label="eq-water"/)
  assert.match(html, /data-formula-kind="chem"/)
  assert.match(html, /data-footnote-text="note"/)
  assert.match(html, /data-citation-key="RFC"/)
})
