import { env } from "@/common/env";
import { SITE_DESCRIPTION } from "@/common/site";
import { isProduction } from "@/common/utils";
import { ViewportProvider } from "@/components/context/viewport-context";
import Footer from "@/components/footer";
import Navbar from "@/components/navbar/navbar";
import { Toaster } from "@/components/ui/toaster";
import type { Metadata, Viewport } from "next";
import localFont from "next/font/local";
import Script from "next/script";
import { QueryProvider } from "../providers/query-provider";
import "./styles/globals.css";

const siteFont = localFont({
  src: "./fonts/JetBrainsMono.ttf",
  weight: "100 800",
});

export const viewport: Viewport = { themeColor: "#101115" };
export const metadata: Metadata = {
  metadataBase: new URL(env.NEXT_PUBLIC_BASE_URL),
  title: {
    default: "MC Utils: Minecraft Player & Server Lookup",
    template: "%s | MC Utils",
  },
  description: SITE_DESCRIPTION,
  keywords: [
    "Minecraft player lookup",
    "Minecraft skin viewer",
    "Minecraft UUID lookup",
    "check Minecraft server status",
    "Minecraft cape viewer",
    "Minecraft profile lookup",
    "Java server status",
    "Bedrock server status",
  ],
  openGraph: {
    type: "website",
    locale: "en_US",
    title: "MC Utils: Minecraft Player & Server Lookup",
    siteName: "MC Utils",
    images: [
      {
        url: `${env.NEXT_PUBLIC_BASE_URL}/media/logo/logo.png`,
        width: 128,
        height: 128,
      },
    ],
  },
  icons: {
    icon: `${env.NEXT_PUBLIC_BASE_URL}/media/logo/logo.png`,
  },
  twitter: { card: "summary_large_image" },
};

export interface RootLayoutProps {
  readonly children: React.ReactNode;
}

export default function RootLayout({ children }: RootLayoutProps) {
  return (
    <html lang="en" className="h-full">
      {/* Analytics */}
      {isProduction() && (
        <Script
          src="https://analytics.fascinated.cc/script.js"
          data-website-id="83459095-69ee-4511-be2f-784217ba6a88"
          strategy="lazyOnload"
        />
      )}

      <QueryProvider>
        <ViewportProvider>
          <body className={`${siteFont.className} flex min-h-full w-full flex-col antialiased`}>
            <a
              href="#content"
              className="bg-background text-foreground ring-ring sr-only rounded-md px-4 py-2 text-sm font-medium shadow-md focus:not-sr-only focus:fixed focus:top-2 focus:left-2 focus:z-[100] focus:ring-2"
            >
              Skip to content
            </a>
            <Navbar />
            <main id="content" className="relative flex min-h-0 w-full flex-1 flex-col" tabIndex={-1}>
              <Toaster />
              <div className="z-1 mx-auto flex min-h-0 w-full max-w-[1200px] flex-1 flex-col px-4 md:px-6">
                <div className="flex min-h-0 flex-1 flex-col">{children}</div>
                <Footer />
              </div>
            </main>
          </body>
        </ViewportProvider>
      </QueryProvider>
    </html>
  );
}
