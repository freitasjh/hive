# Segurança de Sistemas — Padrão Obrigatório (Hive AI)

> 🚨 **REGRA CRÍTICA:** Este documento é a **FONTE DA VERDADE** para segurança de sistemas no Hive AI — abrangendo **Web**, **API** e **Mobile**. Nenhuma decisão de segurança deve divergir deste padrão sem aprovação explícita do `security-engineer` e registro de exceção.

## Base de Referência (Frameworks)

| Framework | Aplicação |
|-----------|-----------|
| **OWASP Top 10 (2021)** | Linha de base para risco web/API |
| **OWASP ASVS 4.x** | Verificação de requisitos de segurança de aplicações (Níveis 1-3) |
| **OWASP API Security Top 10 (2023)** | Riscos específicos de APIs |
| **OWASP MASVS 2.x** | Padrão de verificação de segurança para mobile |
| **OWASP WSTG / MASTG** | Guias de teste (web e mobile) |
| **NIST SP 800-63B** | Gestão de credenciais e autenticação |
| **CWE Top 25** | Referência de fraquezas de software |

**Objetivo de conformidade:** Nível 2 ASVS para web/API; MASVS L2 (defense-in-depth) para mobile.

---

## OWASP Top 10 — Check por Categoria (Web/API)

### A01 — Broken Access Control (Falha de Controle de Acesso)
- Implementar controle de acesso **servidor-side** em cada método/rota — nunca confiar apenas no frontend.
- Denegar por padrão (`default-deny`); whitelist explícita de permissões.
- Verificar ownership/autorização em toda operação (anti-IDOR): usuário só acessa recursos que lhe pertencem ou que o RBAC conceda.
- Evitar `ObjectNotFoundException` genérica que vaze existência de recurso; usar mensagens uniformes.
- **Hive AI:** usar `@HasPermission("PERMISSION_NAME")` em todos os endpoints; validar grants por recurso (RBAC/ReBAC).

### A02 — Cryptographic Failures (Falhas Criptográficas)
- Proteger dados em trânsito: **TLS 1.2+ obrigatório** (HTTPS em produção).
- Dados sensíveis em repouso: criptografar com AES-256-GCM (dados em volume) ou Argon2id/bcrypt/SCrypt para senhas.
- **Nunca** armazenar senha em texto puro ou hash reversível (MD5/SHA1 sem salt).
- Usar algoritmos e curvas atuais (>= RSA-2048 / Ed25519 / P-256); proibir SHA-1, DES, RC4, MD5.
- JWT: assinar com **RS256/ES256** (nunca `none`); `alg` validado no servidor; `jti` para revogação.

### A03 — Injection (Injeção)
- **SQL:** usar JPQL/QueryDSL parametrizado ou `PreparedStatement`/`EntityManager` com parâmetros. **PROIBIDO** concatenação de string em queries nativas.
- Validar/escapar qualquer input em comandos OS, LDAP, XML (XXE), templating (SSTI).
- RabbitMQ: validar payloads antes de processar; nunca executar payload como código.
- Spring: usar `@Valid` + beans de validação (Bean Validation); jamais confiar em inputs não validados.

### A04 — Insecure Design (Design Inseguro)
- Threat modeling (STRIDE) antes de novas features com superfície de ataque.
- Rate limiting e quotas por usuário/IP em endpoints críticos (login, registro, upload, export).
- Sem dados sensíveis em URLs (`GET`) — usar `POST`/`PUT` para envio de dados.
- Limits de tamanho de payload e uploads; validação de tipos de arquivo.

### A05 — Security Misconfiguration (Má Configuração)
- Headers de segurança no web (ver seção Web).
- Sem credenciais default em produção; `spring.profiles` separados dev/prod.
- Desabilitar verbose errors em produção (stack traces) — usar mensagens genéricas + log estruturado.
- Atualizar dependências; verificar CVEs (OWASP Dependency-Check / `mvn` Snyk/OWASP plugin).
- Não expor endpoints administrativos (actuator, swagger) sem auth em produção.

### A06 — Vulnerable and Outdated Components (Componentes Vulneráveis)
- Auditar dependências continuamente (frontend `npm audit`, backend OWASP Dependency-Check).
- Nenhuma dependência com CVE conhecida **crítica/alta** sem justificativa e mitigação.
- Manter Java, Spring Boot, PostgreSQL, RabbitMQ e libs frontend em versões suportadas.

### A07 — Identification and Authentication Failures (Falhas de Identificação e Autenticação)
- MFA para acesso administrativo (quando suportado).
- Rate limiting em autenticação (prevenção brute-force); bloqueio/backoff após tentativas.
- Sessões JWT: expiração curta (access token) + refresh token rotativo; invalidar no logout.
- Sem credenciais em código, logs, URLs ou mensagens de erro.

