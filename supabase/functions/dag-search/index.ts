import { createClient } from "@supabase/supabase-js";

type DagSearchPayload = {
    action?: "search" | "suggest";
    device_id?: string;
    query?: string;
    language?: string;
    page?: number;
};

type BraveWebResult = {
    title?: string;
    url?: string;
    description?: string;
};

const rateWindows = new Map<string, { minute: number; count: number }>();
const suggestionRateWindows = new Map<
    string,
    { minute: number; count: number }
>();

Deno.serve(async (request) => {
    if (request.method !== "POST") {
        return json({ error: "Método no permitido." }, 405);
    }

    const payload = await request.json().catch(() => ({})) as DagSearchPayload;
    const deviceId = payload.device_id?.trim() ?? "";
    const query = payload.query?.trim() ?? "";
    const language = supportedLanguage(payload.language);
    const action = payload.action === "suggest" ? "suggest" : "search";
    const page = Number.isInteger(payload.page) ? Number(payload.page) : 0;
    if (
        !isUuid(deviceId) ||
        query.length < 2 ||
        query.length > (action === "suggest" ? 200 : 400) ||
        wordCount(query) > (action === "suggest" ? 24 : 50) ||
        page < 0 ||
        page > 1
    ) {
        return json({ error: "La búsqueda no es válida." }, 400);
    }

    const supabaseUrl = requiredEnv("SUPABASE_URL");
    const anonKey = requiredEnv("SUPABASE_ANON_KEY");
    const deviceToken = request.headers.get("x-device-token") ?? "";
    const authHeader = request.headers.get("authorization") ??
        `Bearer ${anonKey}`;
    const client = createClient(supabaseUrl, anonKey, {
        global: {
            headers: {
                authorization: authHeader,
                "x-device-token": deviceToken,
            },
        },
    });
    if (action === "suggest") {
        if (!consumeSuggestionRate(deviceId)) {
            return json({
                error: "Esperá un momento para ver más sugerencias.",
            }, 429);
        }
    } else if (!consumeRate(deviceId)) {
        return json(
            { error: "Esperá un minuto antes de volver a buscar." },
            429,
        );
    }

    if (action === "suggest") {
        const { data: authorized, error: authorizationError } = await client
            .rpc(
                "authorize_dag_suggestions",
                { p_device_id: deviceId },
            );
        if (authorizationError) {
            return json(
                { error: "No se pudo comprobar el acceso a DAG." },
                503,
            );
        }
        if (authorized !== true) {
            return json({
                error: "DAG no está habilitado para este dispositivo.",
            }, 403);
        }
        return googleSuggestions(query, language);
    }

    const braveKey = Deno.env.get("BRAVE_SEARCH_API_KEY")?.trim() ?? "";
    if (!braveKey) {
        return json({ error: "El buscador todavía no está configurado." }, 503);
    }

    const { data: authorization, error: authorizationError } = await client.rpc(
        "authorize_and_consume_dag_search",
        {
            p_device_id: deviceId,
        },
    );
    if (authorizationError) {
        return json({ error: "No se pudo comprobar el acceso a DAG." }, 503);
    }
    if (authorization === "unauthorized") {
        return json(
            { error: "DAG no está habilitado para este dispositivo." },
            403,
        );
    }
    if (authorization !== "allowed") {
        return json({
            error:
                "Alcanzaste el límite de búsquedas disponible para este mes.",
        }, 429);
    }

    const endpoint = new URL("https://api.search.brave.com/res/v1/web/search");
    endpoint.searchParams.set("q", query);
    endpoint.searchParams.set("count", "10");
    endpoint.searchParams.set("offset", String(page));
    endpoint.searchParams.set("country", "AR");
    endpoint.searchParams.set("search_lang", language);
    endpoint.searchParams.set(
        "ui_lang",
        language === "he" ? "he-IL" : language === "en" ? "en-US" : "es-AR",
    );
    endpoint.searchParams.set("safesearch", "strict");
    endpoint.searchParams.set("spellcheck", "1");
    endpoint.searchParams.set("text_decorations", "0");

    const response = await fetch(endpoint, {
        method: "GET",
        headers: {
            accept: "application/json",
            "accept-encoding": "gzip",
            "x-subscription-token": braveKey,
        },
    });
    if (!response.ok) {
        return json({
            error: "El buscador no está disponible en este momento.",
        }, response.status === 429 ? 429 : 502);
    }

    const body = await response.json().catch(() => ({}));
    const remoteResults = Array.isArray(body?.web?.results)
        ? body.web.results as BraveWebResult[]
        : [];
    const results = remoteResults
        .map((result) => ({
            title: cleanText(result.title, 240),
            url: cleanHttpsUrl(result.url),
            description: cleanText(result.description, 600),
        }))
        .filter((result) => result.title.length > 0 && result.url.length > 0)
        .slice(0, 10);
    return json({
        results,
        has_more_results: body?.query?.more_results_available === true,
        diagnostics: {
            brave_received: remoteResults.length,
            server_rejected: remoteResults.length - results.length,
        },
    }, 200);
});

