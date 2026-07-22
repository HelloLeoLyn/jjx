import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { debounce } from 'lodash-es'
import { orderApi } from '@/api/sales/order'
import { customerApi } from '@/api/sales/customer'
import { roleApi } from '@/api/system/role'
import type { CustomerSearchVO } from '@/types/sales/customer'
import type { SysUserVO, RoleUserQueryDTO } from '@/types/system'
import type { OrderFormData, OrderItem, UseOrderFormOptions } from '@/types/sales/order'
import { formatCurrency } from '@/utils/format'
import { productApi } from '@/api/product'

export function useOrderForm(options: UseOrderFormOptions = {}) {
  const { isEdit = false, initialData = {} } = options

  // 表单引用
  const orderFormRef = ref<FormInstance>()

  // 加载状态
  const customerLoading = ref(false)
  const productLoading = ref(false)
  const submitting = ref(false)
  const loading = ref(false)

  // 选项数据
  const customerOptions = ref<Array<CustomerSearchVO>>([])
  const productOptions = ref<
    Array<{ productCode: string; productName: string; productId: number }>
  >([])

  // 产品搜索缓存
  const productSearchCache = ref<
    Map<
      string,
      {
        data: Array<{ productCode: string; productName: string; productId: number }>
        timestamp: number
      }
    >
  >(new Map())
  // 缓存有效期（5分钟）
  const CACHE_EXPIRY_TIME = 5 * 60 * 1000

  // 订单类型选项
  const orderTypeOptions = ref([
    { value: 1, label: '标准订单' },
    { value: 2, label: '样品订单' },
  ])

  // 销售负责人选项
  const salesPersonOptions = ref<Array<{ userId: number; nickName: string; userName: string }>>([])

  // 加载销售负责人列表（角色ID=4）
  const loadSalesPersons = async () => {
    try {
      // TODO: 后端需要提供一个接口，直接返回销售负责人的列表，目前只能通过角色查询用户来实现
      const res = await roleApi.allocatedList({ roleId: 7, pageNum: 1, pageSize: 999 })
      if (res.code === 200 && res.data?.records) {
        salesPersonOptions.value = res.data.records.map((user: SysUserVO) => ({
          userId: user.userId,
          nickName: user.nickName || '',
          userName: user.userName,
        }))
      }
    } catch (error) {
      console.error('加载销售负责人失败:', error)
    }
  }

  // 销售负责人选择变化
  const salesPersonChanged = (userId: number) => {
    const selected = salesPersonOptions.value.find((item) => item.userId === userId)
    if (selected) {
      form.salesPersonName = selected.nickName
    }
  }

  // 字典选项
  const currencyOptions = ref([
    { value: 'CNY', label: '人民币' },
    { value: 'USD', label: '美元' },
    { value: 'EUR', label: '欧元' },
    { value: 'JPY', label: '日元' },
    { value: 'HKD', label: '港币' },
  ])

  const paymentTermsOptions = ref([
    { value: 'prepaid', label: '预付' },
    { value: 'cod', label: '货到付款' },
    { value: 'net30', label: '月结30天' },
    { value: 'net60', label: '月结60天' },
  ])

  const shippingMethodOptions = ref([
    { value: 'express', label: '快递' },
    { value: 'logistics', label: '物流' },
    { value: 'self_pickup', label: '自提' },
  ])

  // 表单数据
  const form = reactive<OrderFormData>({
    orderId: undefined,
    orderNo: '',
    customerId: undefined,
    customerName: '',
    contactPerson: '',
    contactPhone: '',
    email: '',
    creditLimit: 0,
    orderDate: '',
    deliveryDate: '',
    orderType: undefined,
    currency: 'CNY',
    exchangeRate: 1.0,
    paymentTerms: 'prepaid',
    shippingMethod: 'express',
    shippingAddress: '',
    subtotalAmount: 0,
    taxRate: 0,
    taxAmount: 0,
    shippingFee: 0,
    discountAmount: 0,
    totalAmount: 0,
    totalQuantity: 0, // 总数量
    orderStatus: 1,
    prodStatus: 1,
    paymentStatus: 1,
    salesPersonId: undefined,
    salesPersonName: '',
    remark: '',
    items: [],
    ...initialData,
  })

  // 表单验证规则
  const rules = reactive<FormRules>({
    customerId: [{ required: true, message: '请选择客户', trigger: 'change' }],
    orderDate: [{ required: true, message: '请选择订单日期', trigger: 'change' }],
    deliveryDate: [{ required: true, message: '请选择交货日期', trigger: 'change' }],
    orderType: [{ required: true, message: '请选择订单类型', trigger: 'change' }],
    salesPersonId: [{ required: true, message: '请选择销售负责人', trigger: 'change' }],
    currency: [{ required: true, message: '请选择币种', trigger: 'change' }],
    paymentTerms: [{ required: true, message: '请选择付款条件', trigger: 'change' }],
    shippingMethod: [{ required: true, message: '请选择运输方式', trigger: 'change' }],
    shippingAddress: [{ required: true, message: '请输入收货地址', trigger: 'blur' }],
  })

  // 搜索客户
  const searchCustomer = (query: string) => {
    if (!query || query.trim().length < 2) {
      customerOptions.value = []
      return
    }

    customerLoading.value = true
    customerApi
      .searchCustomers(query)
      .then((response) => {
        if (response.code === 200 && response.data) {
          customerOptions.value = response.data
        } else {
          customerOptions.value = []
        }
      })
      .catch((error) => {
        console.error('搜索客户失败:', error)
        customerOptions.value = []
      })
      .finally(() => {
        customerLoading.value = false
      })
  }

  // 客户选择变化时，自动填充联系人、联系电话、收货地址
  const customerChanged = (customerId: number) => {
    const selectedCustomer = customerOptions.value.find(
      (customer) => customer.customerId === customerId
    )
    if (selectedCustomer) {
      form.customerName = selectedCustomer.customerName
      form.contactPerson = selectedCustomer.contactPerson || ''
      form.contactPhone = selectedCustomer.contactPhone || ''
      form.shippingAddress = selectedCustomer.address || ''
    }
  }

  /**
   * 从缓存获取产品搜索结果
   */
  const getProductFromCache = (query: string) => {
    const normalizedQuery = query.trim().toLowerCase()
    const cacheItem = productSearchCache.value.get(normalizedQuery)

    if (cacheItem) {
      const now = Date.now()
      // 检查缓存是否过期
      if (now - cacheItem.timestamp < CACHE_EXPIRY_TIME) {
        return cacheItem.data
      } else {
        // 缓存过期，删除
        productSearchCache.value.delete(normalizedQuery)
      }
    }
    return null
  }

  /**
   * 保存产品搜索结果到缓存
   */
  const saveProductToCache = (
    query: string,
    data: Array<{ productCode: string; productName: string; productId: number }>
  ) => {
    const normalizedQuery = query.trim().toLowerCase()
    productSearchCache.value.set(normalizedQuery, {
      data,
      timestamp: Date.now(),
    })
  }

  /**
   * 清理过期缓存
   */
  const cleanupExpiredCache = () => {
    const now = Date.now()
    // 使用 Array.from 来避免迭代器问题
    const entries = Array.from(productSearchCache.value.entries())
    for (const [key, cacheItem] of entries) {
      if (now - cacheItem.timestamp >= CACHE_EXPIRY_TIME) {
        productSearchCache.value.delete(key)
      }
    }
  }

  /**
   * 实际执行产品搜索的函数
   */
  const performProductSearch = async (query: string) => {
    if (!query || query.trim().length === 0) {
      productOptions.value = []
      productLoading.value = false
      return
    }

    const normalizedQuery = query.trim()

    // 1. 检查缓存
    const cachedData = getProductFromCache(normalizedQuery)
    if (cachedData) {
      productOptions.value = cachedData
      productLoading.value = false
      return
    }

    // 2. 执行API搜索
    try {
      productLoading.value = true
      const res = await productApi.search(normalizedQuery)

      if (res.code === 200 && res.data) {
        const productData = res.data.map((item: any) => ({
          productCode: item.productCode,
          productName: item.productName,
          productId: item.productId,
        }))

        // 保存到缓存
        saveProductToCache(normalizedQuery, productData)
        productOptions.value = productData
      } else {
        productOptions.value = []
      }
    } catch (error) {
      console.error('搜索产品失败:', error)
      productOptions.value = []
      ElMessage.error('搜索产品失败，请稍后重试')
    } finally {
      productLoading.value = false
    }
  }

  // 防抖搜索函数（300ms）
  const debouncedSearchProduct = debounce(performProductSearch, 300)

  // 搜索产品（带防抖和缓存）
  const searchProduct = (query: string, row: any) => {
    if (!query || query.trim().length < 2) {
      productOptions.value = []
      return
    }

    // 触发防抖搜索
    debouncedSearchProduct(query)
  }

  // 组件卸载时清理防抖函数
  onUnmounted(() => {
    if (debouncedSearchProduct && debouncedSearchProduct.cancel) {
      debouncedSearchProduct.cancel()
    }
  })

  // 处理产品选择变化
  const handleProductChange = (item: OrderItem) => {
    console.log(item)

    // 根据选择的产品编码自动填充产品名称
    const selectedProduct = productOptions.value.find(
      (product) => product.productCode === item.productCode
    )
    console.log(selectedProduct)

    if (selectedProduct) {
      item.productName = selectedProduct.productName
      item.unit = 'PCS' // 默认单位
      item.specification = '标准规格' // 默认规格
      item.productId = selectedProduct.productId
    } else if (item.productCode) {
      // 没有匹配的产品（样品单或自定义编码）
      // 保持用户输入的产品名和编码不变
      if (!item.productName) {
        item.productName = item.productCode
      }
      item.unit = item.unit || 'PCS'
    } else {
      item.productName = ''
      item.unit = ''
      item.specification = ''
    }
    calculateItemAmount(item)
  }

  // 计算明细金额
  const calculateItemAmount = (item: OrderItem) => {
    item.amount = (item.quantity || 0) * (item.unitPrice || 0)
    calculateTotalAmount()
  }

  // 计算总金额和总数量
  const calculateTotalAmount = () => {
    // 计算小计金额
    form.subtotalAmount = form.items.reduce((sum, item) => sum + (item.amount || 0), 0)

    // 计算总数量
    form.totalQuantity = form.items.reduce((sum, item) => sum + (item.quantity || 0), 0)

    // 计算税额
    form.taxAmount = (form.subtotalAmount * (form.taxRate || 0)) / 100

    // 计算总金额
    form.totalAmount =
      form.subtotalAmount + form.taxAmount + (form.shippingFee || 0) - (form.discountAmount || 0)
  }

  // 添加明细
  const addItem = (orderId?: number) => {
    form.items.push({
      productCode: '',
      productName: '',
      specification: '',
      customerMaterialNo: '',
      lineRemark: '',
      unit: 'PCS',
      quantity: 1,
      unitPrice: 0,
      amount: 0,
      deliveryDays: 30,
      customRequirements: '',
      orderId: orderId,
    })
  }

  // 删除明细
  const removeItem = (index: number) => {
    form.items.splice(index, 1)
    calculateTotalAmount()
  }

  // 重置表单
  const resetForm = () => {
    if (orderFormRef.value) {
      orderFormRef.value.resetFields()
    }
    Object.assign(form, {
      orderId: undefined,
      orderNo: '',
      customerId: undefined,
      customerName: '',
      contactPerson: '',
      contactPhone: '',
      email: '',
      creditLimit: 0,
      orderDate: '',
      deliveryDate: '',
      orderType: undefined,
      currency: 'CNY',
      exchangeRate: 1.0,
      paymentTerms: 'prepaid',
      shippingMethod: 'express',
      shippingAddress: '',
      subtotalAmount: 0,
      taxRate: 0,
      taxAmount: 0,
      shippingFee: 0,
      discountAmount: 0,
      totalAmount: 0,
      totalQuantity: 0, // 总数量
      orderStatus: 1,
      prodStatus: 1,
      paymentStatus: 1,
      salesPersonId: undefined,
      salesPersonName: '',
      remark: '',
      items: [],
    })
  }

  // 生成订单号（新增时使用）
  const generateOrderNo = async () => {
    try {
      const response = await orderApi.generateOrderNo()
      if (response.data) {
        form.orderNo = response.data
      }
    } catch (error) {
      console.error('生成订单号失败:', error)
    }
  }

  // 加载订单数据（编辑时使用）
  const loadOrderData = async (orderId: number) => {
    loading.value = true
    try {
      // 1. 加载订单基本信息
      const orderResponse = await orderApi.getOrder(orderId)
      if (orderResponse.code === 200 && orderResponse.data) {
        Object.assign(form, orderResponse.data)
        // 2. 将后端字段映射为表单字段
        form.salesPersonId = orderResponse.data.salesManagerId
        form.salesPersonName = orderResponse.data.salesManagerName
        // 3. 重新计算金额
        calculateTotalAmount()
      } else {
        ElMessage.error('加载订单数据失败')
        return false
      }
    } catch (error) {
      console.error('加载订单数据失败:', error)
      ElMessage.error('加载订单数据失败')
      return false
    } finally {
      loading.value = false
    }
    return true
  }

  // 提交表单验证
  const validateForm = (): boolean => {
    if (!orderFormRef.value) return false

    let isValid = true
    orderFormRef.value.validate((valid) => {
      isValid = valid
    })

    if (!isValid) return false

    // 验证明细
    if (form.items.length === 0) {
      ElMessage.warning('请至少添加一条订单明细')
      return false
    }

    // 验证明细数据
    for (const item of form.items) {
      if (!item.productCode || !item.productName) {
        ElMessage.warning('请填写完整的产品信息')
        return false
      }
      if (item.quantity <= 0) {
        ElMessage.warning('数量必须大于0')
        return false
      }
      if (item.unitPrice < 0) {
        ElMessage.warning('单价不能为负数')
        return false
      }
    }

    // 验证总数量（与后端验证保持一致）
    if (form.totalQuantity <= 0) {
      ElMessage.warning('总数量至少为1')
      return false
    }

    return true
  }

  // 提交新增订单
  const submitAdd = async (): Promise<boolean> => {
    if (!validateForm()) return false

    submitting.value = true
    try {
      // 1. 构建提交数据，将表单字段映射为后端DTO字段
      const submitData = {
        ...form,
        salesManagerId: form.salesPersonId,
        salesManagerName: form.salesPersonName,
      }
      // 1. 保存订单基本信息
      const orderResponse = await orderApi.addOrder(submitData as any)
      if (orderResponse.code !== 200) {
        ElMessage.error('新增订单失败')
        return false
      }
      ElMessage.success('新增成功')
      return true
    } catch (error) {
      console.error('新增订单失败:', error)
      ElMessage.error('新增订单失败')
      return false
    } finally {
      submitting.value = false
    }
  }

  // 提交编辑订单
  const submitEdit = async (): Promise<boolean> => {
    if (!validateForm()) return false

    submitting.value = true
    try {
      // 1. 构建提交数据，将表单字段映射为后端DTO字段
      const submitData = {
        ...form,
        salesManagerId: form.salesPersonId,
        salesManagerName: form.salesPersonName,
      }
      // 1. 更新订单基本信息
      const orderResponse = await orderApi.updateOrder(submitData as any)
      if (orderResponse.code !== 200) {
        ElMessage.error('修改订单失败')
        return false
      }
      ElMessage.success('修改成功')
      return true
    } catch (error) {
      console.error('修改订单失败:', error)
      ElMessage.error('修改订单失败')
      return false
    } finally {
      submitting.value = false
    }
  }

  // 根据模式提交
  const submitForm = async (): Promise<boolean> => {
    if (isEdit) {
      return await submitEdit()
    } else {
      return await submitAdd()
    }
  }

  return {
    // 引用
    orderFormRef,

    // 状态
    customerLoading,
    productLoading,
    submitting,
    loading,

    // 数据
    customerOptions,
    productOptions,
    currencyOptions,
    paymentTermsOptions,
    shippingMethodOptions,
    orderTypeOptions,
    salesPersonOptions,
    form,
    rules,

    // 方法
    searchCustomer,
    customerChanged,
    loadSalesPersons,
    salesPersonChanged,
    searchProduct,
    handleProductChange,
    calculateItemAmount,
    calculateTotalAmount,
    addItem,
    removeItem,
    resetForm,
    generateOrderNo,
    loadOrderData,
    validateForm,
    submitAdd,
    submitEdit,
    submitForm,

    // 工具函数
    formatCurrency,
  }
}
