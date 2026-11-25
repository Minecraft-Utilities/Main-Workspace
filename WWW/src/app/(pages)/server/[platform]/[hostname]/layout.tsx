import Background from "@/components/background";
import { Suspense } from "react";
import { getServer, SERVER_BACKGROUND_FALLBACK } from "./data";

export default async function ServerLayout({
  children,
  params,
}: {
  children: React.ReactNode;
  params: Promise<{ platform: string; hostname: string }>;
}) {
  const { platform, hostname } = await params;

  return (
    <>
      <Suspense fallback={<Background url={SERVER_BACKGROUND_FALLBACK} />}>
        <ServerBackground platform={platform} hostname={hostname} />
      </Suspense>
      {children}
    </>
  );
}

async function ServerBackground({ platform, hostname }: { platform: string; hostname: string }) {
  const { server } = await getServer(platform, hostname);

  return <Background url={server?.registryEntry?.backgroundImageUrl ?? SERVER_BACKGROUND_FALLBACK} />;
}
