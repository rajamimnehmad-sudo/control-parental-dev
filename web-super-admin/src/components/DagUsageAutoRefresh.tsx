"use client";

import { RefreshCw } from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect, useState, useTransition } from "react";

const RefreshIntervalMillis = 10_000;

export function DagUsageAutoRefresh() {
  const router = useRouter();
  const [isRefreshing, startRefresh] = useTransition();
  const [lastRefresh, setLastRefresh] = useState("ahora");

  useEffect(() => {
    const interval = window.setInterval(() => {
      startRefresh(() => router.refresh());
      setLastRefresh(
        new Intl.DateTimeFormat("es-AR", {
          hour: "2-digit",
          minute: "2-digit",
          second: "2-digit",
        }).format(new Date()),
      );
    }, RefreshIntervalMillis);

    return () => window.clearInterval(interval);
  }, [router]);

  return (
    <div className="flex items-center gap-2 text-xs font-medium text-slate-500" aria-live="polite">
      <RefreshCw className={`h-3.5 w-3.5 ${isRefreshing ? "animate-spin text-accent" : ""}`} />
      <span>{isRefreshing ? "Actualizando" : `Actualizado ${lastRefresh}`} · cada 10 s</span>
    </div>
  );
}
