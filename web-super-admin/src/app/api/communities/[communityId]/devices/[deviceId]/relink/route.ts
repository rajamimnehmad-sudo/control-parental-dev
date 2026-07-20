import { NextResponse } from "next/server";
import { createClient } from "@/lib/supabase/server";

type Props = {
  params: Promise<{ communityId: string; deviceId: string }>;
};

export async function POST(_request: Request, { params }: Props) {
  const { deviceId } = await params;
  const supabase = await createClient();
  const { data, error } = await supabase.rpc("super_admin_create_device_relink_code", {
    target_device_id: deviceId,
    ttl_minutes: 30,
  });

  if (error) {
    return NextResponse.json({ error: error.message }, { status: 400 });
  }

  const row = (data ?? [])[0] as { activation_code: string; expires_at: string } | undefined;
  if (!row) {
    return NextResponse.json({ error: "No se pudo generar el token" }, { status: 500 });
  }

  return NextResponse.json({ activationCode: row.activation_code, expiresAt: row.expires_at });
}
