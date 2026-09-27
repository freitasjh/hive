/**
 * Read the `permissions` claim out of the JWT the app already holds.
 *
 * <h3>This is a UX gate, not a security boundary</h3>
 *
 * <p>Hiding a screen the user cannot use is the whole job here. The enforcement is
 * {@code @HasPermission} no servidor, e ela não depende deste arquivo: apagar este módulo da
 * aplicação inteira continuaria devolvendo 403 de toda rota protegida. O que acontece se alguém
 * FORJAR o claim no localStorage é a tela aparecer e a chamada falhar — nunca o contrário.
 *
 * <p>Por isso a ausência de token, de claim, ou de payload legível é <b>negação</b> (fail closed):
 * um guard que falha aberto aqui só denied-screen para quem já estava dentro.
 *
 * <p>O backend assina o token com {@code java-jwt} HS256 e o claim é uma lista de strings
 * (ver {@code JwtTestHelper.generateToken} e {@code SecurityTokenServiceImpl}). Base64URL — com
 * {@code -} e {@code _} no lugar de {@code +} e {@code /} — e sem o {@code =} de padding, que o
 * {@code atob} do browser não aceita.
 */
export function decodePermissions(token: string | null | undefined): string[] {
  if (!token) return []
  const segments = token.split('.')
  if (segments.length < 2) return []
  try {
    const base64 = segments[1].replace(/-/g, '+').replace(/_/g, '/')
    const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '=')
    const json = decodeURIComponent(
      atob(padded)
        .split('')
        .map(c => '%' + c.charCodeAt(0).toString(16).padStart(2, '0'))
        .join('')
    )
    const claims = JSON.parse(json) as { permissions?: unknown }
    return Array.isArray(claims.permissions) ? claims.permissions.filter((p): p is string => typeof p === 'string') : []
  } catch {
    // Token ilegível ou expirado fora da tela de login. Negar é a resposta que não abre nada.
    return []
  }
}

/**
 * Whether {@code token} carries {@code permission}. A route without a claim is not this
 * function's business — o chamador só consulta quando o meta declara um.
 */
export function hasPermission(token: string | null | undefined, permission: string): boolean {
  return decodePermissions(token).includes(permission)
}
