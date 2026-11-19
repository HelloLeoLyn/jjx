import request from '@/utils/request'
import type { R } from '@/types'
export interface MigrationBatch { inboundId: number; batchCode: string; businessDate: string; importedAt: string; rows: number; missingCosts: number }
export interface MigrationRow { lineId: number; sourceRow: number; materialCode: string; materialName: string; specification: string; unit: string; initialQuantity: number; receivedQuantity: number; issuedQuantity: number; remainingQuantity: number; unitCost: number | null; costVersion: number | null; location: string; issues: string }
export interface MigrationCheck { rows: number; materials: number; zeroRows: number; errors: string[]; alreadyImported: boolean }
export interface MigrationCost { lineId: number; expectedVersion: number | null; unitCost: number; reason: string }
export interface MigrationCostAudit { recordId: number; time: string; operator: string; beforeCost: number | null; afterCost: number; amountDelta: number; reason: string; referenceNo: string }
export const migrationApi = {
  batches: () => request.get<R<MigrationBatch[]>>('/inventory/migration/batches'),
  rows: (batchId: number) => request.get<R<MigrationRow[]>>(`/inventory/migration/${batchId}/rows`),
  history: (batchId: number, lineId: number) => request.get<R<MigrationCostAudit[]>>(`/inventory/migration/${batchId}/rows/${lineId}/cost-history`),
  preview: (data: unknown) => request.post<R<MigrationCheck>>('/inventory/migration/preview', data),
  import: (data: unknown) => request.post<R<{ inboundId: number; rows: number; alreadyImported: boolean }>>('/inventory/migration/import', data),
  costs: (batchId: number, data: MigrationCost[]) => request.post<R<{ changedRows: number; adjustmentRecords: number; amountDelta: number }>>(`/inventory/migration/${batchId}/costs`, data),
}
