import { notFound } from "next/navigation";
import { createClient } from "@/lib/supabase/server";
import type { CommunityAdmin, CommunityDetail, CommunitySummary, ProtectedUser } from "@/lib/types";

function raise(error: unknown): never {
  const message = error instanceof Error ? error.message : "No se pudo completar la operacion";
  throw new Error(message);
}

export async function listCommunities() {
  const supabase = await createClient();
  const { data, error } = await supabase.rpc("super_admin_list_communities");
  if (error) raise(error);
  return (data ?? []) as CommunitySummary[];
}

export async function getCommunityBundle(communityId: string) {
  const supabase = await createClient();
  const [detailResult, adminsResult, protectedUsersResult] = await Promise.all([
    supabase.rpc("super_admin_get_community_detail", { target_community_id: communityId }),
    supabase.rpc("super_admin_list_community_admins", { target_community_id: communityId }),
    supabase.rpc("super_admin_list_protected_users", { target_community_id: communityId }),
  ]);

  if (detailResult.error) raise(detailResult.error);
  if (adminsResult.error) raise(adminsResult.error);
  if (protectedUsersResult.error) raise(protectedUsersResult.error);

  const detail = (detailResult.data ?? [])[0] as CommunityDetail | undefined;
  if (!detail) notFound();

  return {
    detail,
    admins: (adminsResult.data ?? []) as CommunityAdmin[],
    protectedUsers: (protectedUsersResult.data ?? []) as ProtectedUser[],
  };
}
