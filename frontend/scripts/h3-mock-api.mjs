/**
 * Fixture HTTP somente para H3 (dev). Não é importado pela aplicação nem pelo build.
 * Uso: node scripts/h3-mock-api.mjs 8099
 *      VITE_HIVE_API_PROXY=http://localhost:8099 npm run dev -- --port 5183
 *
 * Sessão humana (ADR-003): o fixture implementa POST /auth/login devolvendo um
 * JWT de development com claim `tenant-id` e `permissions` contendo
 * AGENT_MANAGE — o mesmo contrato que o front consome em auth.service.ts.
 * Basta entrar na UI com qualquer usuário/senha. Regras do fixture:
 *   - sem header Authorization → 401 (problem+json UNAUTHENTICATED);
 *   - Bearer com claim `permissions` explícito sem AGENT_MANAGE → 403 (FORBIDDEN);
 *   - Bearer sem claim de permissões (token opaco de dev) → AGENT_MANAGE implícito,
 *     mantendo o comportamento permissivo que este fixture já tinha.
 * Para exercitar 403 sem sair da UI: trocar `hive_token` no localStorage por um
 * JWT com `permissions: []` (o claim `tenant-id` deve permanecer UUID válido).
 *
 * Clients públicos: estado EM MEMÓRIA durante a sessão do browser; criar →
 * listar → rotacionar → revogar funciona em sequência. `client_secret` existe
 * somente no corpo de POST /public-api/clients (201) e POST /{id}/rotate (200);
 * nunca é armazenado, logado ou devolvido em list/get. Erros usam
 * application/problem+json (status, detail, code).
 */
import { createServer } from 'node:http'
import { randomBytes, randomUUID } from 'node:crypto'

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

/* -------------------------- ADR-003 / clients ----------------------------- */

const DEFAULT_TENANT = llmConfig.tenantId
const ADMIN_PERMISSIONS = ['AGENT_MANAGE', 'CHAT_SEND', 'PROMPT_MANAGE', 'PROMPT_ADMIN']

/** Semente: um ativo com janela de secret anterior e um revogado/expirado. */
const publicClients = [
  {
    clientId: 'c1111111-1111-4111-8111-111111111111',
    label: 'Chat do site',
    tenantId: DEFAULT_TENANT,
    grants: ['PUBLIC_CHAT_COMPLETE'],
    prompts: [{ promptCode: 'greeting-v1', pinMode: 'PINNED', pinnedVersion: 1 }],
    token_ttl_seconds: 900,
    max_messages: 20,
    max_message_chars: 4000,
    max_total_chars: 20000,
    revoked_at: null,
    expires_at: null,
    previous_secret_expires_at: new Date(Date.now() + 10 * 60_000).toISOString(),
  },
  {
    clientId: 'c2222222-2222-4222-8222-222222222222',
    label: 'Integração parceira (revogada)',
    tenantId: DEFAULT_TENANT,
    grants: ['PUBLIC_CHAT_COMPLETE'],
    prompts: [{ promptCode: 'greeting-v1', pinMode: 'FOLLOW_ACTIVE', pinnedVersion: null }],
    token_ttl_seconds: 600,
    max_messages: 5,
    max_message_chars: 1000,
    max_total_chars: 5000,
    revoked_at: '2026-11-20T12:00:00Z',
    expires_at: '2026-11-21T00:00:00Z',
    previous_secret_expires_at: null,
  },
]

/** Campos públicos espelham PublicApiClientResponseDTO — sem secret e sem hash. */
function publicView(client) {
  return {
    clientId: client.clientId,
    label: client.label,
    tenantId: client.tenantId,
    grants: [...client.grants],
    prompts: client.prompts.map((prompt) => ({ ...prompt })),
    token_ttl_seconds: client.token_ttl_seconds,
    max_messages: client.max_messages,
    max_message_chars: client.max_message_chars,
    max_total_chars: client.max_total_chars,
    revoked_at: client.revoked_at,
    expires_at: client.expires_at,
    previous_secret_expires_at: client.previous_secret_expires_at,
  }
}

function send(response, status, body) {
  if (status === 204) {
    response.writeHead(204)
    return response.end()
  }
  const payload = body === undefined ? '' : JSON.stringify(body)
  response.writeHead(status, {
    'content-type': 'application/json',
    'content-length': Buffer.byteLength(payload),
  })
  response.end(payload)
}

