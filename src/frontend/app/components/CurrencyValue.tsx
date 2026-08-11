import { Tooltip } from "./Tooltip";

// Displays a (possibly abbreviated) currency string, wrapping it in the
// existing Tooltip to reveal the exact value on hover whenever it was
// abbreviated. exact is null below the abbreviation threshold, where no
// tooltip is shown at all.
export function CurrencyValue({ abbreviated, exact }: { abbreviated: string; exact: string | null }) {
  if (exact == null) return <>{abbreviated}</>;
  return (
    <Tooltip label={exact}>
      <span tabIndex={0}>{abbreviated}</span>
    </Tooltip>
  );
}
