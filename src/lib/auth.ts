import { redirect } from "next/navigation";
import { createClient } from "@/lib/supabase/server";

export async function requireSuperAdmin() {
  const supabase = await createClient();
  const { data, error } = await supabase.auth.getClaims();

  if (error || !data?.claims) {
    redirect("/login");
  }

  const { data: allowed, error: allowedError } = await supabase.rpc("is_super_admin");
  if (allowedError || allowed !== true) {
    redirect("/unauthorized");
  }

  return data.claims;
}
