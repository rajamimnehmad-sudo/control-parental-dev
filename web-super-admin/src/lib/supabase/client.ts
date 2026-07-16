import { createBrowserClient } from "@supabase/ssr";
import { requireDevSupabaseUrl, requireSupabasePublishableKey } from "@/lib/environment";

export function createClient() {
  return createBrowserClient(
    requireDevSupabaseUrl(),
    requireSupabasePublishableKey(),
  );
}