function problem(response, status, code, detail) {
  const payload = JSON.stringify({ status, code, detail })
  response.writeHead(status, {
    'content-type': 'application/problem+json',
    'content-length': Buffer.byteLength(payload),
  })
  response.end(payload)
}

function readBody(request) {
  return new Promise((resolve) => {
    let data = ''
    request.on('data', (chunk) => (data += chunk))
    request.on('end', () => {
      try {
        resolve(data ? JSON.parse(data) : {})
      } catch {
        resolve({})
      }
    })
  })
}

function encodeSegment(value) {
  return Buffer.from(JSON.stringify(value)).toString('base64url')
}

function issueToken(tenantId = DEFAULT_TENANT, permissions = ADMIN_PERMISSIONS) {
  const header = encodeSegment({ alg: 'none', typ: 'JWT' })
  const payload = encodeSegment({
    sub: 'h3-admin',
    'tenant-id': tenantId,
    permissions,
    exp: 4102444800,
  })
  return `${header}.${payload}.h3mock`
}

function decodeClaims(request) {
  const authorization = request.headers.authorization
  if (typeof authorization !== 'string' || !authorization.startsWith('Bearer ')) return null
  const segment = authorization.slice(7).trim().split('.')[1]
  if (!segment) return {}
  try {
    const base64 = segment.replace(/-/g, '+').replace(/_/g, '/')
    const parsed = JSON.parse(Buffer.from(base64, 'base64').toString('utf8'))
    return parsed && typeof parsed === 'object' ? parsed : {}
  } catch {
    return {}
  }
}

/** Sessão humana: 401 sem Bearer, 403 sem AGENT_MANAGE explícito. */
function humanSession(request, response) {
  const claims = decodeClaims(request)
  if (claims === null) {
    problem(response, 401, 'UNAUTHENTICATED', 'Sessão humana necessária.')
    return null
  }
  const permissions = claims.permissions
  if (Array.isArray(permissions) && !permissions.includes('AGENT_MANAGE')) {
    problem(response, 403, 'FORBIDDEN', 'Permissão AGENT_MANAGE necessária.')
    return null
  }
  const tenantId = typeof claims['tenant-id'] === 'string' ? claims['tenant-id'] : DEFAULT_TENANT
  return { tenantId, permissions: Array.isArray(permissions) ? permissions : ADMIN_PERMISSIONS }
}

function findClient(clientId, tenantId) {
  return publicClients.find((item) => item.clientId === clientId && item.tenantId === tenantId)
}

function normalizeGrants(value) {
  return Array.isArray(value) ? value.filter((grant) => typeof grant === 'string') : []
}

function normalizePrompts(value) {
  if (!Array.isArray(value)) return []
  return value
    .filter((prompt) => prompt && typeof prompt === 'object' && typeof prompt.promptCode === 'string')
    .map((prompt) => ({
      promptCode: prompt.promptCode,
      pinMode: prompt.pinMode === 'PINNED' ? 'PINNED' : 'FOLLOW_ACTIVE',
      pinnedVersion:
        prompt.pinMode === 'PINNED' && Number.isInteger(prompt.pinnedVersion)
          ? prompt.pinnedVersion
          : null,
    }))
}

function oneShotSecret() {
  return `h3_sk_${randomBytes(18).toString('hex')}`
}

