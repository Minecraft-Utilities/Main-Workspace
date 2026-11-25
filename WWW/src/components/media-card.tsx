import { cn } from "@/common/utils";
import OwnerCount from "@/components/owner-count";
import SimpleLink from "@/components/simple-link";
import Card, { CardContent } from "@/components/ui/card";
import Image from "next/image";

export const MEDIA_GRID = "grid grid-cols-2 sm:grid-cols-3 xl:grid-cols-5 gap-4";

export interface MediaCardProps {
  href: string;
  imageUrl: string;
  alt: string;
  caption?: string;
  count?: number;
  className?: string;
}

export function MediaCard({ href, imageUrl, alt, caption, count, className }: MediaCardProps) {
  return (
    <SimpleLink href={href} className="group block w-full">
      <Card
        className={cn("overflow-hidden transition-transform duration-200 hover:-translate-y-0.5", className)}
      >
        <CardContent className="relative flex min-h-0 flex-1 flex-col items-center justify-center p-0">
          <div className="flex items-center justify-center px-4 pt-4 pb-10">
            <Image
              src={imageUrl}
              alt={alt}
              width={184}
              height={184}
              unoptimized
              className="mx-auto h-46 w-auto object-contain"
            />
          </div>
          <div className="absolute inset-x-0 bottom-0 flex flex-col items-center gap-0.5 bg-linear-to-t from-black/70 to-transparent px-3 pt-8 pb-3">
            {caption ? (
              <p className="w-full truncate text-center text-sm font-medium text-white">{caption}</p>
            ) : null}
            {count !== undefined ? (
              <OwnerCount count={count} name="owners" variant="overlay" ariaHidden />
            ) : null}
          </div>
        </CardContent>
      </Card>
    </SimpleLink>
  );
}
