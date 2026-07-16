#!/usr/bin/env python3
"""Build DAG's tiny multilingual intent model using only Python's stdlib.

The model is a multinomial linear classifier over hashed character and word
n-grams. Training data is deliberately small, reviewed and product-specific;
the deterministic artifact is consumed entirely on-device.
"""

from __future__ import annotations

import math
import random
import re
import struct
import unicodedata
from collections import Counter
from pathlib import Path


CLASSES = (
    "general",
    "sexual",
    "dating",
    "gambling",
    "drugs",
    "violence",
    "sensitive_context",
)
BUCKETS = 4096
EPOCHS = 90
LEARNING_RATE = 0.12
L2 = 0.00008
SEED = 20260715
MAGIC = b"DAGTXT1\0"
ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / "app-user/src/main/assets/dag/dag_text_intent_v1.bin"


SEEDS = {
    "general": (
        "horario del banco", "turnos mi buenos aires", "comprar una heladera", "receta de pan",
        "clima de mañana", "mapa de buenos aires", "noticias económicas", "pagar impuestos",
        "correo electrónico", "cómo arreglar una canilla", "historia argentina", "tienda de muebles",
        "qué significa esta palabra", "farmacia de turno", "transporte público", "aprender matemática",
        "galería de arte", "galería de productos", "fotos de flores", "imágenes de una ciudad",
        "contenido general", "sitio de información general", "página con imágenes de ejemplo", "resultados generales",
        "gmail iniciar sesión", "abrir el correo de gmail", "entrar a mi cuenta de correo",
        "weather tomorrow", "bank opening hours", "buy a refrigerator", "bread recipe",
        "government appointments", "send an email", "public transport map", "learn mathematics",
        "home improvement store", "economic news", "computer repair guide", "family activities",
        "art gallery", "product image gallery", "photos of flowers", "city pictures",
        "general content", "general information website", "page with example images", "general results",
        "זמני תפילה היום", "תחזית מזג אוויר", "מתכון ללחם", "שעות פתיחה של הבנק",
        "לימוד מתמטיקה", "חדשות כלכלה", "תחבורה ציבורית", "חנות רהיטים",
        "תשלום מיסים", "שליחת דואר אלקטרוני", "היסטוריה יהודית", "תיקון מחשב",
        "גלריית אמנות", "תמונות מוצרים", "תמונות של פרחים", "תמונות של עיר",
        "תוכן כללי", "אתר מידע כללי", "דף עם תמונות לדוגמה", "תוצאות כלליות",
        "איפה עושים תור למשרד ממשלתי", "איפה קונים ספרי לימוד", "מי נותן שירות לתיקון בית",
        "זמני שבת בבואנוס איירס", "שיעור תורה פרשת השבוע", "פרשת השבוע עם פירושים",
        "לוח שנה יהודי", "זמני הדלקת נרות", "בית כנסת קרוב אלי", "שיעור גמרא יומי",
        "חדשות מישראל היום", "תוצאות משחק כדורגל", "כניסה לחשבון דואר", "קביעת תור לרופא",
        "חנות מוצרי חשמל", "לקנות מקרר חדש", "מתכון למרק ירקות", "שירותי עירייה",
        "תשלום חשבון חשמל", "מפת רחובות העיר", "תמונות של גינה", "מוזיאון אמנות",
        "לימוד אנגלית למתחילים", "ספרי קודש למכירה", "תחזית מזג אוויר לשבת",
        "איפה קונים מזוזה כשרה", "חנות טליתות קרובה", "לקנות סידור תפילה חדש",
        "לקנות תפילין לבר מצווה", "לקנות חומש עם פירוש", "לקנות כיפה לילד",
        "איפה אפשר לקנות נרות שבת", "איפה קונים ארבעת המינים", "חנות יודאיקה באזור",
        "לקנות שולחן לבית", "לקנות ספר לימוד", "לקנות מתנה ליום הולדת",
    ),
    "sexual": (
        "videos para adultos", "mujeres sin ropa", "fotos eróticas", "contenido caliente",
        "galería de mujeres sin ropa", "fotos de chicas sin ropa", "chicas sin ropa para adultos",
        "escenas íntimas explícitas", "modelos desnudas", "imágenes subidas de tono", "contenido para mayores",
        "adult videos", "women without clothes", "erotic pictures", "hot explicit content",
        "uncensored intimate scenes", "naked models gallery", "adult only images", "explicit bedroom videos",
        "private collection of nude pictures",
        "סרטים למבוגרים", "נשים בלי בגדים", "תמונות אירוטיות", "תוכן מיני מפורש",
        "סצנות אינטימיות ללא צנזורה", "גלריית נשים עירומות", "סרטונים חמים", "תמונות למבוגרים בלבד",
        "אוסף פרטי של צילומי עירום", "גלריה פרטית של עירום", "תמונות עירום פרטיות",
    ),
    "dating": (
        "conocer solteras cerca", "encuentros casuales", "chatear con chicas para salir", "buscar pareja online",
        "citas sin compromiso", "personas disponibles esta noche", "app para conocer mujeres", "encuentros privados",
        "meet single women nearby", "casual encounters", "chat with girls to date", "find a partner online",
        "no strings attached dates", "people available tonight", "meet women app", "private encounters",
        "meet someone with no commitment", "find singles available tonight",
        "להכיר רווקות קרוב", "מפגשים מזדמנים", "צ'אט עם נשים לדייט", "מציאת זוגיות באינטרנט",
        "דייטים ללא מחויבות", "פנויים להערב", "אפליקציה להיכרויות", "מפגשים פרטיים",
        "שיחה פרטית עם רווקות באזור", "קשר אינטימי בלי מחויבות",
    ),
    "gambling": (
        "dónde apostar un partido", "ruleta con dinero real", "cuotas para apostar", "tragamonedas online",
        "ganar plata apostando", "casino con premio", "pronósticos para apuestas", "jugar poker por dinero",
        "poner dinero al resultado de un partido", "arriesgar plata por quién gana", "dinero por acertar el marcador",
        "where to bet on a match", "real money roulette", "betting odds today", "online slot machines",
        "make money betting", "casino cash prize", "sports betting tips", "play poker for money",
        "איפה להמר על משחק", "רולטה בכסף אמיתי", "יחסי הימורים", "מכונות מזל באינטרנט",
        "להרוויח כסף מהימורים", "פרס כספי בקזינו", "טיפים להימורי ספורט", "פוקר על כסף",
    ),
    "drugs": (
        "conseguir drogas cerca", "comprar porro", "dealer en mi zona", "cómo drogarse",
        "pastillas recreativas", "venta de cocaína", "comprar sustancias ilegales", "dónde venden marihuana",
        "comprar merca", "vendedor de merca", "quién consigue droga en el barrio",
        "get drugs nearby", "buy weed", "dealer near me", "how to get high",
        "recreational pills", "cocaine for sale", "buy illegal substances", "where to buy marijuana",
        "לקנות סמים קרוב", "לקנות מריחואנה", "סוחר סמים באזור", "איך להתמסטל",
        "כדורים לשימוש פנאי", "קוקאין למכירה", "לקנות חומרים אסורים", "איפה מוכרים קנאביס",
        "פרטי קשר של סוחר סמים",
    ),
    "violence": (
        "videos de muertes reales", "peleas con sangre", "torturas sin censura", "accidentes fatales explícitos",
        "videos de gente descuartizada", "personas desmembradas en video", "cuerpos descuartizados reales",
        "imágenes de cuerpos mutilados", "ejecuciones grabadas", "asesinatos reales en video", "violencia gráfica extrema",
        "real death videos", "bloody fights", "uncensored torture", "graphic fatal accidents",
        "pictures of mutilated bodies", "recorded executions", "real murder footage", "extreme graphic violence",
        "סרטוני מוות אמיתיים", "קטטות עם דם", "עינויים ללא צנזורה", "תאונות קטלניות קשות",
        "סרט אמיתי של רצח", "וידאו קשה של הריגה", "הריגה אמיתית מצולמת",
        "תמונות של גופות", "הוצאות להורג מצולמות", "רצח אמיתי בווידאו", "אלימות גרפית קיצונית",
    ),
    "sensitive_context": (
        "educación sexual para padres", "salud sexual explicada por médicos", "prevención de abuso infantil",
        "clase de biología sobre reproducción", "tratamiento médico para adicciones", "historia de la prohibición de drogas",
        "leyes sobre apuestas", "prevención de violencia escolar", "halajá sobre relaciones familiares",
        "tratamiento médico del consumo de cocaína", "guía clínica para dejar sustancias ilegales",
        "sexual education for parents", "sexual health explained by doctors", "child abuse prevention",
        "parent guide to sexual health", "health guide for parents about reproduction",
        "biology lesson about reproduction", "medical addiction treatment", "history of drug prohibition",
        "gambling laws", "school violence prevention", "halacha about family relationships",
        "חינוך מיני להורים", "בריאות מינית לפי רופאים", "מניעת פגיעה בילדים",
        "שיעור ביולוגיה על רבייה", "טיפול רפואי בהתמכרות", "היסטוריה של איסור סמים",
        "חוקי הימורים", "מניעת אלימות בבית ספר", "הלכה בנושא יחסי משפחה",
    ),
}

