import { NextResponse } from "next/server";
import { devSupabaseProjectRef, requireDevSupabaseUrl } from "@/lib/environment";

export const dynamic = "force-dynamic";

export function GET() {
  try {
    requireDevSupabaseUrl();
    return NextResponse.json(
      {
        service: "content-filter-super-admin",
        status: "ok",
        environment: "DEV",
        supabaseProjectRef: devSupabaseProjectRef,
        commit: process.env.VERCEL_GIT_COMMIT_SHA ?? process.env.CF_PAGES_COMMIT_SHA ?? "local",
      },
      { headers: { "Cache-Control": "no-store" } },
    );
  } catch {
    return NextResponse.json(
      { service: "content-filter-super-admin", status: "misconfigured" },
      { status: 503, headers: { "Cache-Control": "no-store" } },
    );
  }
}
