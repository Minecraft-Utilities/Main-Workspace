import ProfileFieldsSkeleton from "@/components/skeleton/ui/profile-fields-skeleton";
import Card, { CardContent, CardHeader } from "@/components/ui/card";

export default function ServerDetailsSkeleton() {
  return (
    <section className="flex flex-col gap-4">
      <Card className="w-full min-w-0 overflow-hidden p-0">
        <CardHeader>Details</CardHeader>
        <CardContent className="p-3 pt-2">
          <ProfileFieldsSkeleton
            fields={[
              { labelWidth: "w-14", valueWidth: "w-20" },
              { labelWidth: "w-20", valueWidth: "w-full max-w-[200px]" },
              { labelWidth: "w-16", valueWidth: "w-28" },
              { labelWidth: "w-10", valueWidth: "w-12" },
              { labelWidth: "w-28", valueWidth: "w-36" },
              { labelWidth: "w-24", valueWidth: "w-10" },
              { labelWidth: "w-28", valueWidth: "w-10" },
              { labelWidth: "w-32", valueWidth: "w-10" },
              { labelWidth: "w-24", valueWidth: "w-10" },
              { labelWidth: "w-20", valueWidth: "w-10" },
              { labelWidth: "w-16", valueWidth: "w-10" },
            ]}
          />
        </CardContent>
      </Card>
    </section>
  );
}
