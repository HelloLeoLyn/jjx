package com.jjx.sales;

import com.jjx.common.exception.BusinessException;
import com.jjx.sales.domain.dto.CustomerAddDTO;
import com.jjx.sales.domain.dto.CustomerEditDTO;
import com.jjx.sales.domain.entity.SalesCustomer;
import com.jjx.sales.domain.vo.CustomerVO;
import com.jjx.sales.domain.dto.CustomerQueryDTO;
import com.jjx.sales.mapper.CustomerMapper;
import com.jjx.sales.service.impl.CustomerServiceImpl;
import com.jjx.sales.domain.converter.CustomerConverter;
import com.jjx.framework.common.RedisSequenceService;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 销售接单流程测试
 * 客户创建 → 修改 → 查询 → 删除
 * 
 * 模拟一个销售员第一次使用系统的完整操作
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SalesFlowTest {

    // ===== 模拟依赖 =====
    @Mock private CustomerMapper customerMapper;
    @Mock private RedisSequenceService redisSequenceService;
    @Mock private CustomerConverter customerConverter;

    private CustomerServiceImpl customerService;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        // 手动注入所有依赖
        customerService = new CustomerServiceImpl(
            customerMapper, customerConverter, redisSequenceService
        );
    }

    @Test
    @Order(1)
    @DisplayName("1. 新建客户 - 正常流程")
    void step1_createCustomer() {
        // 准备：销售员填写客户信息
        CustomerAddDTO dto = new CustomerAddDTO();
        dto.setCustomerName("华南科技有限公司");
        dto.setCustomerShortName("华南科技");
        dto.setCustomerType(1);
        dto.setContactPerson("张三");
        dto.setContactPhone("13800138000");

        // Mock：编码生成 + 数据库插入
        when(redisSequenceService.generateBizNumber(any())).thenReturn("CUS202607200001");
        when(customerConverter.toEntity(dto)).thenAnswer(inv -> {
            CustomerAddDTO arg = inv.getArgument(0);
            SalesCustomer entity = new SalesCustomer();
            entity.setCustomerName(arg.getCustomerName());
            return entity;
        });
        when(customerMapper.insert(any(SalesCustomer.class))).thenReturn(1);

        // 执行：保存客户
        int result = customerService.insertCustomer(dto);

        // 验证：创建成功
        assert result > 0 : "❌ 客户创建失败";
        System.out.println("  ✅ 客户「华南科技有限公司」创建成功，编号: CUS202607200001");
    }

    @Test
    @Order(2)
    @DisplayName("2. 查询客户列表")
    void step2_listCustomers() {
        // 准备：模拟数据库有一条客户记录
        SalesCustomer mockCustomer = new SalesCustomer();
        mockCustomer.setCustomerId(1L);
        mockCustomer.setCustomerName("华南科技有限公司");
        mockCustomer.setCustomerCode("CUS202607200001");
        CustomerVO mockVo = new CustomerVO();
        mockVo.setCustomerName("华南科技有限公司");

        when(customerMapper.selectList(any())).thenReturn(List.of(mockCustomer));
        when(customerConverter.toVOList(anyList())).thenReturn(List.of(mockVo));

        // 执行：查询客户列表
        CustomerQueryDTO query = new CustomerQueryDTO();
        List<CustomerVO> list = customerService.list(query);

        // 验证：查到记录
        assert list != null && !list.isEmpty() : "❌ 客户列表为空";
        assert "华南科技有限公司".equals(list.get(0).getCustomerName()) : "❌ 客户名称不匹配";
        System.out.println("  ✅ 客户列表查询成功，共 " + list.size() + " 条记录");
    }

    @Test
    @Order(3)
    @DisplayName("3. 修改客户信息")
    void step3_editCustomer() {
        // 准备：修改客户名称和联系人
        CustomerEditDTO dto = new CustomerEditDTO();
        dto.setCustomerId(1L);
        dto.setCustomerName("华南科技有限公司(华东分公司)");
        dto.setContactPerson("李四");

        // Mock：查询旧数据 + 更新
        SalesCustomer existing = new SalesCustomer();
        existing.setCustomerId(1L);
        existing.setCustomerCode("CUS202607200001");

        when(customerMapper.selectById(1L)).thenReturn(existing);
        when(customerMapper.updateById(any(SalesCustomer.class))).thenReturn(1);

        // Mock converter
        when(customerConverter.toEntity(dto)).thenAnswer(inv -> new SalesCustomer());

        // 执行：更新
        int result = customerService.updateCustomer(dto);

        // 验证：更新成功
        assert result > 0 : "❌ 客户修改失败";
        System.out.println("  ✅ 客户信息已更新: " + dto.getCustomerName());
    }

    @Test
    @Order(4)
    @DisplayName("4. 修改客户 - ID为空应报错")
    void step4_editCustomer_noId_shouldFail() {
        CustomerEditDTO dto = new CustomerEditDTO();
        // 不设置 ID

        try {
            customerService.updateCustomer(dto);
            assert false : "❌ 应该抛出异常但未抛出";
        } catch (BusinessException e) {
            System.out.println("  ✅ 正确拦截: " + e.getMessage());
        }
    }

    @Test
    @Order(5)
    @DisplayName("5. 修改客户 - 不存在应报错")
    void step5_editCustomer_notFound_shouldFail() {
        CustomerEditDTO dto = new CustomerEditDTO();
        dto.setCustomerId(999L);

        when(customerMapper.selectById(999L)).thenReturn(null);

        try {
            customerService.updateCustomer(dto);
            assert false : "❌ 应该抛出异常但未抛出";
        } catch (BusinessException e) {
            System.out.println("  ✅ 正确拦截: " + e.getMessage());
        }
    }
}
