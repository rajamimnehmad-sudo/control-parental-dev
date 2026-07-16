"use client";

import { Send } from "lucide-react";
import { useActionState } from "react";
import { emptyState } from "@/lib/action-state";
import { createAnnouncementAction } from "@/lib/actions";
import type { CommunitySummary } from "@/lib/types";

export function AnnouncementForm({ communities }: { communities: CommunitySummary[] }) {
  const [state, action, pending] = useActionState(createAnnouncementAction, emptyState);
  return (
    <form action={action} className="grid gap-4 rounded-md border border-line bg-white p-5 shadow-soft">
      <div className="grid gap-4 sm:grid-cols-2">
        <label className="field">Comunidad
          <select className="input" name="communityId" required defaultValue="">
            <option value="" disabled>Elegí una comunidad</option>
            {communities.map((community) => <option key={community.community_id} value={community.community_id}>{community.name}</option>)}
          </select>
        </label>
        <label className="field">Destinatarios
          <select className="input" name="targetRole" defaultValue="all">
            <option value="all">Usuarios y administradores</option>
            <option value="user">Solo usuarios</option>
            <option value="admin">Solo administradores</option>
          </select>
        </label>
      </div>
      <label className="field">Título
        <input className="input" name="title" minLength={2} maxLength={80} required />
      </label>
      <label className="field">Mensaje
        <textarea className="input min-h-32" name="body" minLength={2} maxLength={500} required />
      </label>
      <label className="field sm:max-w-sm">Vence (opcional)
        <input className="input" name="expiresAt" type="datetime-local" />
      </label>
      <div className="flex flex-wrap items-center gap-3">
        <button className="button button-primary" type="submit" disabled={pending || communities.length === 0}>
          <Send className="h-4 w-4" />{pending ? "Enviando" : "Guardar y enviar"}
        </button>
        {state.message ? <p className={state.ok ? "text-sm font-medium text-teal-700" : "text-sm font-medium text-danger"}>{state.message}</p> : null}
      </div>
    </form>
  );
}
