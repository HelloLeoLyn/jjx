import { computed, reactive, ref } from 'vue'
import { printingArtworkApi as api } from '@/api/product/film'
import type { ArtworkListRow, ArtworkView, FilmRequest, FilmRequestCommand } from '@/types/engineering/printingArtwork'
import { ArtworkVersionStatusEnum } from '@/enums/product/film'

export function filmError(error: unknown, fallback: string) {
  const response=(error as { response?: { status?: number; data?: { message?: string; msg?: string } } })?.response
  if(response?.data?.message || response?.data?.msg) return response.data.message || response.data.msg || fallback
  if(response?.status) return `${fallback}（HTTP ${response.status}）`
  if(error instanceof Error && error.message==='Network Error') return `${fallback}，请检查网络连接`
  return error instanceof Error && error.message ? error.message : fallback
}
export function newFilmRequestId() {
  if (typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  // 内网 HTTP 不提供 randomUUID；getRandomValues 在此环境仍可用。
  const bytes = crypto.getRandomValues(new Uint8Array(16))
  bytes[6] = (bytes[6] & 0x0f) | 0x40
  bytes[8] = (bytes[8] & 0x3f) | 0x80
  const hex = Array.from(bytes, byte => byte.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}

/** 所有正式数据来自API；选择/加载与表单交互在前端，归属/数量/并发规则由后端执行。 */
export function useFilmManagement() {
  const filters = reactive({ keyword: '', family: '' })
  const page = reactive({ pageNum: 1, pageSize: 20, total: 0 })
  const rows = ref<ArtworkListRow[]>([])
  const listLoading = ref(false)
  const listError = ref('')
  const detail = ref<ArtworkView>()
  const detailLoading = ref(false)
  const detailError = ref('')
  const requests = ref<FilmRequest[]>([])
  const requestsLoading = ref(false)
  const requestsError = ref('')
  const selectedArtworkId = ref<number>()
  const selectedVersionId = ref<number>()
  let listGeneration = 0, detailGeneration = 0, requestGeneration = 0
  const selectedVersion = computed(() => detail.value?.versions?.find(row => row.versionId === selectedVersionId.value))
  const releasedVersions = computed(() => detail.value?.versions?.filter(row => row.status === ArtworkVersionStatusEnum.RELEASED.value) || [])
  const versionCopies = computed(() => detail.value?.films?.filter(row => row.versionId === selectedVersionId.value) || [])
  const versionRequests = computed(() => requests.value.filter(row => row.versionId === selectedVersionId.value))

  async function loadRows(pageNum = page.pageNum) {
    const generation = ++listGeneration
    page.pageNum = pageNum; listLoading.value = true; listError.value = ''
    try {
      const result: any = await api.list({ keyword: filters.keyword.trim() || undefined, family: filters.family || undefined, pageNum: page.pageNum, pageSize: page.pageSize })
      if (generation !== listGeneration) return
      rows.value = result.data?.records || []; page.total = result.data?.total || 0
    } catch (error) { if (generation === listGeneration) { rows.value = []; page.total = 0; listError.value = filmError(error, '菲林列表加载失败，请重试') } }
    finally { if (generation === listGeneration) listLoading.value = false }
  }
  async function loadDetail(artworkId: number, keepSelection = false): Promise<boolean> {
    const generation = ++detailGeneration
    selectedArtworkId.value = artworkId; detailLoading.value = true; detailError.value = ''
    if (!keepSelection || detail.value?.artworkId !== artworkId) {
      detail.value = undefined; selectedVersionId.value = undefined; requests.value = []; requestsError.value = ''; ++requestGeneration; requestsLoading.value = false
    }
    try {
      const result: any = await api.detail(artworkId)
      if (generation !== detailGeneration || selectedArtworkId.value !== artworkId) return false
      const data: ArtworkView = result.data
      if (!data || data.artworkId !== artworkId) throw new Error('菲林详情身份不匹配，请重新加载')
      detail.value = data
      if (!keepSelection || !data.versions?.some(row => row.versionId === selectedVersionId.value)) selectedVersionId.value = data.defaultVersionId ?? data.versions?.[0]?.versionId
      return true
    } catch (error) { if (generation === detailGeneration) { detail.value = undefined; detailError.value = filmError(error, '菲林详情加载失败，请重试') }; return false }
    finally { if (generation === detailGeneration) detailLoading.value = false }
  }
  async function loadRequests(artworkId = selectedArtworkId.value) {
    if (!artworkId) return
    const generation = ++requestGeneration
    requestsLoading.value = true; requestsError.value = ''
    try {
      const result: any = await api.listRequests(artworkId)
      if (generation !== requestGeneration || selectedArtworkId.value !== artworkId) return
      requests.value = result.data || []
    } catch (error) { if (generation === requestGeneration) { requests.value = []; requestsError.value = filmError(error, '出片申请加载失败，请确认后端已部署并重试') } }
    finally { if (generation === requestGeneration) requestsLoading.value = false }
  }
  async function refreshDetails(artworkId = selectedArtworkId.value) {
    if (!artworkId || selectedArtworkId.value !== artworkId) return
    await Promise.all([loadDetail(artworkId, true), loadRequests(artworkId)])
  }
  async function applyRequest(versionId: number, command: FilmRequestCommand) {
    const artworkId = selectedArtworkId.value
    await api.applyFilmRequest(versionId, command)
    await Promise.all([loadRows(), refreshDetails(artworkId)])
  }
  async function receiveRequest(requestId: number, command: FilmRequestCommand) {
    const artworkId = selectedArtworkId.value
    await api.receiveFilmRequest(requestId, command)
    await Promise.all([loadRows(), refreshDetails(artworkId)])
  }
  return { filters, page, rows, listLoading, listError, detail, detailLoading, detailError, requests, requestsLoading, requestsError,
    selectedArtworkId, selectedVersionId, selectedVersion, releasedVersions, versionCopies, versionRequests,
    loadRows, loadDetail, loadRequests, refreshDetails, applyRequest, receiveRequest }
}
