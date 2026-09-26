# Bugfix — Cadastro de Usuários (registration)

> Workflow: Bugfix Fast-Track — Fase B1 Análise
> Severidade: 🔴 Crítica
> Camada: **Fullstack** (frontend + backend)
> Módulo: `identity` (api/impl/web) + `frontend/src/views/Register.vue` + `AuthLayout.vue`
> Data: 2026-08-26

---

## Bug

O fluxo de cadastro apresenta falhas críticas que impedem conclusão do registro e quebram experiência:

1. **Campos ausentes**: `Register.vue` renderiza apenas `email`, `password`, `confirmPassword` (3 campos). O domínio `UserAccount` exige `name`, `username`, `password`, `email`, `document` (5 campos) — `name` derivado de `email.split('@')[0]` de forma frágil, `username` e `document` nunca enviados.
2. **Validação inconsistente**: DTO `UserAccountCreateDTO` sem Bean Validation (`@NotBlank`/`@Size`/`@Email`); serviço `UserServiceImpl.validateSaveNewUser` valida apenas `password` e `username/email`, ignora `name`/`document`; erros retornam `UserUsernameOrEmailExistException` etc. sem contrato RFC 7807 consistente nem mensagens descritivas expostas ao frontend.
3. **Feedback quebrado**: frontend usa `alert('As senhas não coincidem')` e `console.error` — sem Toast/Message, sem sinalização campo, sem disabled duplo-clique efetivo, sem máscaras.
4. **Responsividade ausente**: `Register.vue` usa `flex flex-column gap-4` puro sem grid; `AuthLayout` card `w-full sm:w-10 md:w-8 lg:w-4 xl:w-3` fica estreito em lg/xl (25% ≈ 300px) — 2 colunas em desktop impossíveis; sobreposição e rolagem horizontal em resoluções intermediárias; sem breakpoints `md:col-6`.

**Impacto**: nenhum registro completo persiste; dados parciais/inconsistentes podem ser gravados; mobile/tablet perdem legibilidade.

---

## Causa Raiz

| Camada | Arquivo:linha | Causa |
|--------|---------------|-------|
| **Frontend** | `frontend/src/views/Register.vue:1-30` | Template hardcoded 3 campos; ausência de `name`, `username`, `document`; `InputText` sem `autocomplete`, sem `aria-invalid`, sem `p-invalid`; `Button` com `:loading` mas sem `:disabled` duplo-clique; grid sem `formgrid`/`col-12 md:col-6` |
| **Frontend** | `Register.vue:48-66` | `handleRegister` deriva `name: email.split('@')[0]` — nome incompleto; não envia `username`/`document`; valida senha só com `alert`; catch só `console.error` sem `useToast`; sem validação inline onBlur; sem máscara CPF |
| **Frontend** | `frontend/src/components/AuthLayout.vue:3` | `lg:w-4 xl:w-3` estreito para 6 campos; deveria `lg:w-5 xl:w-4` para caber grid 2 colunas |
| **Backend DTO** | `identity/web/internal/v1/dto/UserAccountCreateDTO.java:3-8` | `record(String name,username,password,email,document)` **sem** `jakarta.validation.constraints.*` — nenhuma `@NotBlank`/`@Email`/`@Size`; `@Valid` no controller existe mas não valida nada |
| **Backend Service** | `identity/impl/service/UserServiceImpl.java:81-98` | `validateSaveNewUser` só checa `password` e `username/email`, ignora `name`/`document`; `username == null && email.blank` fallback `username=email` mascara falta de username; duplicidade só por `findByUsernameOrEmail` (document duplicado não barrado) |
| **Backend Entity** | `identity/dbupdate/.../V2__create_table_user.sql` | `users` sem `NOT NULL`/`CHECK` em `first_name`/`username`/`email`/`federal_id` — permite registro parcial inconsistente |
| **Backend Mapper** | `identity/impl/mapper/UserAccountMapper.java:15-24` | `toUser` mapeia `username`/`document` que nunca chegam (null) — propaga vazio |
| **Backend Controller** | `identity/web/internal/v1/controller/UserAccountControllerV1.java:51` | `@Valid` presente mas DTO sem constraints → 400 nunca disparado para `name`/`document` ausentes; contrato erro não-RFC7807 consistente (handler não mapeia UsernameOrEmailExist → 409 descritivo) |
| **Cross** | `Register.vue` ↔ `UserAccountControllerV1` | Contrato quebrado: frontend envia `POST /user-accounts/create {email,password,name}` sem `username/document`; backend aceita `null` e persiste parcial |