async function googleSuggestions(
    query: string,
    language: "es" | "en" | "he",
): Promise<Response> {
    const endpoint = new URL(
        "https://suggestqueries.google.com/complete/search",
    );
    endpoint.searchParams.set("client", "firefox");
    endpoint.searchParams.set("q", query);
    endpoint.searchParams.set("hl", language);
    endpoint.searchParams.set("gl", "ar");

    const response = await fetch(endpoint, {
        method: "GET",
        headers: {
            accept: "application/json",
            "accept-encoding": "gzip",
        },
    });
    if (!response.ok) {
        return json(
            { error: "Las sugerencias no están disponibles en este momento." },
            response.status === 429 ? 429 : 502,
        );
    }

    const body = await response.json().catch(() => []);
    const values = Array.isArray(body) && Array.isArray(body[1])
        ? body[1] as unknown[]
        : [];
    const seen = new Set<string>();
    const suggestions = values
        .map((value) => cleanText(value, 200))
        .filter((value) => {
            const normalized = normalizeSuggestion(value);
            if (normalized.length < 2 || seen.has(normalized)) return false;
            seen.add(normalized);
            return true;
        })
        .slice(0, 8);
    const correction = closestCorrection(query, suggestions);
    return json({
        suggestions,
        corrected_query: correction,
    }, 200);
}

function closestCorrection(
    query: string,
    suggestions: string[],
): string | null {
    const normalizedQuery = normalizeSuggestion(query);
    const queryWords = wordCount(normalizedQuery);
    const threshold = Math.min(
        3,
        Math.max(1, Math.ceil(normalizedQuery.length * 0.2)),
    );
    const candidates = suggestions
        .map((suggestion) => ({
            suggestion,
            normalized: normalizeSuggestion(suggestion),
        }))
        .filter(({ normalized }) =>
            normalized !== normalizedQuery &&
            wordCount(normalized) === queryWords &&
            !normalized.startsWith(`${normalizedQuery} `) &&
            Math.abs(normalized.length - normalizedQuery.length) <= threshold
        )
        .map((candidate) => ({
            ...candidate,
            distance: editDistance(normalizedQuery, candidate.normalized),
        }))
        .filter(({ distance }) => distance <= threshold)
        .sort((left, right) => left.distance - right.distance);
    return candidates[0]?.suggestion ?? null;
}

function normalizeSuggestion(value: string): string {
    return value
        .normalize("NFD")
        .replace(/\p{Diacritic}/gu, "")
        .toLocaleLowerCase()
        .replace(/[^\p{Letter}\p{Number}]+/gu, " ")
        .trim()
        .replace(/\s+/g, " ");
}

function editDistance(left: string, right: string): number {
    const previous = Array.from(
        { length: right.length + 1 },
        (_, index) => index,
    );
    for (let leftIndex = 1; leftIndex <= left.length; leftIndex += 1) {
        const current = [leftIndex];
        for (
            let rightIndex = 1;
            rightIndex <= right.length;
            rightIndex += 1
        ) {
            const cost = left[leftIndex - 1] === right[rightIndex - 1] ? 0 : 1;
            current[rightIndex] = Math.min(
                current[rightIndex - 1] + 1,
                previous[rightIndex] + 1,
                previous[rightIndex - 1] + cost,
            );
        }
        previous.splice(0, previous.length, ...current);
    }
    return previous[right.length];
}

function consumeRate(deviceId: string): boolean {
    const minute = Math.floor(Date.now() / 60_000);
    const current = rateWindows.get(deviceId);
    if (!current || current.minute !== minute) {
        rateWindows.set(deviceId, { minute, count: 1 });
        return true;
    }
    if (current.count >= 20) return false;
    current.count += 1;
    return true;
}

function consumeSuggestionRate(deviceId: string): boolean {
    const minute = Math.floor(Date.now() / 60_000);
    const current = suggestionRateWindows.get(deviceId);
    if (!current || current.minute !== minute) {
        suggestionRateWindows.set(deviceId, { minute, count: 1 });
        return true;
    }
    if (current.count >= 60) return false;
    current.count += 1;
    return true;
}

function supportedLanguage(value?: string): "es" | "en" | "he" {
    return value === "en" || value === "he" ? value : "es";
}

function cleanText(value: unknown, maxLength: number): string {
    if (typeof value !== "string") return "";
    return value.replace(/[\u0000-\u001f\u007f]/g, " ").replace(/\s+/g, " ")
        .trim().slice(0, maxLength);
}

function cleanHttpsUrl(value: unknown): string {
    if (typeof value !== "string" || value.length > 2048) return "";
    try {
        const url = new URL(value);
        return url.protocol === "https:" ? url.toString() : "";
    } catch {
        return "";
    }
}

function wordCount(value: string): number {
    return value.split(/\s+/).filter(Boolean).length;
}

function isUuid(value: string): boolean {
    return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i
        .test(value);
}

function requiredEnv(name: string): string {
    const value = Deno.env.get(name)?.trim();
    if (!value) throw new Error(`Missing ${name}`);
    return value;
}

function json(body: unknown, status: number): Response {
    return new Response(JSON.stringify(body), {
        status,
        headers: {
            "content-type": "application/json; charset=utf-8",
            "cache-control": "no-store, max-age=0",
            "referrer-policy": "no-referrer",
            "x-content-type-options": "nosniff",
        },
    });
}
