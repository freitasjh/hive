/**
 * Fixture HTTP somente para H3 (dev). Não é importado pela aplicação nem pelo build.
 * Uso: node scripts/h3-mock-api.mjs 8099
 *      VITE_HIVE_API_PROXY=http://localhost:8099 npm run dev -- --port 5183
 */
import { createServer } from 'node:http'

const port = Number(process.argv[2] ?? 8099)

const credentials = [
  { provider: 'OPENAI', configured: true, last4: '1234', baseUrl: null, updatedAt: '2026-11-19T10:00:00' },
  { provider: 'GEMINI', configured: false, last4: null, baseUrl: null, updatedAt: null },
  { provider: 'ANTHROPIC', configured: false, last4: null, baseUrl: null, updatedAt: null },
  { provider: 'OLLAMA', configured: true, last4: '8080', baseUrl: 'http://localhost:11434', updatedAt: '2026-11-18T10:00:00' },
  { provider: 'OPENCODE_GO', configured: false, last4: null, baseUrl: 'https://opencode.ai/zen/go/v1', updatedAt: null },
  { provider: 'OPENCODE_ZEN', configured: false, last4: null, baseUrl: 'https://opencode.ai/zen/v1', updatedAt: null },
  { provider: 'OPENAI_COMPATIBLE', configured: false, last4: null, baseUrl: null, updatedAt: null },
]

const llmConfig = {
  id: '22222222-2222-2222-2222-222222222222',
  tenantId: '33333333-3333-3333-3333-333333333333',
  provider: 'OPENAI',
  model: 'gpt-4o-mini',
  createAt: '2026-11-01T00:00:00Z',
  updateAt: '2026-11-19T00:00:00Z',
}

const prompts = [
  {
    id: '11111111-1111-1111-1111-111111111111',
    name: 'Saudação',
    code: 'greeting-v1',
    description: 'Prompt inicial de boas-vindas',
    tags: ['chat', 'boas-vindas'],
    template: 'Olá {{nome}}, bem-vindo ao {{produto}}!',
    params: ['nome', 'produto'],
    inferenceParams: { temperature: 0.7, topP: 0.9, maxTokens: 2048 },
    status: 'ACTIVE',
    version: 1,
    createAt: '2026-11-10T00:00:00Z',
    updateAt: '2026-11-19T00:00:00Z',
  },
]

function send(response, status, body) {
  const payload = body === undefined ? '' : JSON.stringify(body)
  response.writeHead(status, {
    'content-type': 'application/json',
    'content-length': Buffer.byteLength(payload),
  })
  response.end(payload)
}

function readBody(request) {
  return new Promise((resolve) => {
    let data = ''
    request.on('data', (chunk) => (data += chunk))
    request.on('end', () => resolve(data ? JSON.parse(data) : {}))
  })
}

createServer(async (request, response) => {
  const url = new URL(request.url ?? '/', `http://localhost:${port}`)
  const path = url.pathname.replace(/^\/hive\/v1/, '')

  if (request.headers.authorization === undefined) {
    // O fixture não valida token; a aplicação envia Bearer e o header é redactado no capture.
  }

  if (path === '/provider-credentials' && request.method === 'GET') return send(response, 200, credentials)

  if (path.startsWith('/provider-credentials/') && request.method === 'PUT') {
    const provider = path.split('/').pop()
    const index = credentials.findIndex((item) => item.provider === provider)
    if (index >= 0) credentials[index] = { ...credentials[index], configured: true, last4: '9999' }
    return send(response, 200, credentials[index] ?? { provider, configured: true, last4: '9999' })
  }

  if (path.startsWith('/provider-credentials/') && request.method === 'DELETE') {
    const provider = path.split('/').pop()
    const index = credentials.findIndex((item) => item.provider === provider)
    if (index >= 0) credentials[index] = { ...credentials[index], configured: false, last4: null }
    return send(response, 204)
  }

  if (path === '/tenant/llm-config' && request.method === 'GET') return send(response, 200, llmConfig)
  if (path === '/tenant/llm-config' && request.method === 'PUT') return send(response, 200, llmConfig)

  if (path === '/providers/validate' && request.method === 'POST') {
    return send(response, 200, { ok: true, provider: 'OPENAI', model: 'gpt-4o-mini', latencyMs: 42, reason: null })
  }

  if (path === '/providers/go/models' || path === '/providers/zen/models') {
    return send(response, 200, {
      models: path.includes('/go/') ? ['mimo-v2.5', 'mimo-v2.5-preview'] : ['claude-sonnet-4-5'],
      filteredOut: [],
      cachedAt: '2026-11-19T10:00:00Z',
    })
  }

  if (path === '/prompt-templates' && request.method === 'GET') {
    const page = Number(url.searchParams.get('page') ?? 0)
    const size = Number(url.searchParams.get('size') ?? 20)
    const q = (url.searchParams.get('q') ?? '').toLowerCase()
    const status = url.searchParams.get('status')
    const filtered = prompts.filter(
      (item) =>
        (!q || item.name.toLowerCase().includes(q) || item.code.toLowerCase().includes(q)) &&
        (!status || item.status === status)
    )
    return send(response, 200, {
      content: filtered.slice(page * size, page * size + size),
      page: { size, number: page, totalElements: filtered.length, totalPages: Math.max(1, Math.ceil(filtered.length / size)) },
    })
  }

  if (path === '/prompt-templates' && request.method === 'POST') return send(response, 201, prompts[0])

  if (path.startsWith('/prompt-templates/') && request.method === 'GET') {
    return send(response, 200, prompts[0])
  }
  if (path.startsWith('/prompt-templates/') && request.method === 'PUT') {
    return send(response, 200, { ...prompts[0], ...(await readBody(request)) })
  }
  if (path.startsWith('/prompt-templates/') && request.method === 'DELETE') return send(response, 204)

  return send(response, 404, { status: 404, code: 'NOT_FOUND', detail: 'Fixture sem rota para ' + path })
}).listen(port, '127.0.0.1', () => {
  process.stdout.write(`H3 mock API na porta ${port}\n`)
})
