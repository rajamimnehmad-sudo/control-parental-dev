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
  AppRating,
  SupportReport,
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

export async function listAppRatings() {
  const supabase = await createClient();
  const { data, error } = await supabase.rpc("super_admin_list_app_ratings", { max_rows: 500 });
  if (error) raise(error);
  return (data ?? []) as AppRating[];
}

export async function listSupportReports() {
  const supabase = await createClient();
  const { data, error } = await supabase.rpc("super_admin_list_support_reports", { max_rows: 500 });
  if (error) raise(error);
  return (data ?? []) as SupportReport[];
}

export async function getCommunityBundle(communityId: string) {
  const supabase = await createClient();
  const [detailResult, adminsResult, contactsResult, protectedUsersResult, devicesResult, metadataResult, userVersion, adminVersion] = await Promise.all([
    supabase.rpc("super_admin_get_community_detail", { target_community_id: communityId }),
    supabase.rpc("super_admin_list_community_admins", { target_community_id: communityId }),
    supabase.rpc("super_admin_list_admin_contacts", { target_community_id: communityId }),
    supabase.rpc("super_admin_list_protected_users", { target_community_id: communityId }),
    supabase.rpc("super_admin_list_community_devices", { target_community_id: communityId }),
    supabase.rpc("super_admin_list_device_metadata", { target_community_id: communityId }),
    readDevVersion("user"),
    readDevVersion("admin"),
  ]);

  if (detailResult.error) raise(detailResult.error);
  if (adminsResult.error) raise(adminsResult.error);
  if (contactsResult.error) raise(contactsResult.error);
  if (protectedUsersResult.error) raise(protectedUsersResult.error);
  if (devicesResult.error) raise(devicesResult.error);
  if (metadataResult.error) raise(metadataResult.error);
  const detail = (detailResult.data ?? [])[0] as CommunityDetail | undefined;
  if (!detail) notFound();

  const metadataByDevice = new Map(
    ((metadataResult.data ?? []) as Array<{ device_id: string; manufacturer: string | null; model: string | null; android_version: string | null; android_sdk: number | null }>)
      .map((row) => [row.device_id, row]),
  );
  const phoneByAdmin = new Map(
    ((contactsResult.data ?? []) as Array<{ admin_id: string; phone_e164: string | null }>).map((row) => [row.admin_id, row.phone_e164]),
  );
  return {
    detail,
    admins: ((adminsResult.data ?? []) as CommunityAdmin[]).map((admin) => ({ ...admin, phone_e164: phoneByAdmin.get(admin.admin_id) ?? null })),
    protectedUsers: (protectedUsersResult.data ?? []) as ProtectedUser[],
    devices: ((devicesResult.data ?? []) as CommunityDevice[]).map((device) => ({ ...device, ...metadataByDevice.get(device.device_id) })),
    devVersions: { user: userVersion, admin: adminVersion },
  };
}
