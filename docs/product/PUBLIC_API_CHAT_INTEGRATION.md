# Public API Chat — Integration Guide

## Base URL

```
{base_url} = https://host/hive/v1/public
token_url  = https://host/hive/v1/public/auth/token
chat_url   = https://host/hive/v1/public/chat/completions
```

## Authentication (client_credentials)

```bash
curl -X POST $token_url \
  -H "Content-Type: application/json" \
  -d '{"grant_type":"client_credentials","client_id":"...","client_secret":"...","scope":"chat.completions"}'
```

Response:

```json
{"access_token":"...","token_type":"Bearer","expires_in":600,"scope":"chat.completions"}
```

Cache the token until `expires_in - 60s`. Do not re-authenticate per request (risk of 429 on `/auth/token`).

## Chat completions (OpenAI SDK compatible)

```python
from openai import OpenAI
client = OpenAI(base_url=base_url, api_key=access_token)
resp = client.chat.completions.create(model="resumo-pedido-venda", messages=[...])
```

`model` must be a provisioned `prompt_code`. `system` role is rejected (400).

## Error codes

| code | HTTP | meaning |
|------|------|---------|
| invalid_client | 401 | bad client_id/secret |
| invalid_grant | 400 | grant_type must be client_credentials |
| invalid_scope | 400 | requested scope not granted |
| prompt_not_in_allowlist | 403 | prompt_code not provisioned for this client |
| prompt_not_active | 409 | prompt template not ACTIVE |
| quota_exceeded | 429 | token/message/char limits exceeded |
| rate_limit_exceeded | 429 | rate limit per minute exceeded |

## Field taxonomy

- `prompt_code`: code of the prompt template (provisioned).
- `pin_mode`: `FOLLOW_ACTIVE` (default) or `PINNED` (with `pinned_version`).
- Limits: `m` (max messages), `c` (max message chars), `t` (max total chars).
