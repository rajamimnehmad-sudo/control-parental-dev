import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "@supabase/supabase-js";
import queue from "./queue.json" with { type: "json" };

const BUCKET = "gloshia-private-review";
const SESSION_ID = "1a2cd620-d53e-4d4d-bade-84779ec1c7f2";
const BOOTSTRAP_TOKEN_HASH = "a153383ad2b6a372f636e85348aae40c1ad0fa3720834cb97dd6da5b67bed4c2";
const SIGNAL_NAMES: Record<string, string> = {
  explicit_or_nudity: "Desnudez o contenido explicito",
  underwear_or_swimwear: "Ropa interior o traje de baño",
  transparent_clothing: "Ropa transparente",
  neckline_or_chest: "Escote o pecho",
  abdomen_visible: "Abdomen visible",
  shoulder_or_armpit: "Hombro o axila",
  elbow_uncovered: "Brazo por encima del codo",
  knee_uncovered: "Rodilla o pierna descubierta",
  tight_clothing: "Ropa ajustada",
  sexualized_pose: "Pose sugerente",
};

const headers = {
  "access-control-allow-origin": "*",
  "access-control-allow-headers": "content-type",
  "access-control-allow-methods": "GET, POST, OPTIONS",
  "cache-control": "no-store",
  "content-security-policy": "default-src 'none'; img-src 'self' data:; style-src 'unsafe-inline'; script-src 'unsafe-inline'; connect-src 'self'; base-uri 'none'; frame-ancestors 'none'",
  "referrer-policy": "no-referrer",
  "x-content-type-options": "nosniff",
};

function json(data: unknown, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { ...headers, "content-type": "application/json; charset=utf-8" },
  });
}

