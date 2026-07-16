import { NextResponse } from "next/server";
import { revalidatePath } from "next/cache";
import { z } from "zod";
import { createClient } from "@/lib/supabase/server";

const schema = z.object({
  displayName: z.string().trim().min(2),
  ttlMinutes: z.coerce.number().int().min(5).max(10080),
});

type Props = {
  params: Promise<{ communityId: string }>;
};

export async function POST(request: Request, { params }: Props) {
  const { communityId } = await params;
  const contentType = request.headers.get("content-type") ?? "";
  const isJsonRequest = contentType.includes("application/json");
  if (!isJsonRequest) {
    return NextResponse.json({ error: "Usa el formulario seguro de Super Admin" }, { status: 415 });
  }
  const body = isJsonRequest ? await request.json().catch(() => null) : Object.fromEntries(await request.formData());
  const parsed = schema.safeParse(body);

  if (!parsed.success) {
    return NextResponse.json({ error: "Datos invalidos" }, { status: 400 });
  }

  const supabase = await createClient();
  const { data, error } = await supabase.rpc("super_admin_create_admin_pairing_code", {
    target_community_id: communityId,
    admin_display_name: parsed.data.displayName,
    admin_email: null,
    ttl_minutes: parsed.data.ttlMinutes,
  });

  if (error) {
    return NextResponse.json({ error: error.message }, { status: 400 });
  }

  const row = (data ?? [])[0] as { community_admin_id: string; activation_code: string; expires_at: string } | undefined;
  if (!row) {
    return NextResponse.json({ error: "No se pudo generar el token" }, { status: 500 });
  }

  revalidatePath(`/communities/${communityId}`);
  revalidatePath("/communities");

  const result = {
    communityAdminId: row.community_admin_id,
    activationCode: row.activation_code,
    expiresAt: row.expires_at,
  };

  return NextResponse.json(result);
}
