import { describe, expect, it, vi } from 'vitest'
const http=vi.hoisted(()=>({get:vi.fn(),post:vi.fn(),put:vi.fn(),delete:vi.fn()}))
vi.mock('@/utils/request',()=>({default:http}))
import { printingArtworkApi } from '@/api/product/film'
describe('出片申请HTTP契约',()=>{
 it('按菲林读取申请，并向版本申请与申请回片两个不同入口提交',()=>{
  const application={requestId:'apply-1',expectedRevision:4,quantity:2,material:'PET',thicknessMm:0.125,size:'A4',dueDate:'2026-12-01'}
  const receipt={requestId:'receipt-1',expectedRevision:1,copies:[{filmNo:'FL-1',location:'A架'}]}
  printingArtworkApi.listRequests(1);printingArtworkApi.applyFilmRequest(12,application);printingArtworkApi.receiveFilmRequest(7,receipt)
  expect(http.get).toHaveBeenCalledWith('/engineering/printing/artworks/1/film-requests')
  expect(http.post).toHaveBeenCalledWith('/engineering/printing/versions/12/film-requests',application)
  expect(http.post).toHaveBeenCalledWith('/engineering/printing/film-requests/7/receipts',receipt)
  expect(printingArtworkApi).not.toHaveProperty('requestFilms');expect(printingArtworkApi).not.toHaveProperty('sendFilm');expect(printingArtworkApi).not.toHaveProperty('receiveFilm')
 })
})
