import { notFound } from "next/navigation";
import { createClient } from "@/lib/supabase/server";
import type {
  CommunityAdmin,
  CommunityDetail,
  CommunitySummary,
  DagUsageDevice,
  DagUsageSummary,
  ProtectedUser,
} from "@/lib/types";

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