**Dependência herança**: `UserEntity` estende `AbstractEntity` (tenant filter), mas `UserServiceImpl.findByUsernameOrEmail` precisa verificar tenant? Anti-duplicidade hoje é global, não por tenant — aceitável para registro inicial (tenant ainda não existe).

---

## Solução Proposta

### 1. Frontend — Interface & Validação Visual (com parecer vinculante `ux-designer`)

**Arquivos**:
- `frontend/src/views/Register.vue` — **rewrite** para 6 campos + grid responsivo + validação inline + Toast + Password toggle + disabled
- `frontend/src/components/AuthLayout.vue` — ajuste `lg:w-5 xl:w-4` para 2 colunas
- `frontend/src/router/index.ts` — verificar guard `/register` permitAll (já em `SecurityConfig`)
- `frontend/vite.config.ts` / `vitest` — manter

**Design vinculante (ux-designer → fonte da verdade)**:
- **Grid**: `formgrid grid` + `col-12 md:col-6` — mobile 1 coluna (<768px), desktop 2 colunas (Nome+Username, Email+Documento, Senha+Confirmar). Ver SPEC ux §4.1-4.2.
- **Campos** (todos `*` obrigatórios): `Nome Completo` (`autocomplete=name`, min 2 chars), `Username` (`autocomplete=username`, min 3, `^[a-zA-Z0-9._]+$`, helper "Será usado para login"), `Email` (`autocomplete=email`, regex), `Documento CPF/CNPJ` (máscara `000.000.000-00`, `autocomplete=off`), `Senha` (`autocomplete=new-password`, toggle 👁, strength meter fraco/médio/forte, min 8), `Confirmar Senha` (inline `✓ coincide`/`✗ não coincide`).
- **Validação**: inline `onBlur` por campo + `onSubmit` foca primeiro inválido; `aria-required`, `aria-invalid`, `role=alert` nos `<small class="text-red-500">`; `p-invalid` em `InputText`.
- **Estados**: `Button :loading + :disabled` durante request (previne duplo clique); `Toast` `top-right` success (`Conta criada! Redirecionando... 2s`) e error (400/409/Network com messages funcionais §8.1); sem `alert`/`console.error`.
- **Tokens Pulse**: `surface-card`, `var(--p-primary-900→700)`, `text-900` labels, `text-red-500` errors, `text-green-500` success, `text-surface-500` helper; sem Lombok.
- Ver `docs/design/DESIGN_SYSTEM.md` Deep Indigo + Amber.

**Behaviour**:
```ts
reactive form {name,username,email,document,password,confirmPassword}
reactive errors {name,username,email,document,password,confirmPassword}
onBlur validateField(field) → errors[field]
onSubmit: validateAll → hasErrors? focus first invalid : loading=true → api.post('/user-accounts/create', {name,username,email,password,document}) → toast success → setTimeout router.push('/login',2000) catch→ toast error (409 email/username/document, 400 senha, 500 genérico) finally loading=false
```

### 2. Backend — Validação Integridade & Contrato Erro

