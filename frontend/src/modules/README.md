# Convenção de `src/modules`

Organização por **feature** (domínio de UI), não por tipo de arquivo. Cada feature
é dona de suas pages, components, dados e estado; o que serve a mais de uma
feature vive em `shared/`.

```
src/modules/
├── README.md              # esta convenção
└── hive/                  # feature
    ├── README.md          # notas da feature
    ├── index.ts           # API pública (barrel)
    ├── overview/
    │   ├── index.ts
    │   ├── pages/
    │   ├── components/
    │   └── data/
    ├── admin/
    │   ├── index.ts
    │   ├── pages/
    │   ├── components/
    │   ├── credentials/
    │   ├── llm/
    │   └── prompts/
    └── shared/
        └── index.ts
```

## Onde cada arquivo vive

| Pasta / arquivo | Papel |
| --- | --- |
| `pages/` | Composição da rota: template + containers. Sem lógica de negócio. |
| `components/` | Apresentação da feature (View). Recebe props; não chama serviço HTTP. |
| Subpastas de domínio (`credentials/`, `llm/`, `prompts/`) | Unidade Container (estado/dados) + View (apresentação) + stories/testes. |
| `data/` | Constantes e formatação estática. Sem rede. |
| `i18n.ts` | Textos/chaves da feature, quando necessário. |
| `state.ts` | Tipos de estado e ações da feature, quando necessário. |
| `index.ts` | API pública: reexporta só o que outras features e o App consomem. |

Container obtém dados/estado e passa à View. View é apresentação pura, testável
por props. Page é o ponto de entrada da rota.

## Regra de import

- **Entre features: somente pelo barrel** — `@/modules/hive`,
  `@/modules/hive/overview`, `@/modules/hive/admin`, `@/modules/hive/shared`.
  Nunca importar arquivo interno de outra feature.
- **Entre subfeatures irmãs (`overview/`, `admin/`, `shared/`): pelo barrel.**
  Dentro da mesma subfeature: caminho relativo direto (`../data/...`, `./View`).
- Serviços HTTP e DTOs ficam em `src/services/`; não duplicar em `modules/`.
- Testes white-box da própria feature podem usar caminho relativo; cruzando a
  fronteira da feature, usam o barrel.
- Barrel só reposiciona a API: não renomeia, não transforma, não agrega lógica.

## Fontes (deepsearch — consultadas 2026-11-19)

- Bulletproof React — project structure: features colocated (`api/`,
  `components/`, `hooks/`, `types/`) com `index.ts` como API pública.
  https://github.com/alan2207/bulletproof-react/blob/master/docs/project-structure.md
  (2024-11-12).
- Robin Wieruch — React folder structure: feature folders mantêm componentes
  específicos do domínio fora do `components/` global.
  https://www.robinwieruch.de/react-folder-structure/ (2026).
