export function fmtQty(v?: number | string | null): string {
  if (v === null || v === undefined || v === '') return '0'
  return String(Number(v))
}
