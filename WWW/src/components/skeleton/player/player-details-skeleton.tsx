import ProfileFieldsSkeleton from "@/components/skeleton/ui/profile-fields-skeleton";
import Card, { CardContent, CardHeader } from "@/components/ui/card";

export default function PlayerDetailsSkeleton() {
  return (
    <Card className="h-fit w-full overflow-hidden p-0">
      <CardHeader>Details</CardHeader>
      <CardContent className="p-3 pt-2">
        <ProfileFieldsSkeleton
          fields={[
            { labelWidth: "w-10", valueWidth: "w-full max-w-[280px]" },
            { labelWidth: "w-12", valueWidth: "w-28" },
          ]}
        />
      </CardContent>
    </Card>
  );
}
