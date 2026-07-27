import { notFound } from "next/navigation";
import { createClient } from "@/lib/supabase/server";
import type {
  CommunityAdmin,
  CommunityDetail,
  CommunityDevice,
  CommunitySummary,
  ProtectedUser,
  ProtectionAlertEvent,
  Announcement,
} from "@/lib/types";

const devUpdateBase = "https://syeycayasyufedwoprea.supabase.co/storage/v1/object/public/dev-updates";

async function readDevVersion(app: "user" | "admin"): Promise<number | null> {
  try {
    const response = await fetch(`${devUpdateBase}/app-${app}-dev-manifest.json`, { cache: "no-store" });
    if (!response.ok) return null;
    const value = (await response.json()) as { versionCode?: unknown };
    return typeof value.versionCode === "number" ? value.versionCode : null;
  } catch {
    return null;
  }
}

export async function listAnnouncements() {
  const supabase = await createClient();
  const { data, error } = await supabase.rpc("super_admin_list_announcements", { max_rows: 100 });
  if (error) raise(error);
  return (data ?? []) as Announcement[];
}

function raise(error: unknown): never {
  const message = error instanceof Error ? error.message : "No se pudo completar la operacion";
  throw new Error(message);
}

export async function listProtectionAlerts() {
  const supabase = await createClient();
  const { data, error } = await supabase.rpc("super_admin_list_protection_alerts", { max_rows: 200 });
  if (error) raise(error);
  return (data ?? []) as ProtectionAlertEvent[];
}

export async function listCommunities() {
  const supabase = await createClient();
  const { data, error } = await supabase.rpc("super_admin_list_communities");
  if (error) raise(error);
  return (data ?? []) as CommunitySummary[];
}

export async function getCommunityBundle(communityId: string) {
  const supabase = await createClient();
  const [detailResult, adminsResult, protectedUsersResult, devicesResult, userVersion, adminVersion] = await Promise.all([
    supabase.rpc("super_admin_get_community_detail", { target_community_id: communityId }),
    supabase.rpc("super_admin_list_community_admins", { target_community_id: communityId }),
    supabase.rpc("super_admin_list_protected_users", { target_community_id: communityId }),
    supabase.rpc("super_admin_list_community_devices", { target_community_id: communityId }),
    readDevVersion("user"),
    readDevVersion("admin"),
  ]);

  if (detailResult.error) raise(detailResult.error);
  if (adminsResult.error) raise(adminsResult.error);
  if (protectedUsersResult.error) raise(protectedUsersResult.error);
  if (devicesResult.error) raise(devicesResult.error);
  const detail = (detailResult.data ?? [])[0] as CommunityDetail | undefined;
  if (!detail) notFound();

  return {
    detail,
    admins: (adminsResult.data ?? []) as CommunityAdmin[],
    protectedUsers: (protectedUsersResult.data ?? []) as ProtectedUser[],
    devices: (devicesResult.data ?? []) as CommunityDevice[],
    devVersions: { user: userVersion, admin: adminVersion },
  };
}
