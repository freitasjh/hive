# Débito técnico — o nome de projeto compose `docker` é compartilhado por 4 produtos

**Registrado em:** 2026-09-27, na verificação de ambiente do batch de UX do seletor.
**Status:** 🟡 **Mitigado** — `name: hive` nos dois compose do hive. **A causa raiz estrutural
permanece** e está descrita abaixo.
**Classe:** ambiente / isolamento.

> Este incidente **já tinha acontecido duas vezes** e está registrado em `workflow-state.json:363` e
> em outra linha da mesma lista ("Banco de dev perdido (container recriado com volume novo)"). Ninguém
> sabia por quê. Não é azar de configuração: é um namespace de compose compartilhado.

---

## A causa raiz

O Compose V2 deriva o **nome do projeto** do **nome do diretório** onde o arquivo está — e o
diretório se chama `docker`. Dois clones do mesmo produto produzem, portanto, o mesmo projeto.

```
$ docker compose -f docker/docker-compose-vetor.yml config | head -1
name: docker
$ docker compose -f /opt/systec/wsProjetos/hive/docker/docker-compose-vetor.yml config | head -1
name: docker
```

Todos os containers e volumes abaixo vivem no namespace `docker`:

| recurso | projeto compose | arquivo de configuração | produto |
|---------|-----------------|-------------------------|---------|
| `postgres` | `docker` | `…/wsProjeto/hive/docker/docker-compose-vetor.yml` | **hive** |
| `hive-redis`, `hive-redis-insight` | `docker` | **`/opt/systec/wsProjetos/hive/docker/docker-compose-redis.yml`** | **hive** (outro clone) |
| `rabbitmq` | `docker` | `…/wsProjeto/**taskflow**/docker/docker-compose-rabbitmq.yml` | **taskflow** |
| volume `docker_atlas-ecm-pgdata` | `docker` | **atlas-ecm** | **atlas-ecm** |
| volume `docker_postgres_data` | `docker` | hive (e atlas-ecm, compartilhado) | **hive** + **atlas-ecm** |
| volume `docker_redis_data` | `docker` | hive | **hive** |

São **quatro produtos** num namespace só: hive ×2, taskflow, atlas-ecm.

## Como o postgres de dev foi derrubado

`docker compose -f … up -d` **reconcilia o namespace inteiro**, não só os serviços declarados no
arquivo. Subir o compose do hive encontrou `atlas-ecm-postgres` no mesmo projeto e imprimiu
`Container atlas-ecm-postgres Recreate / Recreated` — e o container **sumiu**.

Dois motivos para isso ter sido benigno, e nenhum deles é segurança:

1. **Só o serviço `postgres` foi tocado por sorte de configuração.** O `up` não remove órfãos sem
   `--remove-orphans`, então `rabbitmq`, `hive-redis` e `hive-redis-insight` sobreviveram. Tocar
   `--remove-orphans` teria derrubado o RabbitMQ do taskflow e o Redis do outro clone.
2. **O volume sobreviveu.** Container é descartável; volume não. `docker ps -a` confirma que
   `hive`, `atlas_ecm`, `atlas_admin` e `taskflow` continuam lá dentro. **Nenhum dado foi perdido** —
   o que foi feito também já tinha sido registrado como perdido antes.

## Por que `container_name` fixo agrava

`container_name: postgres` **sai do namespace do projeto**: o nome do container é global no Docker.
Duas consequências:

- Dois projetos com `container_name: postgres` **colidem no primeiro `up`** — o segundo falha, ou o
  primeiro é removido.
