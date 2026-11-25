import { ProfileFields } from "@/components/profile-field";
import Skeleton from "@/components/skeleton/ui/skeleton";

export interface ProfileFieldSkeletonRow {
  labelWidth: string;
  valueWidth: string;
}

export interface ProfileFieldsSkeletonProps {
  fields: ProfileFieldSkeletonRow[];
  className?: string;
}

export default function ProfileFieldsSkeleton({ fields, className }: ProfileFieldsSkeletonProps) {
  return (
    <ProfileFields className={className}>
      {fields.map((field, index) => (
        <div key={index} className="col-span-2 grid grid-cols-subgrid items-center py-2 first:pt-0 last:pb-0">
          <Skeleton className={`h-4 shrink-0 ${field.labelWidth}`} />
          <Skeleton className={`h-4 ${field.valueWidth}`} />
        </div>
      ))}
    </ProfileFields>
  );
}
