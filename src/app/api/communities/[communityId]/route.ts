import { revalidatePath } from "next/cache";
import { NextResponse } from "next/server";
import { createClient } from "@/lib/supabase/server";

type Props = {
  params: Promise<{ communityId: string }>;
};

export async function DELETE(_request: Request, { params }: Props) {
  const { communityId } = await params;
  const supabase = await createClient();
  const { data, error } = await supabase.rpc("super_admin_delete_community", {
    target_community_id: communityId,
  });

  if (error) {
    return NextResponse.json({ error: error.message }, { status: 400 });
  }

  revalidatePath("/communities");
  revalidatePath(`/communities/${communityId}`);

  return NextResponse.json({ ok: true, deleted: data ?? 0 });
}
