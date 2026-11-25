import { Fragment } from "react";

/**
 * Lets hostnames and IP addresses wrap after their separators instead of mid-label when they
 * outgrow a narrow screen.
 */
export function BreakableText({ text }: { text: string }) {
  const parts = text.split(/(?<=[.:/])/);
  return (
    <>
      {parts.map((part, index) => (
        <Fragment key={index}>
          {index > 0 ? <wbr /> : null}
          {part}
        </Fragment>
      ))}
    </>
  );
}
