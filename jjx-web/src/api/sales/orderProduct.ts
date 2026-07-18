import request from '@/utils/request'
import type { PageResult, R } from '@/types/index'
import type {
  SalesOrderProductAddDTO,
  SalesOrderProductEditDTO,
  SalesOrderProductQueryDTO,
  SalesOrderProductVO,
} from '@/types/sales/order'
/**
 * 订单产品明细API
 */
export const orderProductApi = {
  /**
   * 根据ID查询订单产品明细
   */
  getById(id: number) {
    return request.get<R<SalesOrderProductVO>>(`/sales/orders/product/${id}`)
  },

  /**
   * 分页查询订单产品明细列表
   */
  getPageList(params: SalesOrderProductQueryDTO) {
    return request.get<R<PageResult<SalesOrderProductVO>>>('/sales/orders/product/page', { params })
  },

  /**
   * 根据订单ID查询订单产品明细列表
   */
  getListByOrderId(orderId: number) {
    return request.get<R<SalesOrderProductVO[]>>(`/sales/orders/product/order/${orderId}`)
  },

  /**
   * 新增订单产品明细
   */
  add(data: SalesOrderProductAddDTO) {
    return request.post<R<void>>('/sales/orders/product', data)
  },

  /**
   * 批量新增订单产品明细
   */
  batchAdd(data: SalesOrderProductAddDTO[]) {
    return request.post<R<void>>('/sales/orders/product/batch', data)
  },

  /**
   * 修改订单产品明细
   */
  update(data: SalesOrderProductEditDTO) {
    return request.put<R<void>>('/sales/orders/product', data)
  },

  /**
   * 删除订单产品明细
   */
  delete(id: number) {
    return request.delete<R<void>>(`/sales/orders/product/${id}`)
  },

  /**
   * 批量删除订单产品明细
   */
  batchDelete(ids: number[]) {
    return request.delete<R<void>>('/sales/orders/product/batch', { data: ids })
  },

  /**
   * 根据订单ID删除订单产品明细
   */
  deleteByOrderId(orderId: number) {
    return request.delete<R<void>>(`/sales/orders/product/order/${orderId}`)
  },
}
