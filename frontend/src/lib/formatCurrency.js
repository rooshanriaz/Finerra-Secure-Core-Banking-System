/** Pakistan Rupee display for Finnera (core amounts originate from Fineract; UI uses PKR consistently). */
export function formatPkr(amount) {
  return `PKR ${Number(amount || 0).toLocaleString('en-PK', { minimumFractionDigits: 0, maximumFractionDigits: 2 })}`;
}