### A08 — Software and Data Integrity Failures (Falhas de Integridade)
- Assinar artefatos/atualizações quando aplicável (SBO).
- RabbitMQ: garantias de delivery (`publisher confirms`, idempotência no consumidor).
- Validar assinatura de payloads recebidos de terceiros.
- Dependências imutáveis/pinned (lockfiles frontend, versões fixas Maven).

### A09 — Security Logging and Monitoring (Logging e Monitoramento)
- Logar eventos de segurança: login (sucesso/falha), logout, mudança de permissão, exclusão, acesso negado.
- **Nunca** logar segredos, tokens, senhas, PII.
- Logs com correlação (request id); retenção definida; alertas para anomalias (brute-force, spikes 403/401).

### A10 — Server-Side Request Forgery (SSRF)
- Validar/allowlist de URLs permitidas para qualquer fetch server-side.
- Bloquear IPs privados/metadata endpoints (`169.254.169.254`, 10.x, 172.16-31.x, 192.168.x).
- Não seguir redirecionamentos sem validação; DNS rebinding guard.

---

## OWASP API Security Top 10 (2023) — Destaques

| Risco | Regra Hive AI |
|-------|----------------|
| API1 — Broken Object Level Authorization | Verificar ownership em toda operação por ID (anti-IDOR). |
| API2 — Broken Authentication | Validação robusta de JWT, expiração, rotação de refresh. |
| API3 — Broken Object Property Level Authorization | Não expor campos sensíveis no DTO de resposta (mappers controlam projeção). |
| API4 — Unrestricted Resource Consumption | Paginação obrigatória, limits de tamanho, rate limiting. |
| API5 — Broken Function Level Authorization | `@HasPermission` em todos os endpoints (inclusive GET de listas). |
| API6 — Unrestricted Access to Sensitive Business Flows | Rate limiting em fluxos sensíveis (login, cadastro, export). |
| API7 — Server Side Request Forgery | Allowlist de URLs, bloqueio de IPs internos. |
| API8 — Security Misconfiguration | Headers, CORS restrito, sem informação de versão exposta. |
| API9 — Improper Inventory Management | Desativar endpoints/envs não usados; versionamento explícito (`/v1`). |
| API10 — Unsafe Consumption of APIs | Validar payloads de terceiros e do RabbitMQ. |

---

## Web (Frontend Vue 3 + PrimeVue)

### Security Headers (obrigatório em produção)
```
Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self' https://api.hive.dominio
X-Content-Type-Options: nosniff
X-Frame-Options: DENY (ou frame-ancestors via CSP)
Referrer-Policy: strict-origin-when-cross-origin
Strict-Transport-Security: max-age=31536000; includeSubDomains
Permissions-Policy: camera=(), microphone=(), geolocation=()
```

### XSS (reflected / stored / DOM)
- **Nunca** renderizar HTML não confiável com `v-html`. Usar text interpolation (`{{ }}`).
- Sanitizar qualquer conteúdo rich-text com biblioteca de sanitização (ex: DOMPurify) antes de `v-html`.
- Escapar dados do usuário em atributos; usar bindings Vue (`:value`, `:title`) — nunca concatenação de string HTML.
- CSP `script-src 'self'` como camada extra de defesa.

### CSRF
- Autenticação via **Bearer token** (não cookies de sessão) elimina vetor CSRF clássico.
- Se cookies forem usados: `SameSite=Lax/Strict` + CSRF token duplo.
- Proibir CORS com origem `*` em produção; allowlist de origens exatas.

### Frontend (Boas Práticas)
- **Nunca** armazenar segredos ou tokens em código-fonte (`localStorage` para access token é aceitável no Hive AI — jamais em variáveis de ambiente do build expostas ao client).
- Validar input no client (UX) MAS confiar apenas na validação server-side (segurança).
- Auditoria `npm audit` no CI; sem dependência com CVE alta sem mitigação.
- Nenhum dado sensível em `console.log` ou mensagens de erro exibidas.

---

## API (Java 21 + Spring Boot)

### Autenticação & Autorização
- Auth0 JWT: validar assinatura, `iss`, `aud`, `exp` no servidor (resource server).
- Todo endpoint protegido com `@HasPermission("PERMISSION_NAME")` — sem endpoint público sem justificativa (login/health).
- Injeção por construtor; nunca expor credenciais em propriedades versionadas.
- Variáveis de ambiente para segredos (`JWT_TOKEN`, `DB_PASSWORD` etc.) — nunca hardcoded.

### Validação de Entrada (Servidor)
- Bean Validation (`@Valid`) em todo DTO de entrada: tipos, tamanhos, formatos, ranges.
- Whitelist/encode em queries especiais; parâmetros JPQL sempre vinculados.
- Erros de validação: mensagens genéricas (sem stack trace interno), status correto (400/406 conforme projeto).

### Proteção de Dados
- DTOs de resposta com projeção explícita — nunca serializar entidade JPA completa (vaza campos sensíveis).
- Mascarar dados sensíveis em logs (CPF, e-mail, tokens).
- `dateCreated`/`dateUpdated` automáticos — não confiar em timestamps do client.

