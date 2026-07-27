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
    description: "Panel Super Admin para comunidades, licencias y dispositivos.",
    robots: {
      index: false,
      follow: false,
      nocache: true,
    },
    openGraph: {
      title: "Super Admin | Content Filter",
      description: "Administración segura de comunidades y dispositivos.",
      images: [{ url: imageUrl, width: 1731, height: 908, alt: "Content Filter Super Admin" }],
    },
    twitter: {
      card: "summary_large_image",
      title: "Super Admin | Content Filter",
      description: "Administración segura de comunidades y dispositivos.",
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
