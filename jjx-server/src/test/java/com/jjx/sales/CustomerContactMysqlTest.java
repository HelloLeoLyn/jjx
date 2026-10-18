package com.jjx.sales;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.common.exception.BusinessException;
import com.jjx.event.EventPublisher;
import com.jjx.framework.common.RedisSequenceService;
import com.jjx.sales.domain.converter.CustomerConverter;
import com.jjx.sales.domain.dto.CustomerAddDTO;
import com.jjx.sales.domain.dto.SalesCustomerAddressDTO;
import com.jjx.sales.domain.dto.SalesCustomerContactDTO;
import com.jjx.sales.domain.entity.SalesCustomer;
import com.jjx.sales.domain.entity.SalesCustomerAddress;
import com.jjx.sales.mapper.CustomerMapper;
import com.jjx.sales.mapper.SalesCustomerAddressMapper;
import com.jjx.sales.mapper.SalesCustomerContactMapper;
import com.jjx.sales.service.ICustomerService;
import com.jjx.sales.service.ISalesCustomerAddressService;
import com.jjx.sales.service.ISalesCustomerContactService;
import com.jjx.sales.service.impl.CustomerServiceImpl;
import com.jjx.sales.service.impl.SalesCustomerAddressServiceImpl;
import com.jjx.sales.service.impl.SalesCustomerContactServiceImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 显式开启后使用真实 MySQL + Mapper + 事务代理；每项测试回滚，不启动服务。 */
@EnabledIfEnvironmentVariable(named = "JJX_TEST_MYSQL", matches = "1")
class CustomerContactMysqlTest {
    private static DataSourceTransactionManager manager;
    private static JdbcTemplate jdbc;
    private static CustomerMapper customers;
    private static SalesCustomerAddressMapper addresses;
    private static ISalesCustomerContactService contacts;
    private static ISalesCustomerAddressService addressService;
    private TransactionStatus transaction;

    @BeforeAll
    static void setupDatabase() throws Exception {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
        ds.setUrl(System.getenv().getOrDefault("JJX_TEST_MYSQL_URL",
                "jdbc:mysql://127.0.0.1:3306/jjx_erp_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"));
        ds.setUsername(System.getenv().getOrDefault("DB_USER", "root"));
        ds.setPassword(System.getenv().getOrDefault("DB_PASS", "123456"));
        manager = new DataSourceTransactionManager(ds);
        jdbc = new JdbcTemplate(ds);
        MybatisConfiguration config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        config.addMapper(CustomerMapper.class);
        config.addMapper(SalesCustomerAddressMapper.class);
        config.addMapper(SalesCustomerContactMapper.class);
        MybatisSqlSessionFactoryBean bean = new MybatisSqlSessionFactoryBean();
        bean.setDataSource(ds); bean.setConfiguration(config);
        bean.setGlobalConfig(new GlobalConfig().setBanner(false));
        SqlSessionTemplate session = new SqlSessionTemplate(bean.getObject());
        customers = session.getMapper(CustomerMapper.class);
        addresses = session.getMapper(SalesCustomerAddressMapper.class);
        SalesCustomerContactMapper contactMapper = session.getMapper(SalesCustomerContactMapper.class);
        contacts = proxy(new SalesCustomerContactServiceImpl(contactMapper, customers, addresses), ISalesCustomerContactService.class);
        addressService = proxy(new SalesCustomerAddressServiceImpl(addresses, customers, new ObjectMapper(), contacts), ISalesCustomerAddressService.class);
    }

    private static <T> T proxy(Object target, Class<T> type) {
        ProxyFactory factory = new ProxyFactory(target);
        factory.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        return type.cast(factory.getProxy());
    }

    @BeforeEach void begin() { transaction = manager.getTransaction(new DefaultTransactionDefinition()); }
    @AfterEach void rollback() { if (transaction != null && !transaction.isCompleted()) manager.rollback(transaction); }

