"use client"

import { Hexagon } from "lucide-react"
import { Badge } from "@/components/shadcn/ui/badge"
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/shadcn/ui/card"
import {
  HIVE_MODULES,
  HIVE_METRICS,
  HIVE_OVERVIEW_COPY,
  HIVE_STACK,
} from "../data/overview.data"

export function HiveOverviewView() {
  return (
    <section className="flex flex-col gap-6" aria-labelledby="hive-overview-title">
      <div className="flex flex-col gap-1 px-4 lg:px-6">
        <h1
          id="hive-overview-title"
          className="text-xl font-bold tracking-tight text-foreground sm:text-2xl"
        >
          {HIVE_OVERVIEW_COPY.title}
        </h1>
        <p className="text-xs text-muted-foreground">
          {HIVE_OVERVIEW_COPY.subtitle}
        </p>
      </div>

      <div className="grid grid-cols-1 gap-4 px-4 sm:grid-cols-2 lg:grid-cols-4 lg:px-6">
        {HIVE_METRICS.map((metric) => (
          <Card key={metric.label} className="gap-3 py-5">
            <CardHeader className="gap-1 px-5">
              <CardTitle className="flex items-center gap-2 text-xs font-medium text-muted-foreground">
                {metric.label === "Projeto" ? (
                  <Hexagon className="size-4 text-primary" aria-hidden="true" />
                ) : null}
                {metric.label}
              </CardTitle>
            </CardHeader>
            <CardContent className="flex flex-col gap-1 px-5">
              <span className="text-2xl font-bold tracking-tight text-foreground">
                {metric.value}
              </span>
              <span className="text-xs text-muted-foreground">
                {metric.detail}
              </span>
            </CardContent>
          </Card>
        ))}
      </div>

      <div className="grid grid-cols-1 gap-4 px-4 lg:grid-cols-2 lg:px-6">
        <Card>
          <CardHeader>
            <CardTitle>{HIVE_OVERVIEW_COPY.modulesTitle}</CardTitle>
            <CardDescription>
              {HIVE_OVERVIEW_COPY.modulesDescription}
            </CardDescription>
          </CardHeader>
          <CardContent>
            <ul
              className="flex flex-wrap gap-2"
              aria-label={HIVE_OVERVIEW_COPY.modulesTitle}
            >
              {HIVE_MODULES.map((moduleName) => (
                <li key={moduleName}>
                  <Badge variant="outline">{moduleName}</Badge>
                </li>
              ))}
            </ul>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>{HIVE_OVERVIEW_COPY.stackTitle}</CardTitle>
            <CardDescription>{HIVE_OVERVIEW_COPY.stackDescription}</CardDescription>
          </CardHeader>
          <CardContent>
            <ul
              className="flex flex-col gap-2"
              aria-label={HIVE_OVERVIEW_COPY.stackTitle}
            >
              {HIVE_STACK.map((technology) => (
                <li key={technology} className="flex items-center gap-2 text-sm text-foreground">
                  <span className="size-1.5 shrink-0 rounded-full bg-primary" aria-hidden="true" />
                  {technology}
                </li>
              ))}
            </ul>
          </CardContent>
        </Card>
      </div>
    </section>
  )
}
