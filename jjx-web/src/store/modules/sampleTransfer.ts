import { defineStore } from 'pinia'
import { useSampleTransferStore as createConfirmedTransfer } from '@/views/engineering/sample-workbench/composables/useConfirmedSampleTransfer'

/** 销售列表与工程工作台复用同一归档方案转移协议。 */
export const useSampleTransferStore = defineStore('sampleTransfer', createConfirmedTransfer)