### Configuração do Framework
- `spring.jpa.hibernate.ddl-auto=none` (Flyway gerencia schema — evita criação automática insegura).
- Actuator: expor somente endpoints seguros/autenticados em produção.
- Swagger/OpenAPI: restrito em produção ou protegido por auth.
- `server.error.include-stacktrace=never` em produção.
- RabbitMQ: credenciais via env, TLS para conexões externas, filas com `durable` + idempotência de consumidor.

---

## Mobile (quando aplicável — MASVS)

> Aplicável se o Hive AI tiver/ganhar client mobile. Aplicar MASVS L2.

### Armazenamento Local
- **Nunca** armazenar tokens de forma insegura — usar Keychain (iOS) / Keystore (Android) para refresh token.
- Não persistir dados sensíveis em logs, backups, SharedPreferences/UserDefaults sem criptografia.
- Criptografar dados em repouso local com chave protegida por hardware quando disponível.

### Comunicação
- TLS obrigatório; **proibido** cleartext (ATS iOS / `usesCleartextTraffic=false` Android).
- Certificate pinning para APIs críticas (opcional, balanceado com custo operacional).
- Validar certificados; rejeitar auto-assinados em produção.

### Autenticação & Sessão
- Biometria opcional via API nativa (nunca armazenar credenciais para bypass total).
- Sessão expirável; reautenticação para ações sensíveis.
- Clipboard: limpar/evitar copiar dados sensíveis; impedir screenshots em telas sensíveis (quando exigido).

### Código & Distribuição
- Proguard/R8 (Android) e obfuscation (iOS) em release.
- Sem segredos no app (chaves de API, endpoints de admin).
- Assinatura de builds; verificação de integridade (code signing).

---

## Secrets & Credenciais (Todas as Plataformas)

- Segredos em variáveis de ambiente ou cofre (Vault/Secret Manager) — **NUNCA** em código, propriedades versionadas ou frontend.
- Rotação periódica de segredos; nunca reutilizar secrets entre ambientes.
- `application.properties`: apenas placeholders (`${ENV_VAR}`); valores em `.env` local ignorado pelo git.
- `.gitignore` cobre `.env`, `.properties` locais, keystores.
- **Nunca commitar** `JWT_TOKEN`, `DB_PASSWORD`, chaves de API. Se vazar: revogar e rotacionar imediatamente.

---

## Ferramentas Obrigatórias (CI / Verificação)

| Camada | Ferramenta | Quando |
|--------|-----------|--------|
| Backend deps | OWASP Dependency-Check / Snyk Maven | Todo build/CI |
| Frontend deps | `npm audit` / `npm outdated` | Todo build/CI |
| SAST Java | SpotBugs + FindSecBugs | CI |
| SAST Frontend | ESLint com plugins security | CI |
| Container | Trivy (imagens Docker) | CI |
| Testes de segurança | OWASP ZAP (DAST) | Pré-release |
| Manual | `security-engineer` audit | Todo feature sensível |

**Gate mínimo:** zero CVE crítica/alta sem mitigação documentada; SAST sem findings críticos.

---

## Processo Obrigatório

1. **Toda feature/endpoint novo** com superfície de acesso: validar contra esta regra (checklist A01-A10).
2. **Threat modeling (STRIDE)** para features que envolvam dados sensíveis, upload, integração externa ou pagamento.
3. **Auditoria `security-engineer`** obrigatória em: autenticação, autorização, gestão de segredos, integrações externas.
4. **Testes:** incluir casos de segurança nos testes de integração (403/401, IDOR, injeção, payloads malformados).
5. **Registrar exceções** no Brain: `brain_store("arquitetura", "hive/security-exceptions/<motivo>", "...", scope="projetos")` com justificativa e risco aceito.

---

## Checklist de Segurança (Zero-Tolerance)

- [ ] Controle de acesso server-side em todos os endpoints (`@HasPermission`)?
- [ ] Input validation server-side em todo DTO (`@Valid`)?
- [ ] SQL/JPQL parametrizado — zero concatenação de strings em queries?
- [ ] Senhas com Argon2id/bcrypt — nunca texto puro ou hash fraco?
- [ ] JWT RS256/ES256 com validação de assinatura, `iss`, `aud`, `exp`?
- [ ] HTTPS/TLS 1.2+ em produção (web e API)?
- [ ] Security headers configurados (CSP, X-Content-Type-Options, etc.)?
- [ ] Zero `v-html` com conteúdo não sanitizado no frontend?
- [ ] Sem segredos em código versionado, logs ou respostas de API?
- [ ] Rate limiting em autenticação e endpoints sensíveis?
- [ ] Dependências auditadas (npm audit + OWASP Dependency-Check) — zero CVE alta sem mitigação?
- [ ] Actuator/Swagger protegidos em produção?
- [ ] Mobile (se aplicável): tokens em Keychain/Keystore, TLS obrigatório, sem cleartext?