- Um `container_name` confundido (`atlas-ecm-postgres` num compose do hive) **esconde a origem** de um
  incidente. Foi o que fez todo mundo — inclusive a mim, na sessão anterior — acreditar que o
  container era de outro produto. `workflow-state.json:363` tinha a pista certa ("container_name
  atlas-ecm-postgres **do compose**") e ela estava perdida numa linha de nota.

## Por que mover a porta não resolveu

Na primeira tentativa de arrumar o ambiente eu movi o postgres do hive para a host `5433` e pensei
que estava resolvido. Não: o **outro clone** continua com `5432:5432` no mesmo namespace. No dia em
que alguém subir o compose dele, ele recria o container na `5432` e derruba o banco de dev de novo.
**A armadilha continuava armada** — só mudou o número.

## A mitigação (1 linha por arquivo, só no hive)

```yaml
name: hive     # V2 honra. Sem isso o projeto chama-se "docker" (nome do diretório)
```

Aplicado em `docker/docker-compose-vetor.yml` e `docker/docker-compose-redis.yml`.
**Prova:** `docker compose config | head -1` → `name: hive` nos dois.

Os compose do **taskflow** e do **atlas-ecm** **não foram tocados** — são outros produtos, e mexer
nelhes seria exatamente o erro que este registro descreve.

### Companion: os volumes precisam de `name:` fixado junto

Sozinho, o `name: hive` **quebraria o ambiente**: o compose deriva o nome do volume de
`<projeto>_<volume>`, então o próximo `up -d` criaria `hive_postgres_data` vazio e o banco de dev
"desapareceria" — o incidente pela terceira vez. Por isso os volumes estão com nome fixado:

```yaml
volumes:
  postgres_data:
    name: docker_postgres_data   # reaproveita o volume existente
  redis_data:
    name: docker_redis_data
```

Mudar nome de projeto e nome de volume é **um** passo. Fazer o primeiro sem o segundo é trocar um
sintoma por outro.

## ⚠️ A PRIMEIRA SUBIDA VAI FALHAR — e é para falhar

`name: hive` já está nos dois compose, mas **os containers no ar ainda são do projeto `docker`**:

| container | `com.docker.compose.project` | `container_name` no compose |
|-----------|------------------------------|------------------------------|
| `postgres` | `docker` | `postgres` (fixo) |
| `hive-redis` | `docker` | `hive-redis` (fixo) |
| `hive-redis-insight` | `docker` | `hive-redis-insight` (fixo) |

Então o próximo `docker compose -f docker/docker-compose-vetor.yml up -d` falha com:

```
Conflict. The container name "/postgres" is already in use
```

**Por que falha, e por que é o comportamento certo.** `container_name` **sai do namespace do projeto
compose**: é um nome **global** no Docker, o único namespace de nome de container que existe. O
Compose V2 acha `/postgres` ocupado, confere o label de projeto (`docker` ≠ `hive`), e aborta. Ele
**não adota** o container, por dois motivos independentes, e qualquer um dos dois bastaria:

1. o label de projeto não bate;
2. o `config-hash` mudou — o arquivo mudou (nome do projeto, porta `5432`→`5433`,
   `POSTGRES_DB` `embedding_db`→`hive`), e o hash é o que o Compose usa para decidir se um container
   é "o mesmo serviço" ou "outro serviço com o mesmo nome".

Ou seja: **o Compose se recusou a substituir o banco de dev em silêncio.** É exatamente o comportamento
que queríamos do incidente, e ele veio de graça da mudança de nome.

### A falha é segura — nenhum dado se perde

- Os **volumes estão fixados** (`docker_postgres_data`, `docker_redis_data`), então o volume
  existente é reaproveitado. O `up` que falha **não** recria nem apaga volume nenhum.
- O banco **continua no ar** enquanto o `up` não roda. O ambiente de dev está funcionando agora.
- Remover os containers **não** toca os volumes: `docker rm` (sem `-v`) tira só o container.

### Comando de uma vez

```bash
# 1. Adotar os containers do projeto `docker` antigo para o projeto `hive`.
#    Sem `-v`: os volumes estão fixados e saem intactos. Nenhum dado é tocado.
docker rm postgres hive-redis hive-redis-insight

# 2. Subir. Os volumes `docker_postgres_data` / `docker_redis_data` são reaproveitados.
docker compose -f docker/docker-compose-vetor.yml up -d
docker compose -f docker/docker-compose-redis.yml up -d
```

**Isto é a decisão do usuário, e não foi executado.** O ambiente de dev funciona hoje com os
containers antigos; removê-los derrubaria o banco de desenvolvimento. Quem migrar é quem tem o
`docker compose` na mão e a certeza de que o banco pode ser recriado (ele é — o volume persiste).
Duração da janela: alguns segundos, entre remover e subir.

### Redes: decisão de NÃO fixar

O compose vai derivar `hive_hive-network` (redis) e `hive_default` (vetor), que hoje são
`docker_hive-network` e `docker_default`. **Deixei derivar, de propósito**, e é uma decisão diferente
da dos volumes — vale registrar por que não é inconsistência:

- **O volume precisa de `name:` fixado por correção**: sem isso, um volume novo e vazio significa
  banco perdido. O dado obriga.
- **A rede não guarda nada.** É descartável por definição, então não há motivo de correção — e há
  motivo de **clareza**: fixá-la em `docker_hive-network` perpetuaria o prefixo `docker_` que é
  exatamente a confusão que este registro existe para remover, e esconderia a migração em
  `docker network ls`, onde o projeto agora se chamando `hive` é justamente a informação útil.
- **Custo de não unificar:** as redes antigas ficam órfãs. São removíveis com um
  `docker network rm docker_hive-network docker_default` depois da migração, e nada depende delas
  além dos containers que já terão sido removidos.

A regra que fica: **fixe o que guarda dado; deixe derivar o que é descartável.**

## O que fica de dívida

1. **Os dois clones do produto continuam existindo** (`/opt/systec/wsProjetos/hive` e
   `/home/joao/fusionlf/wsProjeto/hive`), com o mesmo `AGENTS.md` e o mesmo `docker/`. Enquanto
   existirem, **subir o compose do clone errado continua sendo possível** — agora vai criar um
   namespace novo (`docker`), sem colidir, mas duplicando ambiente. Decidir qual é o canônico é
   decisão de ambiente, não de código.
2. **A porta `5432` do host continua ocupada por container de outro produto.** O hive usa `5433`.
   Um dia alguém vai querer a 5432 de volta e o conflito volta. Socket público do Docker (`DOCKER_HOST`),
   portas por projeto ou rede dedicada resolvem; nenhum foi adotado aqui.
3. **O volume `docker_postgres_data` é compartilhado entre hive e atlas-ecm.** Dois produtos com o
   **mesmo** volume e superusuário `postgres` é a forma mais barata de "ambiente local compartilhado"
   e a mais fácil de destruir sem querer. Separar exige migração de dados — decisão de ambiente.

## Checklist antes de rodar qualquer compose

- [ ] `docker compose config | head -1` imprime `name:` **esperado**?
- [ ] Existe outro clone deste produto com o mesmo nome de projeto?
- [ ] Algum container com o **mesmo `container_name`** está no ar, de outro projeto? (vai dar
      `Conflict` — ver "A PRIMEIRA SUBIDA VAI FALHAR")
- [ ] Os volumes têm `name:` fixado (ou eu sei que o volume não importa)?
- [ ] As **redes** podem derivar? (elas não guardam dado — só os volumes precisam de `name:`)
- [ ] Eu vou rodar `--remove-orphans`? (se sim, eu **realmente** revisei quem é órfão)