PREFIXES = {
    "es": ("", "buscar ", "quiero encontrar ", "mostrame ", "sitio con "),
    "en": ("", "search for ", "find ", "show me ", "website with "),
    "he": ("", "לחפש ", "אני רוצה ", "תראה לי ", "אתר עם "),
}

SUFFIXES = {
    "es": ("", " online", " gratis", " ahora"),
    "en": ("", " online", " free", " now"),
    "he": ("", " באינטרנט", " בחינם", " עכשיו"),
}

EXPECTED = (
    ("general", "cómo sacar turno para renovar el documento"),
    ("general", "best recipe for vegetable soup"),
    ("general", "איפה משלמים ארנונה"),
    ("general", "frávega heladeras ofertas"),
    ("general", "turnos buenos aires mi ciudad"),
    ("general", "gmail iniciar sesión"),
    ("general", "resultado del partido de argentina"),
    ("general", "flower and garden pictures"),
    ("general", "noticias de israel hoy"),
    ("general", "זמני שבת בבואנוס איירס"),
    ("general", "שיעור תורה פרשת השבוע"),
    ("sexual", "galería de chicas sin ropa"),
    ("sexual", "fotos de chicas en bolas"),
    ("sexual", "show uncensored adult bedroom clips"),
    ("sexual", "תמונות נשים בלי לבוש"),
    ("dating", "hablar con solteras que viven cerca"),
    ("dating", "meet someone tonight without commitment"),
    ("dating", "להכיר נשים פנויות הערב"),
    ("gambling", "poner dinero al resultado del partido"),
    ("gambling", "tragamonedas con premios en efectivo"),
    ("gambling", "cash roulette and slot games"),
    ("gambling", "לשים כסף על תוצאת משחק"),
    ("drugs", "quién vende merca cerca de acá"),
    ("drugs", "comprar falopa en mi zona"),
    ("drugs", "find a local seller for illegal pills"),
    ("drugs", "מי מוכר חומרים אסורים"),
    ("violence", "grabaciones reales con mucha sangre"),
    ("violence", "videos de gente descuartizada"),
    ("violence", "uncensored footage of people being killed"),
    ("violence", "סרט אמיתי של הריגה קשה"),
    ("sensitive_context", "guía médica para tratar consumo de cocaína"),
    ("sensitive_context", "tratamiento psicológico para adicción al juego"),
    ("sensitive_context", "parent guide to reproductive health"),
    ("sensitive_context", "שיעור רפואי על בריאות מינית"),
    ("general", "consultar deuda de patente del auto"),
    ("general", "how to renew a passport appointment"),
    ("general", "comparison of washing machine prices"),
    ("general", "איפה אפשר לקנות תפילין"),
    ("general", "לוח זמני תפילה לחודש הבא"),
    ("sexual", "material explícito para mayores sin censura"),
    ("sexual", "private nude photo collection"),
    ("sexual", "watch erotic scenes without censorship"),
    ("sexual", "גלריה פרטית של תמונות עירום"),
    ("sexual", "סרטים מיניים למבוגרים בלבד"),
    ("dating", "sitio para encuentros íntimos sin compromiso"),
    ("dating", "chat privately with singles nearby tonight"),
    ("dating", "find casual partners in my neighborhood"),
    ("dating", "צ'אט פרטי עם פנויות באזור"),
    ("dating", "מפגש אינטימי ללא קשר רציני"),
    ("gambling", "casino que paga premios con dinero real"),
    ("gambling", "place cash wagers on tonight's game"),
    ("gambling", "online roulette for real money prizes"),
    ("gambling", "אתר להימורים בכסף אמיתי"),
    ("gambling", "לזכות בכסף במכונת מזל"),
    ("drugs", "contacto para conseguir pastillas ilegales"),
    ("drugs", "where can I purchase cocaine nearby"),
    ("drugs", "local seller of recreational narcotics"),
    ("drugs", "מספר טלפון של מוכר סמים"),
    ("drugs", "איפה קונים כדורים לא חוקיים"),
    ("violence", "grabaciones brutales de ejecuciones reales"),
    ("violence", "uncensored footage of graphic torture"),
    ("violence", "real accident victims with visible bodies"),
    ("violence", "תיעוד קשה של הוצאה להורג אמיתית"),
    ("violence", "סרטוני עינויים גרפיים ללא צנזורה"),
    ("sensitive_context", "manual clínico de prevención de adicciones"),
    ("sensitive_context", "research paper about gambling addiction treatment"),
    ("sensitive_context", "school lesson about preventing violent behavior"),
    ("sensitive_context", "מדריך להורים למניעת התמכרות"),
    ("sensitive_context", "מחקר רפואי על טיפול בהתמכרות להימורים"),
)


