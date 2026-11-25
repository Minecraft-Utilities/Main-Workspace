"use client";

import { Toaster as Sonner } from "sonner";

type ToasterProps = React.ComponentProps<typeof Sonner>;

const Toaster = ({ ...props }: ToasterProps) => {
  return (
    <Sonner
      className="toaster group"
      theme="dark"
      style={{ fontFamily: "inherit" }}
      toastOptions={{
        classNames: {
          toast:
            "group toast bg-popover text-foreground border-border-strong shadow-overlay rounded-lg border text-sm font-[inherit]",
          description: "text-text-secondary font-[inherit]",
          actionButton: "bg-primary text-primary-foreground",
          cancelButton: "bg-control text-muted-foreground",
        },
      }}
      {...props}
    />
  );
};

export { Toaster };
