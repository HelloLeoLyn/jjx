package com.jjx.sales;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.event.EventPublisher;
import com.jjx.framework.common.RedisSequenceService;
import com.jjx.sales.domain.converter.CustomerConverter;
import com.jjx.sales.domain.dto.CustomerAddDTO;
import com.jjx.sales.domain.entity.SalesCustomer;
import com.jjx.sales.domain.entity.SalesCustomerAddress;
import com.jjx.sales.mapper.CustomerMapper;
import com.jjx.sales.mapper.SalesCustomerAddressMapper;
import com.jjx.sales.service.ICustomerService;
import com.jjx.sales.service.ISalesCustomerAddressService;
import com.jjx.sales.service.impl.CustomerServiceImpl;
import com.jjx.sales.service.impl.SalesCustomerAddressServiceImpl;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 真实 Spring 事务 + H2；Mapper 为 JDBC 替身，不代表真实 MySQL 业务库验收。 */
class CustomerAddressTransactionTest {
    private JdbcTemplate jdbc;
    private ICustomerService service;
    private boolean failAfterAddressInsert;

    @BeforeEach
    void setup() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE customers (id BIGINT PRIMARY KEY)");
        jdbc.execute("CREATE TABLE addresses (customer_id BIGINT, address VARCHAR(255))");
        DataSourceTransactionManager manager = new DataSourceTransactionManager(dataSource);
        CustomerMapper customers = mock(CustomerMapper.class);
        SalesCustomerAddressMapper addresses = mock(SalesCustomerAddressMapper.class);
        CustomerConverter converter = mock(CustomerConverter.class);
        RedisSequenceService sequence = mock(RedisSequenceService.class);
        SalesCustomer customer = new SalesCustomer();
        customer.setAddress("科技路1号");
        when(converter.toEntity(any(CustomerAddDTO.class))).thenReturn(customer);
        when(sequence.generateBusinessNumberByType(anyString(), anyString(), anyString(), anyInt(), anyLong()))
                .thenReturn("CUS00001");
        when(customers.insert(any(SalesCustomer.class))).thenAnswer(i -> {
            SalesCustomer c = i.getArgument(0); c.setCustomerId(7L);
            return jdbc.update("INSERT INTO customers VALUES (?)", c.getCustomerId());
        });
        when(customers.selectOne(any())).thenReturn(customer);
        when(addresses.selectList(any())).thenReturn(List.of());
        when(addresses.selectCount(any())).thenReturn(0L);
        when(addresses.insert(any(SalesCustomerAddress.class))).thenAnswer(i -> {
            SalesCustomerAddress a = i.getArgument(0); a.setAddressId(1L);
            int rows = jdbc.update("INSERT INTO addresses VALUES (?, ?)", a.getCustomerId(), a.getAddress());
            if (failAfterAddressInsert) throw new IllegalStateException("模拟地址插入后失败");
            return rows;
        });
        ISalesCustomerAddressService addressService = proxy(new SalesCustomerAddressServiceImpl(
                addresses, customers, new ObjectMapper()), manager, ISalesCustomerAddressService.class);
        service = proxy(new CustomerServiceImpl(customers, converter, sequence, addressService,
                mock(EventPublisher.class)), manager, ICustomerService.class);
    }

    private <T> T proxy(Object target, DataSourceTransactionManager manager, Class<T> type) {
        ProxyFactory factory = new ProxyFactory(target);
        factory.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        return type.cast(factory.getProxy());
    }

    @Test
    void successfulSaveCommitsCustomerAndCompanyAddressTogether() {
        assertEquals(1, service.insertCustomer(new CustomerAddDTO()));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM customers", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM addresses", Integer.class));
    }

    @Test
    void addressFailureRollsBackBothInsertedRows() {
        failAfterAddressInsert = true;
        assertThrows(IllegalStateException.class, () -> service.insertCustomer(new CustomerAddDTO()));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM customers", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM addresses", Integer.class));
    }
}
