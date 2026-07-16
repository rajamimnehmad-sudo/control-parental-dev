export const devSupabaseProjectRef = "syeycayasyufedwoprea";
export const devSupabaseUrl = `https://${devSupabaseProjectRef}.supabase.co`;

export function requireDevSupabaseUrl() {
  const configured = process.env.NEXT_PUBLIC_SUPABASE_URL;
  if (configured !== devSupabaseUrl) {
    throw new Error("Super Admin must target the approved Supabase DEV project");
  }
  return configured;
}

export function requireSupabasePublishableKey() {
  const configured = process.env.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY;
  if (!configured) throw new Error("Missing NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY");
  return configured;
}
