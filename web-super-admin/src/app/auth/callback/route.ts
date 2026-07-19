import { NextResponse, type NextRequest } from "next/server";
import { superAdminSiteUrl } from "@/lib/environment";
import { createClient } from "@/lib/supabase/server";

const updatePasswordPath = "/actualizar-password";

function loginRedirect(reason: string) {
  const target = new URL("/login", superAdminSiteUrl);
  target.searchParams.set("recoveryError", reason);
  return NextResponse.redirect(target);
}

export async function GET(request: NextRequest) {
  const code = request.nextUrl.searchParams.get("code");
  const next = request.nextUrl.searchParams.get("next");
  if (!code || next !== updatePasswordPath) {
    return loginRedirect("invalid");
  }

  const supabase = await createClient();
  const { error: exchangeError } = await supabase.auth.exchangeCodeForSession(code);
  if (exchangeError) {
    if (exchangeError.status === 402 || exchangeError.message.includes("project is restricted")) {
      return loginRedirect("service");
    }
    return loginRedirect("expired");
  }

  const { data: allowed, error: allowedError } = await supabase.rpc("is_super_admin");
  if (allowedError || allowed !== true) {
    await supabase.auth.signOut();
    return loginRedirect("unauthorized");
  }

  return NextResponse.redirect(new URL(updatePasswordPath, superAdminSiteUrl));
}
