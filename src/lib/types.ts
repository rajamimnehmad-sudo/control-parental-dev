export type LicenseStatus = "active" | "suspended" | "expired";

export type CommunitySummary = {
  community_id: string;
  account_id: string | null;
  name: string;
  guide_label: string;
  license_status: LicenseStatus;
  plan_name: string;
  expires_at: string | null;
  max_admins: number | null;
  max_user_devices: number | null;
  max_admin_devices: number | null;
  admin_count: number;
  user_device_count: number;
  admin_device_count: number;
  created_at: string;
  updated_at: string;
};

export type CommunityDetail = CommunitySummary & {
  starts_at: string | null;
  internal_notes: string | null;
};

export type CommunityAdmin = {
  admin_id: string;
  display_name: string;
  email: string | null;
  created_at: string;
  updated_at: string;
  activated_device_id: string | null;
  activated_device_name: string | null;
  activated_at: string | null;
  last_seen_at: string | null;
  pending_token_expires_at: string | null;
};

export type CommunityDevice = {
  device_id: string;
  account_id: string;
  display_name: string;
  app_role: "user" | "admin";
  app_version_code: number;
  community_admin_id: string | null;
  community_admin_name: string | null;
  activated_at: string;
  last_seen_at: string | null;
  updated_at: string;
};

export type ProtectedUserStatus = "pending" | "activated" | "expired";

export type ProtectedUser = {
  protected_user_id: string;
  display_name: string;
  status: ProtectedUserStatus;
  activation_code_id: string | null;
  device_id: string | null;
  app_version_code: number | null;
  creator_admin_id: string | null;
  creator_admin_name: string | null;
  creator_admin_email: string | null;
  token_created_at: string | null;
  token_expires_at: string | null;
  activated_at: string | null;
  last_seen_at: string | null;
  updated_at: string;
};