def language(text: str) -> str:
    if any("\u0590" <= char <= "\u05ff" for char in text):
        return "he"
    if any(char in text for char in "áéíóúñ¿"):
        return "es"
    spanish = {"del", "para", "una", "cómo", "dónde", "con", "comprar", "buscar"}
    return "es" if spanish.intersection(text.lower().split()) else "en"


def normalize(text: str) -> str:
    decomposed = unicodedata.normalize("NFKD", text.lower())
    stripped = "".join(char for char in decomposed if unicodedata.category(char) != "Mn")
    translated = stripped.translate(str.maketrans("013457", "oieast"))
    return re.sub(r"[^\w]+", " ", translated, flags=re.UNICODE).strip()


def fnv1a(value: str) -> int:
    result = 0x811C9DC5
    for byte in value.encode("utf-8"):
        result ^= byte
        result = (result * 0x01000193) & 0xFFFFFFFF
    return result


def features(text: str) -> dict[int, float]:
    normalized = normalize(text)
    counts: Counter[int] = Counter()
    words = normalized.split()
    for word in words:
        padded = f"^{word}$"
        for size in (3, 4, 5):
            for offset in range(max(0, len(padded) - size + 1)):
                counts[fnv1a("c:" + padded[offset : offset + size]) % BUCKETS] += 1
        counts[fnv1a("w:" + word) % BUCKETS] += 2
    for left, right in zip(words, words[1:]):
        counts[fnv1a("b:" + left + " " + right) % BUCKETS] += 2
    norm = math.sqrt(sum(value * value for value in counts.values())) or 1.0
    return {index: value / norm for index, value in counts.items()}


