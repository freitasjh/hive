"use client"

import { useCallback, useState } from "react"
import {
  IconCreditCard,
  IconDotsVertical,
  IconLogout,
  IconNotification,
  IconUserCircle,
} from "@tabler/icons-react"
import { ShieldCheck, ShieldOff } from "lucide-react"

import { Badge } from "@/components/shadcn/ui/badge"
import {
  Avatar,
  AvatarFallback,
  AvatarImage,
} from "@/components/shadcn/ui/avatar"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/shadcn/ui/dropdown-menu"
import {
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  useSidebar,
} from "@/components/shadcn/ui/sidebar"
import { useAuthI18n } from "@/modules/hive/auth/i18n"
import { MfaSetupDialog } from "@/modules/hive/auth/components/MfaSetupDialog"
import { MfaDisableDialog } from "@/modules/hive/auth/components/MfaDisableDialog"
import { logout as logoutService } from "@/services/auth.service"

interface NavUserProps {
  user: {
    name: string
    email: string
    avatar: string
  }
  /** Injetável em testes/stories; em produção usa o logout com revogação no backend. */
  onLogout?: () => Promise<void>
}

export function NavUser({ user, onLogout }: NavUserProps) {
  const { isMobile } = useSidebar()
  const { t } = useAuthI18n()
  const [isPending, setIsPending] = useState(false)
  const [mfaDialog, setMfaDialog] = useState<"setup" | "disable" | null>(null)

  const handleLogout = useCallback(async () => {
    if (isPending) return
    setIsPending(true)
    try {
      await (onLogout ?? logoutService)()
    } finally {
      setIsPending(false)
    }
  }, [isPending, onLogout])

  return (
    <SidebarMenu>
      <SidebarMenuItem>
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <SidebarMenuButton
              size="lg"
              className="rounded-lg border border-border/40 bg-sidebar/50 transition-colors data-[state=open]:bg-sidebar-accent data-[state=open]:text-sidebar-accent-foreground hover:bg-sidebar-accent/80 hover:border-border/60"
            >
              <div className="relative">
                <Avatar className="h-8 w-8 rounded-lg ring-1 ring-border/60">
                  <AvatarImage src={user.avatar} alt={user.name} />
                  <AvatarFallback className="rounded-lg bg-primary/10 text-xs font-semibold text-primary">
                    {user.name.slice(0, 2).toUpperCase()}
                  </AvatarFallback>
                </Avatar>
                <span
                  aria-hidden="true"
                  className="absolute -bottom-0.5 -right-0.5 size-2 rounded-full border-2 border-sidebar bg-emerald-500"
                />
              </div>
              <div className="grid flex-1 text-left text-sm leading-tight">
                <span className="truncate font-medium">{user.name}</span>
                <span className="truncate text-xs text-muted-foreground">
                  {user.email}
                </span>
              </div>
              <IconDotsVertical className="ml-auto size-4" />
            </SidebarMenuButton>
          </DropdownMenuTrigger>
          <DropdownMenuContent
            className="relative w-(--radix-dropdown-menu-trigger-width) min-w-56 overflow-hidden rounded-xl border border-border/70 p-1 shadow-[0_4px_16px_rgba(0,0,0,0.08),0_12px_32px_rgba(0,0,0,0.06)] dark:border-border/60 dark:shadow-none"
            side={isMobile ? "bottom" : "right"}
            align="end"
            sideOffset={4}
          >
            <span
              aria-hidden="true"
              className="pointer-events-none absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-foreground/15 to-transparent dark:via-white/20"
            />
            <DropdownMenuLabel className="p-0 font-normal">
              <div className="flex items-center gap-2 px-1 py-1.5 text-left text-sm">
                <Avatar className="h-8 w-8 rounded-lg ring-1 ring-border/60">
                  <AvatarImage src={user.avatar} alt={user.name} />
                  <AvatarFallback className="rounded-lg bg-primary/10 text-xs font-semibold text-primary">
                    {user.name.slice(0, 2).toUpperCase()}
                  </AvatarFallback>
                </Avatar>
                <div className="grid flex-1 text-left text-sm leading-tight">
                  <span className="truncate font-medium">{user.name}</span>
                  <span className="truncate text-xs text-muted-foreground">
                    {user.email}
                  </span>
                </div>
              </div>
            </DropdownMenuLabel>
            <DropdownMenuSeparator />
            <DropdownMenuGroup>
              <DropdownMenuItem>
                <IconUserCircle />
                Conta
              </DropdownMenuItem>
              <DropdownMenuItem>
                <IconCreditCard />
                Faturamento
              </DropdownMenuItem>
              <DropdownMenuItem>
                <IconNotification />
                Notificações
                <Badge variant="secondary" className="ml-auto h-4 px-1.5 text-[10px]">
                  2
                </Badge>
              </DropdownMenuItem>
            </DropdownMenuGroup>
            <DropdownMenuSeparator />
            <DropdownMenuGroup>
              <DropdownMenuItem onSelect={() => setMfaDialog("setup")}>
                <ShieldCheck />
                {t("auth.nav.mfa.setup")}
              </DropdownMenuItem>
              <DropdownMenuItem onSelect={() => setMfaDialog("disable")}>
                <ShieldOff />
                {t("auth.nav.mfa.disable")}
              </DropdownMenuItem>
            </DropdownMenuGroup>
            <DropdownMenuSeparator />
            <DropdownMenuItem
              disabled={isPending}
              aria-busy={isPending}
              onSelect={(event) => {
                // Mantém o menu aberto para expor o estado pendente (aria-busy +
                // disabled); o redirect do logout desmonta o shell logo em seguida.
                event.preventDefault()
                void handleLogout()
              }}
            >
              <IconLogout />
              {isPending ? t("auth.nav.loggingOut") : t("auth.nav.logout")}
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </SidebarMenuItem>
      <MfaSetupDialog
        open={mfaDialog === "setup"}
        onOpenChange={(open) => {
          if (!open) setMfaDialog(null)
        }}
      />
      <MfaDisableDialog
        open={mfaDialog === "disable"}
        onOpenChange={(open) => {
          if (!open) setMfaDialog(null)
        }}
      />
    </SidebarMenu>
  )
}
