export type LicenseStatus = "active" | "suspended" | "expired" | "scheduled";

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
  dag_entitled: boolean;
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

export type DevAppVersions = {
  user: number | null;
  admin: number | null;
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
  dag_enabled: boolean;
};

export type DagUsageSummary = {
  community_id: string;
  community_name: string;
  monthly_limit: number;
  active_dag_devices: number;
  request_count: number;
  total_capacity: number;
  remaining_count: number;
  last_usage_at: string | null;
};

export type DagUsageDevice = {
  community_id: string;
  device_id: string;
  display_name: string;
  dag_enabled: boolean;
  monthly_limit: number;
  request_count: number;
  remaining_count: number;
  last_usage_at: string | null;
  last_seen_at: string | null;
};

export type ProtectionAlertEvent = {
  event_id: string;
  community_id: string;
  community_name: string;
  device_id: string;
  device_name: string;
  alert_type: string;
  title: string;
  body: string;
  created_at: string;
};

export type Announcement = {
  announcement_id: string;
  community_id: string;
  community_name: string;
  target_role: "admin" | "user" | "all";
  title: string;
  body: string;
  created_at: string;
  expires_at: string | null;
};

export type DagCalibrationReview = {
  review_id: string;
  community_id: string;
  community_name: string;
  device_id: string;
  device_name: string;
  storage_path: string;
  image_url: string | null;
  model_version: string;
  initial_decision: "allowed" | "blocked" | "uncertain";
  submission_source: "automatic_uncertainty" | "manual_dag";
  scores: Record<string, number>;
  status: "pending" | "reviewed";
  review_decision: "allow" | "block" | null;
  review_reason: string | null;
  review_note: string | null;
  created_at: string;
  expires_at: string;
  reviewed_at: string | null;
};

export type DagCalibrationVersion = {
  calibration_id: string;
  version_number: number;
  status: "candidate" | "active" | "retired";
  thresholds: Record<string, number>;
  metrics: Record<string, number>;
  labeled_item_count: number;
  model_version: string;
  explanation: string;
  created_at: string;
  activated_at: string | null;
};

export type DagCalibrationAudit = {
  audit_id: string;
  action: string;
  review_id: string | null;
  calibration_id: string | null;
  details: Record<string, unknown>;
  created_at: string;
};

export type DagCalibrationModel = {
  model_id: string;
  model_version: string;
  status: "registered" | "training" | "candidate" | "active" | "retired" | "failed";
  artifact_path: string | null;
  artifact_sha256: string | null;
  training_example_count: number;
  validation_metrics: Record<string, number>;
  notes: string | null;
  created_at: string;
  updated_at: string;
};
