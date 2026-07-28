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
    title: "Glosh Control Center",
    description: "Administración central de comunidades, protección y dispositivos Glosh.",
    robots: {
      index: false,
      follow: false,
      nocache: true,
    },
    openGraph: {
      title: "Glosh Control Center",
      description: "Protección clara. Control responsable.",
      images: [{ url: imageUrl, width: 1729, height: 910, alt: "Glosh Control Center · Protección clara. Control responsable." }],
    },
    twitter: {
      card: "summary_large_image",
      title: "Glosh Control Center",
      description: "Protección clara. Control responsable.",
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
