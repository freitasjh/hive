"use client"

import { useCallback, useEffect, useState, type ReactNode } from "react"
import { isTabbedView, useHashRoute, type RouteTab } from "@/hooks/use-hash-route"
import { getToken, type MfaChallengeState } from "@/services/auth.service"
import { LoginPage } from "@/pages/LoginPage"
import { MfaVerifyPage } from "@/pages/MfaVerifyPage"
import { MfaSetupDialog } from "@/modules/hive/auth/components/MfaSetupDialog"
import { MfaSetupInvite } from "@/modules/hive/auth/components/MfaSetupInvite"
import {
  AiAdminPage,
  ChatPage,
  OverviewPage,
  PlaceholderPage,
  PromptsAdminPage,
  VIEW_LABELS,
} from "@/modules/hive"

/**
 * Cada página embrulha a si mesma em `AtlasAdminTemplate` — DESIGN.md §3.
 * A URL é a única fonte de verdade: `#view/tab[/id]`. Menu usa `navigate`
 * (push); clique de aba e saída do editor usam `replaceRoute` (sem push).
 */
export default function App() {
  const [route, navigate, replaceRoute] = useHashRoute()
  const { view, tab, id } = route
  // Desafio MFA vive SOMENTE em memória: troca de view sem persistir em storage/URL.
  const [mfaChallenge, setMfaChallenge] = useState<MfaChallengeState | null>(null)
  // Onboarding opcional: convite e dispensa vivem apenas neste useState. NUNCA
  // em localStorage/sessionStorage — após reload o sinal de login não reaparece
  // sozinho, e um novo login com mfaSetupAvailable pode oferecer novamente.
  const [mfaInviteOpen, setMfaInviteOpen] = useState(false)
  const [mfaSetupOpen, setMfaSetupOpen] = useState(false)

  const handleAuthenticated = useCallback(
    (mfaSetupAvailable?: boolean) => {
      setMfaChallenge(null)
      setMfaSetupOpen(false)
      setMfaInviteOpen(mfaSetupAvailable === true)
      navigate("overview")
    },
    [navigate]
  )
  const handleMfaInviteSkip = useCallback(() => setMfaInviteOpen(false), [])
  const handleMfaInviteConfigure = useCallback(() => {
    setMfaInviteOpen(false)
    setMfaSetupOpen(true)
  }, [])
  const handleMfaSetupSuccess = useCallback(() => setMfaInviteOpen(false), [])
  const handleMfaRequired = useCallback(
    (challenge: MfaChallengeState) => {
      setMfaChallenge(challenge)
      navigate("mfa")
    },
    [navigate]
  )
  const handleMfaCancel = useCallback(() => {
    setMfaChallenge(null)
    navigate("login")
  }, [navigate])
  // Token é lido a cada render: logout/401 mudam o hash e forçam nova leitura.
  const authenticated = Boolean(getToken())

  // Páginas admin definem "<View> · <Aba>"; demais views exibem só o rótulo da view.
  useEffect(() => {
    if (!isTabbedView(view)) {
      document.title = VIEW_LABELS[view]
    }
  }, [view])

  // Guard: rota protegida sem sessão volta para #login ANTES de qualquer shell
  // Atlas montar. #mfa é exceção (desafio ainda não gerou token) e vive fora do
  // shell, como o login. Deep-link #mfa sem desafio em memória volta ao login.
  // `useLoginController` mantém o redirect inverso (token em #login → overview).
  // Todos os hooks estão acima do early return.
  useEffect(() => {
    if (!authenticated && view !== "login" && view !== "mfa") navigate("login")
    if (view === "mfa" && !mfaChallenge) navigate("login")
  }, [authenticated, view, mfaChallenge, navigate])

  if (!authenticated && view !== "login" && view !== "mfa") return null

  // Login e MFA vivem fora do shell Atlas: nenhuma sidebar/header é montada.
  if (view === "login") {
    return (
      <LoginPage
        onAuthenticated={handleAuthenticated}
        onMfaRequired={handleMfaRequired}
      />
    )
  }
  if (view === "mfa") {
    if (!mfaChallenge) return null
    return (
      <MfaVerifyPage
        challenge={mfaChallenge}
        onAuthenticated={handleAuthenticated}
        onCancel={handleMfaCancel}
      />
    )
  }
  // Shell Atlas: a página continua sendo a única fonte de conteúdo; o convite
  // é irmão dela (sem overlay), então a overview permanece navegável.
  let shell: ReactNode
  if (view === "overview") {
    shell = <OverviewPage onSelectView={navigate} />
  } else if (view === "providers") {
    shell = (
      <AiAdminPage
        view="providers"
        tab={tab === "models" ? "models" : "credentials"}
        onTabChange={(next: RouteTab) => replaceRoute("providers", next === "models" ? "models" : "credentials")}
        onSelectView={navigate}
      />
    )
  } else if (view === "agents") {
    shell = (
      <AiAdminPage
        view="agents"
        tab={tab === "credentials" ? "credentials" : "models"}
        onTabChange={(next: RouteTab) => replaceRoute("agents", next === "credentials" ? "credentials" : "models")}
        onSelectView={navigate}
      />
    )
  } else if (view === "chat") {
    shell = <ChatPage onSelectView={navigate} />
  } else if (view === "prompts") {
    shell = (
      <PromptsAdminPage
        tab={tab === "editor" ? "editor" : "templates"}
        editorId={id}
        onTabChange={(next) =>
          replaceRoute(view, next, next === "editor" ? id ?? "new" : undefined)
        }
        onOpenEditor={(promptId) => navigate(view, "editor", promptId ?? "new")}
        onLeaveEditor={() => replaceRoute(view, "templates")}
        onSelectView={navigate}
      />
    )
  } else {
    // Exaustivo: após login/mfa + overview/providers/agents/chat/prompts, `view`
    // é `never` — placeholder inalcançável.
    shell = <PlaceholderPage view={view} onSelectView={navigate} />
  }

  return (
    <>
      {shell}
      {authenticated && mfaInviteOpen && (
        <MfaSetupInvite
          onConfigure={handleMfaInviteConfigure}
          onSkip={handleMfaInviteSkip}
        />
      )}
      <MfaSetupDialog
        open={authenticated && mfaSetupOpen}
        onOpenChange={setMfaSetupOpen}
        onSuccess={handleMfaSetupSuccess}
      />
    </>
  )
}
