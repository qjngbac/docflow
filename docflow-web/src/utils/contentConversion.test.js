import { describe, expect, it } from 'vitest'
import { htmlToMarkdown, markdownToHtml, sanitizeHtml } from './contentConversion'

const markdown = `# 一级标题

### 三级标题

**粗体**、*斜体*、~~删除线~~和\`行内代码\`

- 无序项
  - 嵌套项

1. 有序项
2. 第二项

> 引用块

\`\`\`js
const value = 1
\`\`\`

[链接](https://example.com)

![图片](/files/example.png)

| 名称 | 值 |
| --- | --- |
| A | B |

---

- [x] 已完成
- [ ] 未完成

第一行${'  '}
第二行
`

describe('content conversion', () => {
  it('preserves common Markdown structures through HTML', () => {
    const html = markdownToHtml(markdown)
    expect(html).toContain('<h1>一级标题</h1>')
    expect(html).toContain('<table>')
    expect(html).toContain('type="checkbox"')
    expect(html).toContain('<blockquote>')
    expect(html).not.toContain('<script')

    const roundTrip = htmlToMarkdown(html)
    expect(roundTrip).toContain('# 一级标题')
    expect(roundTrip).toContain('~~删除线~~')
    expect(roundTrip).toContain('| 名称 | 值 |')
    expect(roundTrip).toMatch(/-\s+\[x\]\s+已完成/)
    expect(roundTrip).toContain('```js')
  })

  it('preserves supported rich HTML through Markdown', () => {
    const html = '<h2>标题</h2><p><strong>粗体</strong> <em>斜体</em> <u>下划线</u></p>'
      + '<ol><li>项目</li></ol><blockquote><p>引用</p></blockquote>'
      + '<table><tbody><tr><td>A</td><td>B</td></tr></tbody></table><p><br></p>'
    const converted = markdownToHtml(htmlToMarkdown(html))
    expect(converted).toContain('<h2>标题</h2>')
    expect(converted).toContain('<strong>粗体</strong>')
    expect(converted).toContain('<u>下划线</u>')
    expect(converted).toContain('<table>')
  })

  it('removes dangerous HTML while retaining document markup', () => {
    const safe = sanitizeHtml('<p onclick="bad()">内容</p><script>alert(1)</script><a href="javascript:bad()">链接</a>')
    expect(safe).toBe('<p>内容</p><a>链接</a>')
  })
})

describe('editor metadata', () => {
  it('keeps safe math and comment attributes while removing event handlers', () => {
    const html = sanitizeHtml('<span data-type="inline-math" data-latex="E=mc^2" data-comment-id="7" onclick="alert(1)"></span>')
    expect(html).toContain('data-latex="E=mc^2"')
    expect(html).toContain('data-comment-id="7"')
    expect(html).not.toContain('onclick')
  })
})
