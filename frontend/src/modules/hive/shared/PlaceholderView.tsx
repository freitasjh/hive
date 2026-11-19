"use client"

import type { DashboardViewId } from "@/components/shadcn/navigation"
import { Button } from "@/components/shadcn/ui/button"
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/shadcn/ui/card"
import { HIVE_OVERVIEW_COPY, VIEW_LABELS } from "../overview"

export interface PlaceholderViewProps {
  view: Exclude<DashboardViewId, "overview">
  onBack: () => void
}

export function PlaceholderView({ view, onBack }: PlaceholderViewProps) {
  const label = VIEW_LABELS[view]

  return (
    <section className="flex flex-col gap-6" aria-labelledby="hive-placeholder-title">
      <div className="flex flex-col gap-1 px-4 lg:px-6">
        <h1
          id="hive-placeholder-title"
          className="text-xl font-bold tracking-tight text-foreground sm:text-2xl"
        >
          {label}
        </h1>
        <p className="text-xs text-muted-foreground">
          {HIVE_OVERVIEW_COPY.placeholderDescription}
        </p>
      </div>

      <div className="px-4 lg:px-6">
        <Card>
          <CardHeader>
            <CardTitle>{HIVE_OVERVIEW_COPY.placeholderTitle}</CardTitle>
            <CardDescription>
              {`${label}${HIVE_OVERVIEW_COPY.placeholderDetail}`}
            </CardDescription>
          </CardHeader>
          <CardContent>
            <Button type="button" onClick={onBack}>
              {HIVE_OVERVIEW_COPY.backLabel}
            </Button>
          </CardContent>
        </Card>
      </div>
    </section>
  )
}
