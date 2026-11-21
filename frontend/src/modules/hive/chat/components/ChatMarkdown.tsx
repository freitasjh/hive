"use client"

import * as React from "react"
import { Streamdown, useIsCodeFenceIncomplete, type ExtraProps } from "streamdown"
import {
  CodeBlock,
  CodeBlockActions,
  CodeBlockContent,
  CodeBlockCopyButton,
  CodeBlockHeader,
  CodeBlockLine,
  CodeBlockTitle,
} from "@/components/shadcn/ui/code-block"
import { useHighlightedCode } from "@/hooks/use-highlighted-code"
import { cn } from "@/lib/cn"
import { useChatI18n } from "../i18n"

type CodeComponentProps = React.ComponentProps<"code"> &
  ExtraProps & { "data-block"?: string }

/** Streamdown entrega o texto do fence como string (ou 1 elemento com children string). */
function codeText(children: React.ReactNode): string {
  if (typeof children === "string" || typeof children === "number") return String(children)
  if (Array.isArray(children)) return children.map(codeText).join("")
  if (React.isValidElement(children)) {
    return codeText((children.props as { children?: React.ReactNode }).children)
  }
  return ""
}

/**
 * Ponte Streamdown → primitives shadcn CodeBlock.
 *
 * `data-block` só existe em fence (ui.shadcn.com/docs/components/base/code-block,
 * consultado em 2026-11-20): inline code continua `<code>` e não vira bloco.
 * O highlight vem de useHighlightedCode/Shiki JS — nenhum AI SDK envolvido.
 */
export function MarkdownCode({ node: _node, className, children, ...props }: CodeComponentProps) {
  const { t } = useChatI18n()
  const isBlock = "data-block" in props
  const language = isBlock ? (className?.match(/language-([^\s]+)/)?.[1] ?? "") : ""
  const raw = isBlock ? codeText(children) : ""
  const lines = useHighlightedCode(raw, language)
  const incomplete = useIsCodeFenceIncomplete()

  if (!isBlock) {
    return (
      <code
        className={cn("rounded bg-muted px-1.5 py-0.5 font-mono text-sm", className)}
        {...props}
      >
        {children}
      </code>
    )
  }

  return (
    <CodeBlock>
      <CodeBlockHeader>
        <CodeBlockTitle>{language || t("chat.code.plain")}</CodeBlockTitle>
        <CodeBlockActions>
          <CodeBlockCopyButton
            value={raw}
            disabled={incomplete}
            aria-label={incomplete ? t("chat.code.copyUnavailable") : t("chat.thread.copy")}
            data-testid="chat-code-copy"
          />
        </CodeBlockActions>
      </CodeBlockHeader>
      <CodeBlockContent lineNumbers>
        {lines.map((line, index) => (
          <CodeBlockLine key={index}>{line}</CodeBlockLine>
        ))}
      </CodeBlockContent>
    </CodeBlock>
  )
}

export interface ChatMarkdownProps {
  content: string
  /** Passar durante a geração: habilita o estado "código incompleto" do Streamdown. */
  isAnimating?: boolean
}

export function ChatMarkdown({ content, isAnimating = false }: ChatMarkdownProps) {
  return (
    <div data-chat-bubble="ASSISTANT" className="min-w-0 text-sm leading-relaxed wrap-break-word">
      <Streamdown components={{ code: MarkdownCode }} isAnimating={isAnimating}>
        {content}
      </Streamdown>
    </div>
  )
}

export default ChatMarkdown
