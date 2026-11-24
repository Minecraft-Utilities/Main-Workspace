import { capeRenderUrl, mcUtilsApi } from "@/common/mc-utils";
import CapeDetails from "@/components/cape/cape-details";
import CapePlayers from "@/components/cape/cape-players";
import CapePreview from "@/components/cape/cape-preview";
import { Metadata } from "next";
import { redirect } from "next/navigation";

export const dynamic = "force-dynamic";

export async function generateMetadata(props: PageProps<"/cape/[query]">): Promise<Metadata> {
  const { query } = await props.params;
  const { data: cape } = await mcUtilsApi.cape().fetch(query);

  if (!cape) {
    return {
      title: "Cape not found",
      description: "Cape not found",
      openGraph: {
        title: "Cape not found",
        description: "Cape not found",
      },
    };
  }

  const capeName = cape.name ?? "Minecraft Cape";
  return {
    title: `${capeName}: Minecraft Cape`,
    icons: {
      icon: cape.parts.FRONT,
    },
    description: `View the ${capeName} Minecraft cape on MC Utils: preview the design, see current wearers, and explore cape details.`,
    openGraph: {
      title: `${capeName}: Minecraft Cape`,
      description: `View the ${capeName} Minecraft cape on MC Utils: preview the design, see current wearers, and explore cape details.`,
      images: [
        {
          url: capeRenderUrl(cape.id),
        },
      ],
    },
  };
}

export default async function CapePage({ params }: PageProps<"/cape/[query]">) {
  const { query } = await params;

  const { data: cape } = await mcUtilsApi.cape().fetch(query);

  if (!cape) {
    return redirect("/capes");
  }

  return (
    <div className="mt-10 flex w-full flex-col items-center justify-center gap-10">
      <header className="w-full max-w-[980px] text-left">
        <h1 className="text-foreground text-4xl font-bold tracking-tight">{cape.name ?? "Unknown"} Cape</h1>
      </header>

      <div className="flex w-full max-w-[980px] flex-col gap-4 lg:flex-row">
        {/* Left: preview + details */}
        <div className="flex w-full min-w-0 flex-1 flex-col gap-4">
          <CapePreview cape={cape} />
          <CapeDetails cape={cape} />
        </div>

        {/* Right: players */}
        <div className="flex w-full min-w-0 flex-col gap-4 lg:max-w-xl">
          <CapePlayers cape={cape} />
        </div>
      </div>
    </div>
  );
}
