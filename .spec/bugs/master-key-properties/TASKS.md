# BUG — `PROVIDER_MASTER_KEY` sem caminho declarativo: provider não salva sem env var no shell

- **Data:** 2026-09-26
- **Severidade:** 🔴 P0 (bloqueia o cadastro de provider; a tela de Provedores inteira cai)
- **Tipo:** backend / config / `agent/impl` converter
- **Origem:** reporte do usuário · reproduzido no cenário exato do usuário antes de codar
- **Decisão:** usuário aprovou (opção A) — properties + chave gerada + limpeza do banco

## Bug

Com o backend iniciado **sem** `PROVIDER_MASTER_KEY` exportada no shell:

```
PUT /hive/v1/provider-credentials/OPENCODE_GO  → 500 INTERNAL_ERROR
GET /hive/v1/provider-credentials               → 500  (tela inteira cai)
log: "Master key not configured. Set PROVIDER_MASTER_KEY (256-bit base64)."
```

Nenhum provider pode ser cadastrado. Sem a chave configurada, **toda** a feature GO/Zen fica
inutilizável — o usuário não consegue testar nada.

## Causa raiz

`backend/agent/impl/.../converter/AesGcmAttributeConverter.java:121-133` (`keySpec()`):

```java
String raw = System.getenv(MASTER_KEY_ENV);          // PROVIDER_MASTER_KEY
if (raw == null || raw.isBlank()) {
    raw = System.getProperty(MASTER_KEY_PROPERTY);   // provider.master.key (System property!)
}
if (raw == null || raw.isBlank()) {
    throw new IllegalStateException("Master key not configured. ...");
}
```

**`System.getProperty` não enxerga `application.properties`.** Propriedades Spring são carregadas
em `Environment`, não em `System.getProperty`. Portanto **não existe caminho suportado para
configurar a chave declarativamente** — a única opção é exportar a env var no shell que sobe o
backend. Qualquer pessoa que suba o projeto pelo README/IDE sem a variável exportada fica com o
app inutilizável, sem caminho de recuperação descrito.

Além disso, a chave é **efêmera por natureza**: cada shell nova gera uma chave diferente, e trocar
a chave torna **inrecuperável** qualquer credencial já gravada.

## Objetivo

Tornar a chave **configurável por properties** (dev sem atrito) mantendo **env var como caminho
seguro** para produção, sem nunca embutir segredo em produção.

## Solução

### 1. `ProviderMasterKey` — bean Spring (fonte única de verdade)
Novo componente (ex.: `agent/impl/.../security/ProviderMasterKey.java`) que resolve a chave uma vez:
- **Precedência 1:** env var `PROVIDER_MASTER_KEY` (mantém o caminho seguro/secret store).
- **Precedência 2:** propriedade Spring `provider.master.key`.
- **Sem nenhuma das duas:** em dev, **falhar no boot** com mensagem que diga exatamente o que fazer
  (nome da propriedade + como gerar: `openssl rand -base64 32`).
- Validar **na inicialização**, não a cada cifragem: base64 válido e exatamente 32 bytes.
- Cache do `SecretKeySpec` derived; **nunca** expor a chave em `toString`/log.
- Manter a API estática `AesGcmAttributeConverter.setTestKey()/clearTestKey()` (usada pelos testes)
  funcionando com precedência sobre tudo, e com o guard de perfil `test` atual.

### 2. `AesGcmAttributeConverter` passa a delegar ao bean
Remover a leitura direta de env/System property. O `AttributeConverter` deve receber a chave do
bean. Cuidado: `AttributeConverter` não é bean Spring por padrão — registrar via `@Converter`
em classe que o Spring Boot detecte, ou injetar o bean no converter via construtor, garantindo que
o Hibernate use a instância com a chave resolvida (**verificar no teste de integração que a
cifragem/decifragem realmente usa a chave do bean** — o modo de falha silencioso seria o
converter continuar usando a chave hardcoded anterior).

### 3. `application-dev.properties` — chave fixa de dev
```properties
# Chave mestra de CIFRAGEM das credenciais de provider (AES-256-GCM), 32 bytes base64.
# ATENÇÃO: valor de DESENVOLVIMENTO. Trocar esta chave torna as credenciais já gravadas
# IRECUPERÁVEIS — será preciso re-cadastrar as chaves dos providers.
# Produção NUNCA deve usar este valor: defina PROVIDER_MASTER_KEY no ambiente.
provider.master.key=myiU2GAGC3aSZkUgtybiGenOQ1gMSQIcfcwDyW/onTM=
```

### 4. Produção sem default
`application.properties` (perfil padrão) **não** define `provider.master.key`. Sem env var e sem
propriedade, a aplicação **falha no boot** com mensagem acionável — nunca em runtime no meio de uma
requisição. Isso converte o 500 difícil de diagnosticar em erro de boot imediato.

### 5. `CredentialMasterKeyStartupCheck` na mesma fonte
Passar a usar `ProviderMasterKey` (mesma resolução), mantendo:
- banner **uma vez**;
- **nunca** logar chave, parte dela ou ciphertext;
- erro de infra do repositório **não** pode ser reportado como chave divergente (os dois `try`
  separados que o developer já fez devem ser preservados).

## Testes

1. `ProviderMasterKeyTest`:
   - env var presente → usada
   - env var ausente + propriedade presente → propriedade usada
   - **env var tem precedência** sobre a propriedade
   - nenhuma das duas → `IllegalStateException` com mensagem que contenha `provider.master.key`
     e a instrução de gerar
   - base64 inválido → erro nomeando o formato
   - tamanho ≠ 32 bytes → erro nomeando 256-bit
   - a chave nunca aparece na `toString()`/log
2. **IT decisivo** (`CredentialIT` ou novo): gravar uma credencial via API **sem** env var,
   com apenas a propriedade do profile, e reler — se passar, o Hibernate está usando o bean.
   Este é o teste que pega o modo de falha silencioso do item 2.
3. `AesGcmConverterTest`: manter os testes existentes; `setTestKey` continua prevalecendo e
   continua bloqueado fora do perfil `test`.
4. `CredentialMasterKeyStartupCheckTest`: manter; adaptar à nova fonte.
5. Regressão: `mvn clean install` → 0 falhas (unit + IT), `npm run test` + `npm run build` → 0 falhas.

## Migração dos dados (aprovada pelo usuário)

O banco dev tem a credencial `OPENCODE_GO` cifrada com uma chave **ad-hoc** gerada ad hoc. Com a
chave fixa de dev ela fica ilegível. **Ação: apagar as linhas de `provider_credential`** (dados de
teste) e o usuário cadastra a chave uma única vez pela tela, agora com chave estável.
**Esta limpeza é feita por mim (orchestrator), não pelo developer.**

## Fora de escopo

- Rotação de chave com re-cifragem automática (fase 2).
- KMS / secret manager.
- Migration Flyway (não há mudança de schema).

## Critérios de aceite (H6)

- [ ] `provider.master.key` funciona em dev sem env var
- [ ] env var tem precedência
- [ ] produção sem nenhuma das duas → falha no boot com mensagem acionável
- [ ] IT prova que o Hibernate usa o bean (gravar + reler sem env var)
- [ ] chave nunca em log/`toString`
- [ ] `mvn clean install` + `npm run test` + `npm run build` → 0 falhas
- [ ] `fullstack-code-reviewer` → zero 🔴
- [ ] `brain_store`
- [ ] Processos finalizados
