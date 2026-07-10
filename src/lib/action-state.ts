export type ActionState = {
  ok: boolean;
  message: string;
};

export const emptyState: ActionState = { ok: false, message: "" };