**Arquivos**:
- `identity/web/internal/v1/dto/UserAccountCreateDTO.java` — adicionar Bean Validation: `@NotBlank @Size(min=2,max=80) name`, `@NotBlank @Size(min=3,max=40) @Pattern(regexp="^[a-zA-Z0-9._]+$") username`, `@NotBlank @Email @Size(max=255) email`, `@NotBlank @Size(min=8,max=64) password`, `@NotBlank @Size(min=11,max=18) document` (ou `@Pattern` CPF/CNPJ se desejar rigor, mas MVP `@NotBlank` basta). Opcional: `@Size` password strength.
- `identity/api/domain/UserAccount.java` — manter VO; não JPA; manual getters/setters.
- `identity/impl/service/UserServiceImpl.java:81-98` — estender `validateSaveNewUser` para checar `name` notBlank (→ `UsernameNotProvideInformationException` ou nova `UserNameNotProvidedException`) e `document` notBlank; validar `email` formato `@Email`; checar duplicidade `document` via `findByDocument` se repo tiver (ou adicionar `findByDocument`/`findByUsernameOrEmailOrDocument`); manter `@Transactional` em `create`.
- `identity/impl/repository/UserRepository.java` — adicionar `Optional<UserEntity> findByDocument(String document)` e `Optional<UserEntity> findByUsernameOrEmailOrDocument(String username, String email, String document)` se necessário para unicidade document; anotar `@Repository`, JPQL parametrizado.
- `identity/impl/mapper/UserAccountMapper.java` — já mapeia `document`; garantir `toUser`/`toTenant` não perde campos.
- `identity/web/internal/v1/mapper/UserAccountMapperDTO.java` — MapStruct `@Mapper(componentModel="spring")` já; garantir mapeia todos 5 campos (name,username,password,email,document) ↔ UserAccount.
- `commons/api-tools/.../exceptions/ControllerExceptionHandler.java` — garantir mapeia `UserUsernameOrEmailExistException` → 409 Conflict com RFC 7807 `problem+json`, `MethodArgumentNotValidException` → 400, `AccessDenied` 403 etc. Se não existir handler, adicionar.
- `identity/dbupdate` — **não criar migration V3 agora** (users sem NOT NULL já flexível); documentar débito de constraints DB para próxima iteração (hardening). Se criar, adicionar `NOT NULL` checks como débito não-bloqueante.

**Contrato erro**:
- `POST /v1/user-accounts/create` com `name: ""` → 400 `{"type":"about:blank","title":"Bad Request","status":400,"detail":"name: must not be blank",...}`
- `email` duplicado → 409 `{"status":409,"detail":"Email já cadastrado"}`
- `username` duplicado → 409 `{"detail":"Username já existe"}`
- `document` duplicado → 409 `{"detail":"Documento já cadastrado"}`
- `password <8` → 400 `{"detail":"password: size must be between 8 and 64"}`
- Nenhum registro parcial persiste em 400/409 (validação antes de `repository.save`).

### 3. Testes

**Backend**:
- `identity/impl/src/test/java/.../UserAccountServiceImplTest.java` — já existe, estender: `when_create_with_missingName_then_throwException`, `when_create_with_shortPassword_then_400`, `when_create_with_duplicateDocument_then_throwConflict`, `when_create_with_validAllFields_then_success`.
- `identity/impl/src/test/java/.../UserServiceImplTest.java` — similar para name/document validation.
- `identity/web/.../UserAccountControllerV1Test` (MockMvc) — novo ou `IT`: `when_create_with_missingName_then_400`, `when_create_with_invalidEmail_then_400`, `when_create_with_duplicate_then_409`, `when_create_with_valid_then_204`.
- **Naming**: `when_{action}_with_{scenario}_then_{result}()`; Mockito + AssertJ; `@ExtendWith(MockitoExtension.class)`.

**Frontend**:
- `frontend/src/views/__tests__/Register.spec.ts` — novo com `vitest` + `vue-test-utils`: render 6 campos `*`, responsividade classes `col-12 md:col-6`/`formgrid grid`, validação inline (blur → error), confirm mismatch inline, disabled+loading previne duplo clique, Toast success/error, `api.post` payload com 5 campos.
- **Critério**: `npm run build` (vue-tsc + vite) verde; `npm run test:run` verde.

