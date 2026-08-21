export function cn(...parts: Array<string | false | null | undefined>) {
  return parts.filter(Boolean).join(" ");
}

export function formatDate(value: string | null | undefined, fallback = "Sin fecha") {
  if (!value) return fallback;
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return fallback;
  return new Intl.DateTimeFormat("es-AR", {
    dateStyle: "medium",
    timeStyle: "short",
    timeZone: "America/Argentina/Buenos_Aires",
  }).format(date);
}

export function formatShortDate(value: string | null | undefined, fallback = "Sin fecha") {
  if (!value) return fallback;
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return fallback;
  return new Intl.DateTimeFormat("es-AR", {
    dateStyle: "medium",
    timeZone: "America/Argentina/Buenos_Aires",
  }).format(date);
}

export function formatDateInput(value: string | null | undefined) {
  if (!value) return "";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "" : date.toISOString().slice(0, 10);
}

export function compactNumber(value: number | bigint | null | undefined) {
  return Number(value ?? 0).toLocaleString("es-AR");
}

export function capacitySnapshot(usedValue: number | bigint, maximumValue: number | null | undefined) {
  const used = Math.max(0, Number(usedValue));
  const maximum = maximumValue === null || maximumValue === undefined ? null : Math.max(0, Number(maximumValue));
  return {
    used,
    maximum,
    available: maximum === null ? null : Math.max(0, maximum - used),
    exceeded: maximum !== null && used > maximum,
  };
}