async function handlePublicClients(request, response, url, match) {
  const session = humanSession(request, response)
  if (!session) return
  const { tenantId } = session
  const clientId = match ? decodeURIComponent(match[1]) : null
  const action = match?.[2] ?? null

  if (!match && request.method === 'GET') {
    const page = Number(url.searchParams.get('page') ?? 0)
    const size = Number(url.searchParams.get('size') ?? 20)
    if (!Number.isInteger(page) || page < 0 || !Number.isInteger(size) || size < 1 || size > 100) {
      return problem(response, 400, 'INVALID_PAGE', 'page >= 0 e 1 <= size <= 100')
    }
    const items = publicClients.filter((item) => item.tenantId === tenantId)
    return send(response, 200, {
      content: items.slice(page * size, page * size + size).map(publicView),
      page,
      size,
      totalElements: items.length,
      totalPages: Math.max(1, Math.ceil(items.length / size)),
    })
  }

  if (!match && request.method === 'POST') {
    const body = await readBody(request)
    if (typeof body.label !== 'string' || !body.label.trim()) {
      return problem(response, 400, 'VALIDATION', 'label é obrigatório.')
    }
    if ('clientId' in body || 'tenantId' in body || 'rate_limit_per_minute' in body) {
      return problem(
        response,
        400,
        'VALIDATION',
        'clientId, tenantId e rate_limit_per_minute são gerados pelo servidor.'
      )
    }
    const label = body.label.trim()
    if (publicClients.some((item) => item.tenantId === tenantId && item.label === label)) {
      return problem(response, 409, 'DUPLICATE_CLIENT_LABEL', 'Já existe client com este label no tenant.')
    }
    const client = {
      clientId: randomUUID(),
      label,
      tenantId,
      grants: normalizeGrants(body.grants),
      prompts: normalizePrompts(body.prompts),
      token_ttl_seconds: Number.isInteger(body.tokenTtlSeconds) ? body.tokenTtlSeconds : 900,
      max_messages: Number.isInteger(body.maxMessages) ? body.maxMessages : 20,
      max_message_chars: Number.isInteger(body.maxMessageChars) ? body.maxMessageChars : 4000,
      max_total_chars: Number.isInteger(body.maxTotalChars) ? body.maxTotalChars : 20000,
      revoked_at: null,
      expires_at: null,
      previous_secret_expires_at: null,
    }
    publicClients.push(client)
    // Plaintext one-shot: sai daqui e nunca é guardado em `client`.
    return send(response, 201, { ...publicView(client), client_secret: oneShotSecret() })
  }

  const client = findClient(clientId, tenantId)
  if (!client) {
    return problem(response, 404, 'CLIENT_NOT_FOUND', 'Client não encontrado para o tenant da sessão.')
  }

  if (!action && request.method === 'GET') return send(response, 200, publicView(client))

  if (action === 'rotate' && request.method === 'POST') {
    const previousSecretExpiresAt = new Date(Date.now() + 10 * 60_000).toISOString()
    client.previous_secret_expires_at = previousSecretExpiresAt
    // Só o timestamp da janela é persistido; plaintext não entra em `client`.
    return send(response, 200, {
      clientId: client.clientId,
      client_secret: oneShotSecret(),
      previous_secret_expires_at: previousSecretExpiresAt,
    })
  }

  if (action === 'revoke' && request.method === 'POST') {
    client.revoked_at = new Date().toISOString()
    return send(response, 204)
  }

  if (action === 'grants' && request.method === 'PUT') {
    const body = await readBody(request)
    if (!Array.isArray(body.grants)) return problem(response, 400, 'VALIDATION', 'grants deve ser uma lista.')
    client.grants = normalizeGrants(body.grants)
    return send(response, 200, publicView(client))
  }

  if (action === 'prompts' && request.method === 'PUT') {
    const body = await readBody(request)
    if (!Array.isArray(body.prompts)) return problem(response, 400, 'VALIDATION', 'prompts deve ser uma lista.')
    client.prompts = normalizePrompts(body.prompts)
    return send(response, 200, publicView(client))
  }

  return problem(response, 405, 'METHOD_NOT_ALLOWED', 'Método não suportado para este recurso.')
}

createServer(async (request, response) => {
  const url = new URL(request.url ?? '/', `http://localhost:${port}`)
  const path = url.pathname.replace(/^\/hive\/v1/, '')

  // Login humano do fixture: qualquer credencial vira sessão com AGENT_MANAGE.
  if (path === '/auth/login' && request.method === 'POST') {
    await readBody(request) // corpo não é logado
    return send(response, 200, {
      userId: 'h3-admin',
      accessToken: issueToken(),
      type: 'Bearer',
      mfaSetupAvailable: false,
    })
  }
  if (path === '/auth/logout' && request.method === 'POST') return send(response, 204)
  if (path === '/auth/refresh-token' && request.method === 'POST') {
    const tenantId = request.headers['x-tenant-id']
    if (typeof tenantId !== 'string' || !tenantId) {
      return problem(response, 401, 'REFRESH_UNAVAILABLE', 'Tenant da sessão ausente.')
    }
    return send(response, 200, { accessToken: issueToken(tenantId) })
  }

  if (path === '/public-api/clients' || path.startsWith('/public-api/clients/')) {
    const match = /^\/public-api\/clients\/([^/]+)(?:\/(rotate|revoke|grants|prompts))?$/.exec(path)
    if (path === '/public-api/clients' || match) {
      return handlePublicClients(request, response, url, match)
    }
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
      cachedAt: '2026-11-19T00:00:00Z',
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
