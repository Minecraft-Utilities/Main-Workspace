import ProfileFieldsSkeleton from "@/components/skeleton/ui/profile-fields-skeleton";
import Skeleton from "@/components/skeleton/ui/skeleton";
import Card, { CardContent, CardHeader } from "@/components/ui/card";

export default function ServerDetailsSkeleton() {
  return (
    <section className="flex w-full min-w-0 flex-col gap-4">
      <Card className="w-full min-w-0 overflow-hidden p-0">
        <CardHeader>Details</CardHeader>
        <CardContent className="flex flex-col gap-5">
          {Array.from({ length: 2 }).map((_, group) => (
            <div key={group} className="flex flex-col gap-2">
              <Skeleton className="h-4 w-20 rounded-md" />
              <ProfileFieldsSkeleton
                fields={[
                  { labelWidth: "w-20", valueWidth: "w-full max-w-[200px]" },
                  { labelWidth: "w-16", valueWidth: "w-28" },
                  { labelWidth: "w-24", valueWidth: "w-32" },
                  { labelWidth: "w-14", valueWidth: "w-12" },
                ]}
              />
            </div>
          ))}
        </CardContent>
      </Card>
    </section>
  );
}