### 4. Harness (R1 obrigatório)

- **Antes de codar** (B1 já em análise): `qa-engineer` sobe `backend :8080` + `frontend :5173`, Chrome DevTools `new_page` → `/register`, `take_snapshot` (6 campos? grid? labels), `list_network_requests` (POST /user-accounts/create 400/409 vs 204), `list_console_messages` (zero [ERROR]), `take_screenshot`.
- **Após fix** (B3): re-run harness na mesma URL, validar 6 campos, grid responsivo (resize 375/768/1024), Toast, 201/204, sem console errors.

---

## Testes Necessários

| Camada | Arquivo de teste | Casos | Ferramenta |
|--------|------------------|-------|------------|
| Backend unit | `UserAccountServiceImplTest` + `UserServiceImplTest` | happy all fields, missing name 400, missing username 400, invalid email 400, short password 400, duplicate email 409, duplicate username 409, duplicate document 409 | JUnit5 + Mockito + AssertJ |
| Backend integration | `UserAccountControllerV1IT` (ou MockMvc unit) | 400 missing, 400 invalid, 409 duplicate, 204 success | MockMvc + Testcontainers pgvector:pg16 (se módulo integration-test existir, senão MockMvc puro) |
| Frontend | `Register.spec.ts` | 6 campos + `*` required, grid `formgrid`, inline errors, confirm match, disabled loading, Toast, payload 5 campos | Vitest + vue-test-utils + happy-dom |

---

## Riscos

| Risco | Impacto | Mitigação |
|-------|---------|-----------|
| Frontend grid quebra layout `Login.vue`/`AuthLayout.vue` reaproveitado | Médio | Alterar só `Register.vue` form + `AuthLayout` `lg:w-5` (não afeta Login além de largura card — validar Login não quebrado via snapshot) |
| Backend `@NotBlank` em DTO quebra payloads antigos `integration-test` existentes | Baixo | Atualizar testes existentes `UserAccountServiceImplTest` que usam mock sem name/document; adicionar campos missing nos fixtures |
| Document duplicado sem índice DB | Médio | Adicionar `findByDocument` em repo; sem migration V3, risco de race condition — aceitável para bugfix, hardening depois |
| Duplo clique race condition | Baixo | `loading` + `:disabled` no Button cobre; `finally loading=false` garante re-habilitação em erro |
| Regressão outros fluxos auth (`/auth/login`) | Baixo | Não alterar `SecurityConfig` além de já permitAll `/user-accounts/create`; não tocar `Login.vue` logic |

---

## Critérios de Aceite (bugfix)

- [ ] Tela adapta mobile/tablet/desktop (grid `col-12 md:col-6`, card `lg:w-5 xl:w-4`, sem sobreposição, sem scroll horizontal)
- [ ] 6 campos presentes com `*` e validados inline + submit (name 2-80, username 3-40 pattern, email, document 11-18, password 8-64, confirm match)
- [ ] Cadastro com dados válidos persiste (User + Tenant, 204, redirect /login, Toast success)
- [ ] Dados faltantes/duplicados/inválidos bloqueados com 400/409 RFC7807 e Toast/Message claros, sem registro parcial
- [ ] Nenhuma regressão visual/funcional em Login/outros fluxos
- [ ] `mvn test -pl identity/impl -am` + `mvn test` + `npm run build` + `npm run test:run` verdes; harness Chrome DevTools 0 errors, network 2xx/400/409 conforme

---

## Checklist Harness & Persistência (R1-R4)

- [ ] Harness-First: backend+frontend UP, Chrome DevTools snapshot/network/console/screenshot antes e depois do fix
- [ ] Encerramento limpo: processos :8080/:5173 finalizados após testes
- [ ] Análise detalhada antes dev (este arquivo) — causa raiz arquivo:linha, camada, módulo
- [ ] Brain persistência `scope=projetos` para erro específico + `scope=global` para regra stack

