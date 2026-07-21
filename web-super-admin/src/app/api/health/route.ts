import { NextResponse } from "next/server";
import { getDeploymentCommit } from "@/lib/build-info";
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
        commit: getDeploymentCommit(),
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
