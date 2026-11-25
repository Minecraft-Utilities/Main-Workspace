import Skeleton from "@/components/skeleton/ui/skeleton";

export default function CapeHeaderSkeleton() {
  return (
    <header className="w-full text-left">
      <Skeleton className="h-9 w-72 rounded-md sm:h-10 sm:w-96" />
    </header>
  );
}
