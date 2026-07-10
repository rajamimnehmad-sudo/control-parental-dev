import { revalidatePath } from "next/cache";
import { NextResponse } from "next/server";
import { createClient } from "@/lib/supabase/server";

type Props = {
  params: Promise<{ communityId: string; adminId: string }>;
};

export async function DELETE(_request: Request, { params }: Props) {
  const { communityId, adminId } = await params;
  const supabase = await createClient();
  const { data, error } = await supabase.rpc("super_admin_delete_community_admin", {
    target_community_id: communityId,
    target_admin_id: adminId,
  });

  if (error) {
    return NextResponse.json({ error: error.message }, { status: 400 });
  }

  revalidatePath(`/communities/${communityId}`);
  revalidatePath("/communities");

  return NextResponse.json({ ok: true, deleted: data ?? 0 });
}