def obfuscate(text: str, rng: random.Random) -> str:
    substitutions = {"o": "0", "i": "1", "e": "3", "a": "4", "s": "5", "t": "7"}
    chars = list(text)
    candidates = [index for index, char in enumerate(chars) if char.lower() in substitutions]
    rng.shuffle(candidates)
    for index in candidates[: max(1, min(3, len(candidates) // 4))]:
        chars[index] = substitutions[chars[index].lower()]
    return "".join(chars)


def examples() -> list[tuple[int, str]]:
    rng = random.Random(SEED)
    generated: set[tuple[int, str]] = set()
    for class_index, class_name in enumerate(CLASSES):
        for seed in SEEDS[class_name]:
            lang = language(seed)
            generated.add((class_index, seed))
            generated.add((class_index, rng.choice(PREFIXES[lang]) + seed + rng.choice(SUFFIXES[lang])))
            if class_name not in ("general", "sensitive_context"):
                generated.add((class_index, obfuscate(seed, rng)))
    return sorted(generated, key=lambda item: (item[0], item[1]))


def train(samples: list[tuple[int, str]]) -> tuple[list[list[float]], list[float]]:
    rng = random.Random(SEED)
    weights = [[0.0] * BUCKETS for _ in CLASSES]
    biases = [0.0] * len(CLASSES)
    encoded = [(label, features(text)) for label, text in samples]
    for epoch in range(EPOCHS):
        rng.shuffle(encoded)
        rate = LEARNING_RATE / (1.0 + epoch * 0.025)
        for label, vector in encoded:
            logits = [biases[class_index] for class_index in range(len(CLASSES))]
            for class_index in range(len(CLASSES)):
                row = weights[class_index]
                logits[class_index] += sum(row[index] * value for index, value in vector.items())
            maximum = max(logits)
            exponentials = [math.exp(value - maximum) for value in logits]
            total = sum(exponentials)
            probabilities = [value / total for value in exponentials]
            for class_index, probability in enumerate(probabilities):
                error = (1.0 if class_index == label else 0.0) - probability
                biases[class_index] += rate * error
                row = weights[class_index]
                for index, value in vector.items():
                    row[index] += rate * (error * value - L2 * row[index])
    return weights, biases


def predict(weights: list[list[float]], biases: list[float], text: str) -> tuple[str, float]:
    vector = features(text)
    logits = [biases[index] + sum(weights[index][feature] * value for feature, value in vector.items()) for index in range(len(CLASSES))]
    maximum = max(logits)
    probabilities = [math.exp(value - maximum) for value in logits]
    total = sum(probabilities)
    probabilities = [value / total for value in probabilities]
    best = max(range(len(CLASSES)), key=probabilities.__getitem__)
    return CLASSES[best], probabilities[best]


def write_model(weights: list[list[float]], biases: list[float]) -> None:
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    with OUTPUT.open("wb") as output:
        output.write(MAGIC)
        output.write(struct.pack("<III", 1, BUCKETS, len(CLASSES)))
        output.write(struct.pack(f"<{len(biases)}f", *biases))
        for row in weights:
            output.write(struct.pack(f"<{len(row)}f", *row))


def main() -> None:
    samples = examples()
    weights, biases = train(samples)
    failures = []
    for expected, text in EXPECTED:
        actual, score = predict(weights, biases, text)
        if actual != expected:
            failures.append(f"{expected} != {actual} ({score:.3f}): {text}")
    if failures:
        raise SystemExit("Holdout failures:\n" + "\n".join(failures))
    write_model(weights, biases)
    print(f"wrote {OUTPUT.relative_to(ROOT)} ({OUTPUT.stat().st_size} bytes, {len(samples)} samples)")


if __name__ == "__main__":
    main()
