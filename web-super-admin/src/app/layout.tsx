import type { Metadata } from "next";
import { headers } from "next/headers";
import "./globals.css";

export async function generateMetadata(): Promise<Metadata> {
  const requestHeaders = await headers();
  const host = requestHeaders.get("x-forwarded-host") ?? requestHeaders.get("host");
  const protocol = requestHeaders.get("x-forwarded-proto") ?? "https";
  const origin = host ? `${protocol}://${host}` : "https://content-filter-super-admin.invalid";
  const imageUrl = new URL("/og.png", origin).toString();

  return {
    title: "Super Admin | Content Filter",
    description: "Panel Super Admin para comunidades, licencias, dispositivos y consumo DAG.",
    robots: {
      index: false,
      follow: false,
      nocache: true,
    },
    openGraph: {
      title: "Consumo DAG | Content Filter",
      description: "Control mensual seguro del buscador protegido DAG.",
      images: [{ url: imageUrl, width: 1731, height: 908, alt: "Consumo DAG · Control mensual seguro" }],
    },
    twitter: {
      card: "summary_large_image",
      title: "Consumo DAG | Content Filter",
      description: "Control mensual seguro del buscador protegido DAG.",
      images: [imageUrl],
    },
  };
}

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="es">
      <body>{children}</body>
    </html>
  );
}
