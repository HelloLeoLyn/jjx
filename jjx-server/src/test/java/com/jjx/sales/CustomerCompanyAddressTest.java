package com.jjx.sales;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.common.exception.BusinessException;
import com.jjx.sales.domain.dto.SalesCustomerAddressDTO;
import com.jjx.sales.domain.entity.SalesCustomer;
import com.jjx.sales.domain.entity.SalesCustomerAddress;
import com.jjx.sales.mapper.CustomerMapper;
import com.jjx.sales.mapper.SalesCustomerAddressMapper;
import com.jjx.sales.service.impl.SalesCustomerAddressServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CustomerCompanyAddressTest {
    private final SalesCustomerAddressMapper addresses = mock(SalesCustomerAddressMapper.class);
    private final CustomerMapper customers = mock(CustomerMapper.class);
    private final SalesCustomerAddressServiceImpl service =
            new SalesCustomerAddressServiceImpl(addresses, customers, new ObjectMapper());
    private SalesCustomer customer;
    private final List<SalesCustomerAddress> stored = new ArrayList<>();

    @BeforeEach
    void setup() {
        customer = new SalesCustomer();
        customer.setCustomerId(7L);
        customer.setAddress("科技路1号");
        customer.setCountry("CN");
        customer.setContactPerson("张三");
        customer.setContactPhone("+86 1234");
        when(customers.selectOne(any())).thenReturn(customer);
        when(addresses.selectList(any())).thenAnswer(i -> List.copyOf(stored));
        when(addresses.selectCount(any())).thenAnswer(i -> (long) stored.size());
        when(addresses.insert(any(SalesCustomerAddress.class))).thenAnswer(i -> {
            SalesCustomerAddress a = i.getArgument(0);
            a.setAddressId(100L + stored.size());
            stored.add(a);
            return 1;
        });
    }

    @Test
    void createsOneCompanyAddressAndCopiesContactOnlyOnce() {
        service.ensureCompanyAddress(customer);
        service.ensureCompanyAddress(customer);
        assertEquals(1, stored.size());
        SalesCustomerAddress a = stored.getFirst();
        assertEquals("公司地址", a.getLabel());
        assertEquals(7L, a.getCustomerId());
        assertEquals("科技路1号", a.getAddress());
        assertEquals("张三", a.getContactPerson());
        assertEquals("+86 1234", a.getContactPhone());
        assertEquals(1, a.getIsDefault());
    }

    @Test
    void doesNotReplaceExistingDefault() {
        SalesCustomerAddress old = new SalesCustomerAddress();
        old.setAddress("仓库路2号"); old.setIsDefault(1);
        stored.add(old);
        service.ensureCompanyAddress(customer);
        assertEquals(2, stored.size());
        assertEquals(1, old.getIsDefault());
        assertEquals(0, stored.getLast().getIsDefault());
        verify(addresses, never()).update(any(), any());
    }

    @Test
    void existingSameAddressWithAnotherLabelIsNotDuplicated() {
        SalesCustomerAddress old = new SalesCustomerAddress();
        old.setAddress(" 科技路1号 "); old.setCountry("CN"); old.setLabel("总部");
        stored.add(old);
        service.ensureCompanyAddress(customer);
        assertEquals(1, stored.size());
        assertEquals("总部", old.getLabel());
    }

    @Test
    void parsesStructuredAddressAndSkipsEmptyStreet() {
        customer.setAddress("{\"country\":\"US\",\"province\":\"CA\",\"city\":\"LA\",\"street\":\"1 Main St\",\"zipCode\":\"90001\"}");
        service.ensureCompanyAddress(customer);
        assertEquals("1 Main St", stored.getFirst().getAddress());
        assertEquals("US", stored.getFirst().getCountry());
        assertEquals("90001", stored.getFirst().getPostalCode());
        customer.setAddress("{\"street\":\" \"}");
        service.ensureCompanyAddress(customer);
        assertEquals(1, stored.size());
    }

    @Test
    void blankAddressDoesNotCreateAnythingAndMalformedJsonFailsExplicitly() {
        customer.setAddress(" "); service.ensureCompanyAddress(customer);
        assertTrue(stored.isEmpty());
        customer.setAddress("{invalid");
        assertThrows(BusinessException.class, () -> service.ensureCompanyAddress(customer));
        assertTrue(stored.isEmpty());
    }

    @Test
    void rejectsNonexistentParentAndCrossCustomerUpdate() {
        when(customers.selectOne(any())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.add(99L, new SalesCustomerAddressDTO()));
        when(customers.selectOne(any())).thenReturn(customer);
        SalesCustomerAddress other = new SalesCustomerAddress(); other.setCustomerId(8L);
        when(addresses.selectById(10L)).thenReturn(other);
        SalesCustomerAddressDTO dto = new SalesCustomerAddressDTO(); dto.setAddressId(10L);
        assertThrows(BusinessException.class, () -> service.update(7L, dto));
        verify(addresses, never()).updateById(any(SalesCustomerAddress.class));
    }
}
