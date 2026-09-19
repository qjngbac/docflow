import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const projectRoot = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  '..',
)

const serverRoot = path.join(
  projectRoot,
  'node_modules',
  '@hocuspocus',
  'server',
)

const packageJsonPath = path.join(serverRoot, 'package.json')
const distDir = path.join(serverRoot, 'dist')

const files = [
  'hocuspocus-server.esm.js',
  'hocuspocus-server.cjs',
]

const brokenPattern =
  /(this\.httpServer\.listen\(\{\s*port:\s*this\.configuration\.port,\s*)address(:\s*this\.configuration\.address\s*\})/

const fixedPattern =
  /(this\.httpServer\.listen\(\{\s*port:\s*this\.configuration\.port,\s*)host(:\s*this\.configuration\.address\s*\})/

if (!fs.existsSync(packageJsonPath)) {
  throw new Error(
    '@hocuspocus/server is not installed. Run npm install or npm ci first.',
  )
}

const packageJson = JSON.parse(
  fs.readFileSync(packageJsonPath, 'utf8'),
)

console.log(
  `[hocuspocus-patch] checking @hocuspocus/server ${packageJson.version}`,
)

for (const fileName of files) {
  const filePath = path.join(distDir, fileName)

  if (!fs.existsSync(filePath)) {
    throw new Error(
      `[hocuspocus-patch] expected file not found: ${filePath}`,
    )
  }

  const source = fs.readFileSync(filePath, 'utf8')

  const brokenMatches = source.match(
    new RegExp(brokenPattern.source, 'g'),
  ) ?? []

  const fixedMatches = source.match(
    new RegExp(fixedPattern.source, 'g'),
  ) ?? []

  if (brokenMatches.length === 1 && fixedMatches.length === 0) {
    const patched = source.replace(
      brokenPattern,
      '$1host$2',
    )

    fs.writeFileSync(filePath, patched, 'utf8')

    console.log(`[hocuspocus-patch] patched ${fileName}`)
    continue
  }

  if (brokenMatches.length === 0 && fixedMatches.length === 1) {
    console.log(
      `[hocuspocus-patch] already patched ${fileName}`,
    )
    continue
  }

  throw new Error(
    `[hocuspocus-patch] unexpected @hocuspocus/server listen layout in ${fileName}: ` +
      `broken=${brokenMatches.length}, fixed=${fixedMatches.length}. ` +
      'Dependency contents may have changed; review before deployment.',
  )
}

console.log('[hocuspocus-patch] completed successfully')