    private SalesCustomer customer() {
        SalesCustomer c = new SalesCustomer();
        c.setCustomerCode("CT" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
        c.setCustomerName("联系人事务测试" + c.getCustomerCode());
        c.setContactPerson("测试联系人"); c.setContactPhone("+86 1234");
        c.setAddress("测试街道1号"); c.setCountry("CN");
        customers.insert(c);
        return c;
    }

    private SalesCustomerContactDTO contactDto(String phone) {
        SalesCustomerContactDTO dto = new SalesCustomerContactDTO();
        dto.setContactPerson("测试联系人"); dto.setContactPhone(phone); return dto;
    }

    private SalesCustomerAddressDTO addressDto(Long contactId) {
        SalesCustomerAddressDTO dto = new SalesCustomerAddressDTO(); dto.setContactId(contactId);
        dto.setContactPerson("伪造姓名"); dto.setContactPhone("9999"); dto.setAddress("测试地址"); return dto;
    }

    @Test
    void realMappersPreserveSnapshotsAndSupportExplicitRefreshAndDetach() {
        SalesCustomer c = customer(); Long cid = c.getCustomerId();
        Long contactId = contacts.add(cid, contactDto("1234"));
        Long addressId = addressService.add(cid, addressDto(contactId));
        assertEquals("1234", addresses.selectById(addressId).getContactPhone());
        contacts.update(cid, contactId, contactDto("5678"));
        assertEquals("1234", addresses.selectById(addressId).getContactPhone());
        SalesCustomerAddressDTO edit = addressDto(contactId); edit.setAddressId(addressId);
        addressService.update(cid, edit);
        assertEquals("1234", addresses.selectById(addressId).getContactPhone());
        edit.setRefreshContact(true); addressService.update(cid, edit);
        assertEquals("5678", addresses.selectById(addressId).getContactPhone());
        assertThrows(BusinessException.class, () -> contacts.delete(cid, contactId));
        edit.setContactId(null); edit.setContactPerson("临时收货人"); edit.setContactPhone("8888");
        addressService.update(cid, edit);
        SalesCustomerAddress detached = addresses.selectById(addressId);
        assertNull(detached.getContactId()); assertEquals("8888", detached.getContactPhone());
        contacts.delete(cid, contactId);
        assertTrue(contacts.listByCustomer(cid).isEmpty());
        assertEquals(1, jdbc.queryForObject("SELECT deleted FROM sales_customer_contact WHERE contact_id=?", Integer.class, contactId));
        Long recreated = contacts.add(cid, contactDto("5678"));
        assertNotEquals(contactId, recreated);
        assertEquals(1, contacts.listByCustomer(cid).size());
    }

    @Test
    void companyCreationAndPrimaryContactSeedingAreIdempotent() {
        SalesCustomer c = customer();
        Long contactId = contacts.ensurePrimaryContact(c);
        assertEquals(contactId, contacts.ensurePrimaryContact(c));
        addressService.ensureCompanyAddress(c); addressService.ensureCompanyAddress(c);
        assertEquals(1, contacts.listByCustomer(c.getCustomerId()).size());
        assertEquals(1, addressService.listByCustomer(c.getCustomerId()).size());
        SalesCustomerAddress address = addressService.listByCustomer(c.getCustomerId()).getFirst();
        assertEquals(contactId, address.getContactId()); assertEquals(1, address.getIsDefault());
    }

    @Test
    void duplicateAndCrossCustomerContactWritesAreRejected() {
        SalesCustomer first = customer(); SalesCustomer second = customer();
        Long contactId = contacts.add(first.getCustomerId(), contactDto("1234"));
        assertThrows(BusinessException.class, () -> contacts.add(first.getCustomerId(), contactDto("1234")));
        assertThrows(BusinessException.class, () -> addressService.add(second.getCustomerId(), addressDto(contactId)));
        assertThrows(BusinessException.class, () -> contacts.update(second.getCustomerId(), contactId, contactDto("4567")));
        assertTrue(addressService.listByCustomer(second.getCustomerId()).isEmpty());
    }

    @Test
    void newCustomerCreatesLinkedCompanyAddressAndSeedsContactWithoutAddress() {
        SalesCustomer withAddress = new SalesCustomer(); withAddress.setCustomerName("新建客户事务测试");
        withAddress.setContactPerson("测试联系人"); withAddress.setContactPhone("1234");
        withAddress.setAddress("公司测试地址");
        assertEquals(1, customerService(withAddress).insertCustomer(new CustomerAddDTO()));
        SalesCustomerAddress company = addressService.listByCustomer(withAddress.getCustomerId()).getFirst();
        assertEquals("公司地址", company.getLabel());
        assertEquals(1, company.getIsDefault());
        assertEquals(contacts.listByCustomer(withAddress.getCustomerId()).getFirst().getContactId(), company.getContactId());
        SalesCustomer withoutAddress = new SalesCustomer(); withoutAddress.setCustomerName("无地址新客户事务测试");
        withoutAddress.setContactPerson("另一联系人"); withoutAddress.setContactPhone("5678");
        assertEquals(1, customerService(withoutAddress).insertCustomer(new CustomerAddDTO()));
        assertEquals(1, contacts.listByCustomer(withoutAddress.getCustomerId()).size());
        assertTrue(addressService.listByCustomer(withoutAddress.getCustomerId()).isEmpty());
    }

    private ICustomerService customerService(SalesCustomer customer) {
        CustomerConverter converter = mock(CustomerConverter.class);
        RedisSequenceService sequence = mock(RedisSequenceService.class);
        when(converter.toEntity(any(CustomerAddDTO.class))).thenReturn(customer);
        when(sequence.generateBusinessNumberByType(anyString(), anyString(), anyString(), anyInt(), anyLong()))
                .thenReturn("NC" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
        return proxy(new CustomerServiceImpl(customers, converter, sequence,
                addressService, contacts, mock(EventPublisher.class)), ICustomerService.class);
    }

    @Test
    void actualMysqlCustomerInsertRollsBackWhenCompanyAddressFails() {
        CustomerConverter converter = mock(CustomerConverter.class);
        RedisSequenceService sequence = mock(RedisSequenceService.class);
        String code = "RB" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        SalesCustomer c = new SalesCustomer(); c.setCustomerName("失败回滚测试");
        c.setContactPerson("测试联系人"); c.setContactPhone("1234"); c.setAddress("测试地址");
        when(converter.toEntity(any(CustomerAddDTO.class))).thenReturn(c);
        when(sequence.generateBusinessNumberByType(anyString(), anyString(), anyString(), anyInt(), anyLong())).thenReturn(code);
        ISalesCustomerAddressService failingAddress = mock(ISalesCustomerAddressService.class);
        doThrow(new IllegalStateException("模拟地址失败")).when(failingAddress).ensureCompanyAddress(any());
        ICustomerService customerService = proxy(new CustomerServiceImpl(customers, converter, sequence,
                failingAddress, contacts, mock(EventPublisher.class)), ICustomerService.class);
        manager.rollback(transaction); transaction = null;
        assertThrows(IllegalStateException.class, () -> customerService.insertCustomer(new CustomerAddDTO()));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sales_customer WHERE customer_code=?", Integer.class, code));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sales_customer_contact WHERE customer_id=?", Integer.class, c.getCustomerId()));
    }
}
