import { notFound } from "next/navigation";
import { createClient } from "@/lib/supabase/server";
import type {
  CommunityAdmin,
  CommunityDetail,
  CommunityDevice,
  CommunitySummary,
  DagUsageDevice,
  DagUsageSummary,
  ProtectedUser,
  ProtectionAlertEvent,
  Announcement,
  DagCalibrationAudit,
  DagCalibrationReview,
  DagCalibrationVersion,
  DagCalibrationModel,
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
  const [detailResult, adminsResult, protectedUsersResult, devicesResult, dagEntitlementResult, dagDevicesResult, userVersion, adminVersion] = await Promise.all([
    supabase.rpc("super_admin_get_community_detail", { target_community_id: communityId }),
    supabase.rpc("super_admin_list_community_admins", { target_community_id: communityId }),
    supabase.rpc("super_admin_list_protected_users", { target_community_id: communityId }),
    supabase.rpc("super_admin_list_community_devices", { target_community_id: communityId }),
    supabase.rpc("super_admin_get_dag_entitlement", { target_community_id: communityId }),
    supabase.rpc("super_admin_list_community_dag_devices", { target_community_id: communityId }),
    readDevVersion("user"),
    readDevVersion("admin"),
  ]);

  if (detailResult.error) raise(detailResult.error);
  if (adminsResult.error) raise(adminsResult.error);
  if (protectedUsersResult.error) raise(protectedUsersResult.error);
  if (devicesResult.error) raise(devicesResult.error);
  if (dagEntitlementResult.error) raise(dagEntitlementResult.error);
  if (dagDevicesResult.error) raise(dagDevicesResult.error);

  const detailRow = (detailResult.data ?? [])[0] as Omit<CommunityDetail, "dag_entitled"> | undefined;
  const detail = detailRow
    ? { ...detailRow, dag_entitled: dagEntitlementResult.data === true }
    : undefined;
  if (!detail) notFound();

  const dagByDevice = new Map<string, boolean>(
    ((dagDevicesResult.data ?? []) as Array<{ device_id: string; dag_enabled: boolean }>).map((row) => [row.device_id, row.dag_enabled]),
  );

  return {
    detail,
    admins: (adminsResult.data ?? []) as CommunityAdmin[],
    protectedUsers: ((protectedUsersResult.data ?? []) as Omit<ProtectedUser, "dag_enabled">[]).map((user) => ({
      ...user,
      dag_enabled: user.device_id ? (dagByDevice.get(user.device_id) ?? false) : false,
    })),
    devices: (devicesResult.data ?? []) as CommunityDevice[],
    devVersions: { user: userVersion, admin: adminVersion },
  };
}

export async function getDagUsageBundle(): Promise<{ summaries: DagUsageSummary[]; devices: DagUsageDevice[] }> {
  const supabase = await createClient();
  const [summaryResult, devicesResult] = await Promise.all([
    supabase.rpc("super_admin_get_dag_usage_summary"),
    supabase.rpc("super_admin_list_dag_usage_devices"),
  ]);

  if (summaryResult.error) raise(summaryResult.error);
  if (devicesResult.error) raise(devicesResult.error);

  return {
    summaries: (summaryResult.data ?? []).map((row: Record<string, unknown>) => normalizeDagSummary(row)),
    devices: (devicesResult.data ?? []).map((row: Record<string, unknown>) => normalizeDagDevice(row)),
  };
}

export async function getDagCalibrationBundle() {
  const supabase = await createClient();
  const [pendingResult, reviewedResult, versionsResult, auditResult, modelsResult] = await Promise.all([
    supabase.rpc("super_admin_list_dag_calibration_reviews_v2", { requested_status: "pending", max_rows: 200 }),
    supabase.rpc("super_admin_list_dag_calibration_reviews_v2", { requested_status: "reviewed", max_rows: 500 }),
    supabase.rpc("super_admin_list_dag_calibrations"),
    supabase.rpc("super_admin_list_dag_calibration_audit", { max_rows: 200 }),
    supabase.rpc("super_admin_list_dag_calibration_models"),
  ]);
  if (pendingResult.error) raise(pendingResult.error);
  if (reviewedResult.error) raise(reviewedResult.error);
  if (versionsResult.error) raise(versionsResult.error);
  if (auditResult.error) raise(auditResult.error);
  if (modelsResult.error) raise(modelsResult.error);

  const pending = (pendingResult.data ?? []) as Omit<DagCalibrationReview, "image_url">[];
  const withUrls = await Promise.all(pending.map(async (review) => {
    const { data } = await supabase.storage.from("dag-calibration").createSignedUrl(review.storage_path, 300);
    return { ...review, image_url: data?.signedUrl ?? null };
  }));
  return {
    pending: withUrls,
    reviewed: ((reviewedResult.data ?? []) as Omit<DagCalibrationReview, "image_url">[]).map((review) => ({ ...review, image_url: null })),
    versions: (versionsResult.data ?? []) as DagCalibrationVersion[],
    audit: (auditResult.data ?? []) as DagCalibrationAudit[],
    models: (modelsResult.data ?? []) as DagCalibrationModel[],
  };
}

function normalizeDagSummary(row: Record<string, unknown>): DagUsageSummary {
  return {
    community_id: String(row.community_id),
    community_name: String(row.community_name),
    monthly_limit: Number(row.monthly_limit ?? 0),
    active_dag_devices: Number(row.active_dag_devices ?? 0),
    request_count: Number(row.request_count ?? 0),
    total_capacity: Number(row.total_capacity ?? 0),
    remaining_count: Number(row.remaining_count ?? 0),
    last_usage_at: typeof row.last_usage_at === "string" ? row.last_usage_at : null,
  };
}

function normalizeDagDevice(row: Record<string, unknown>): DagUsageDevice {
  return {
    community_id: String(row.community_id),
    device_id: String(row.device_id),
    display_name: String(row.display_name),
    dag_enabled: row.dag_enabled === true,
    monthly_limit: Number(row.monthly_limit ?? 0),
    request_count: Number(row.request_count ?? 0),
    remaining_count: Number(row.remaining_count ?? 0),
    last_usage_at: typeof row.last_usage_at === "string" ? row.last_usage_at : null,
    last_seen_at: typeof row.last_seen_at === "string" ? row.last_seen_at : null,
  };
}