async function digest(value: string) {
  const bytes = new TextEncoder().encode(value);
  const hash = await crypto.subtle.digest("SHA-256", bytes);
  return [...new Uint8Array(hash)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

function page() {
  return `<!doctype html><html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover"><title>GloshIA · Revision</title><style>
  *{box-sizing:border-box}body{margin:0;background:#0e1117;color:#f6f7fb;font-family:system-ui,-apple-system,sans-serif}main{max-width:680px;margin:auto;padding:16px 16px calc(28px + env(safe-area-inset-bottom))}.top{display:flex;align-items:center;justify-content:space-between;margin-bottom:12px}.brand{font-weight:800;font-size:20px}.progress{font-size:14px;color:#aab4c3}.bar{height:4px;background:#28303c;border-radius:4px;overflow:hidden;margin-bottom:14px}.fill{height:100%;background:#56df9b;transition:width .2s}.card{background:#171c25;border:1px solid #2a3341;border-radius:20px;overflow:hidden;box-shadow:0 16px 50px #0005}.photo{display:grid;place-items:center;background:#090b0f;min-height:44vh;max-height:58vh;touch-action:pan-y}.photo img{display:block;max-width:100%;max-height:58vh;object-fit:contain}.body{padding:15px}.hint{font-size:14px;color:#bbc5d2;margin:0 0 12px}.fixed{font-size:13px;color:#8e9aac;margin-bottom:12px}.signals{display:grid;gap:8px}.signal{border:1px solid #344052;background:#202733;color:#f7f8fb;border-radius:13px;padding:12px;text-align:left;font-size:15px}.signal.yes{border-color:#ff6577;background:#482631}.signal.no{border-color:#4cce91;background:#17382b;color:#caffdf}.quick{width:100%;margin:12px 0 0;border:1px solid #4cce91;color:#bfffdc;background:#132a22;border-radius:13px;padding:12px;font-weight:700}.save{position:sticky;bottom:10px;width:100%;margin-top:14px;border:0;border-radius:14px;padding:15px;background:#fff;color:#111;font-weight:800;font-size:16px}.save:disabled{opacity:.5}.msg{min-height:22px;text-align:center;color:#ff9ba7;font-size:13px;margin-top:8px}.done{text-align:center;padding:70px 15px}.done h1{font-size:28px}.sub{color:#aab4c3}button{cursor:pointer}</style></head><body><main><div class="top"><div class="brand">GloshIA Visual</div><div id="progress" class="progress">Cargando…</div></div><div class="bar"><div id="fill" class="fill"></div></div><section id="app" class="card"><div class="done">Cargando revisión…</div></section></main><script>
  const token=new URL(location.href).searchParams.get('token')||'';const endpoint='/functions/v1/gloshia-r3-review';let current=null;let choices={};
  const names=${JSON.stringify(SIGNAL_NAMES)};
  const esc=s=>String(s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  async function api(action,options={}){const r=await fetch(endpoint+'?token='+encodeURIComponent(token)+'&api='+action,{cache:'no-store',...options});const d=await r.json();if(!r.ok)throw new Error(d.error||'No se pudo conectar');return d}
  function render(d){document.querySelector('#progress').textContent=d.reviewed+' de '+d.total;document.querySelector('#fill').style.width=(d.total?d.reviewed/d.total*100:0)+'%';if(d.completed){document.querySelector('#app').innerHTML='<div class="done"><h1>¡Listo!</h1><p class="sub">Las '+d.total+' revisiones quedaron guardadas.</p></div>';return}current=d.item;choices={};const fixed=current.existing_positive_signals.map(x=>names[x]||x).join(', ');document.querySelector('#app').innerHTML='<div class="photo"><img src="'+endpoint+'?token='+encodeURIComponent(token)+'&image='+encodeURIComponent(current.sample_id)+'" alt="Foto para revisar"></div><div class="body"><p class="hint">Marcá todos los motivos que ves. Tocá una opción: rojo = sí, verde = no.</p>'+(fixed?'<div class="fixed">Ya confirmado: '+esc(fixed)+'</div>':'')+'<div class="signals">'+current.signals_to_review.map(s=>'<button class="signal" data-s="'+esc(s)+'">'+esc(names[s]||s)+'</button>').join('')+'</div><button id="none" class="quick">Ningún motivo adicional</button><button id="save" class="save">Guardar y seguir</button><div id="msg" class="msg"></div></div>';document.querySelectorAll('.signal').forEach(b=>b.onclick=()=>{const s=b.dataset.s;choices[s]=choices[s]==='positive'?'negative':'positive';b.classList.toggle('yes',choices[s]==='positive');b.classList.toggle('no',choices[s]==='negative')});document.querySelector('#none').onclick=()=>{current.signals_to_review.forEach(s=>choices[s]='negative');document.querySelectorAll('.signal').forEach(b=>{b.classList.remove('yes');b.classList.add('no')})};document.querySelector('#save').onclick=save}
  async function load(){try{render(await api('state'))}catch(e){document.querySelector('#app').innerHTML='<div class="done"><h1>No se pudo abrir</h1><p class="sub">'+esc(e.message)+'</p></div>'}}
  async function save(){const missing=current.signals_to_review.filter(s=>!choices[s]);const msg=document.querySelector('#msg');if(missing.length){msg.textContent='Falta decidir '+missing.length+' motivo'+(missing.length===1?'':'s')+'.';return}const b=document.querySelector('#save');b.disabled=true;b.textContent='Guardando…';msg.textContent='';try{render(await api('review',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({sample_id:current.sample_id,labels:choices})}))}catch(e){b.disabled=false;b.textContent='Guardar y seguir';msg.textContent=e.message}}
  load();
  </script></body></html>`;
}

Deno.serve(async (req: Request) => {
  try {
    if (req.method === "OPTIONS") return new Response(null, { status: 204, headers });
    const url = new URL(req.url);
    const token = url.searchParams.get("token") ?? "";
    if (token.length < 32) return json({ error: "Acceso no autorizado" }, 401);
    const client = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
      { auth: { persistSession: false, autoRefreshToken: false } },
    );
    const tokenHash = await digest(token);
    let { data: session } = await client.from("gloshia_r3_review_sessions").select("id,expires_at").eq("token_hash", tokenHash).maybeSingle();
    if (!session && tokenHash === BOOTSTRAP_TOKEN_HASH) {
      const expiresAt = new Date(Date.now() + 14 * 24 * 60 * 60 * 1000).toISOString();
      const { error: sessionError } = await client.from("gloshia_r3_review_sessions").upsert({ id: SESSION_ID, token_hash: tokenHash, title: "GloshIA R3 focused relabel", expires_at: expiresAt });
      if (sessionError) return json({ error: "No se pudo iniciar la revisión" }, 500);
      const { error: itemError } = await client.from("gloshia_r3_review_items").upsert(queue);
      if (itemError) return json({ error: "No se pudo preparar la revisión" }, 500);
      session = { id: SESSION_ID, expires_at: expiresAt };
    }
    if (!session || Date.parse(session.expires_at) <= Date.now()) return json({ error: "Acceso vencido o no autorizado" }, 401);

    const imageId = url.searchParams.get("image");
    if (req.method === "GET" && imageId) {
      const { data: item } = await client.from("gloshia_r3_review_items").select("object_path").eq("session_id", session.id).eq("sample_id", imageId).maybeSingle();
      if (!item) return new Response("No encontrada", { status: 404, headers });
      const { data, error } = await client.storage.from(BUCKET).download(item.object_path);
      if (error || !data) return new Response("No encontrada", { status: 404, headers });
      return new Response(data, { headers: { ...headers, "content-type": data.type || "image/jpeg", "cache-control": "private, max-age=300" } });
    }

    const action = url.searchParams.get("api");
    if (!action && req.method === "GET") return new Response(page(), { headers: { ...headers, "content-type": "text/html; charset=utf-8" } });

    const state = async () => {
      const { count: total } = await client.from("gloshia_r3_review_items").select("*", { count: "exact", head: true }).eq("session_id", session.id);
      const { count: reviewed } = await client.from("gloshia_r3_owner_reviews").select("*", { count: "exact", head: true }).eq("session_id", session.id);
      const { data: allItems } = await client.from("gloshia_r3_review_items").select("sample_id,existing_positive_signals,signals_to_review").eq("session_id", session.id).order("position");
      const { data: reviews } = await client.from("gloshia_r3_owner_reviews").select("sample_id").eq("session_id", session.id);
      const done = new Set((reviews ?? []).map((review) => review.sample_id));
      return { total: total ?? 0, reviewed: reviewed ?? 0, completed: (reviewed ?? 0) >= (total ?? 0), item: (allItems ?? []).find((item) => !done.has(item.sample_id)) ?? null };
    };

    if (req.method === "GET" && action === "state") return json(await state());
    if (req.method === "POST" && action === "review") {
      const body = await req.json();
      const { data: item } = await client.from("gloshia_r3_review_items").select("labels,signals_to_review").eq("session_id", session.id).eq("sample_id", body.sample_id).maybeSingle();
      if (!item) return json({ error: "Muestra inválida" }, 400);
      const labels = { ...item.labels };
      for (const signal of item.signals_to_review) {
        const value = body.labels?.[signal];
        if (value !== "positive" && value !== "negative") return json({ error: "Faltan decisiones" }, 400);
        labels[signal] = value;
      }
      const { error } = await client.from("gloshia_r3_owner_reviews").upsert({ session_id: session.id, sample_id: body.sample_id, labels, reviewer_id: "local-owner", reviewed_at: new Date().toISOString() });
      if (error) return json({ error: "No se pudo guardar" }, 500);
      return json(await state());
    }
    return json({ error: "Ruta inválida" }, 404);
  } catch (error) {
    console.error(error);
    return json({ error: "Error temporal" }, 500);
  }
});
