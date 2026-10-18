package com.jjx.sales;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.common.exception.BusinessException;
import com.jjx.sales.domain.dto.SalesCustomerAddressDTO;
import com.jjx.sales.domain.dto.SalesCustomerContactDTO;
import com.jjx.sales.domain.entity.SalesCustomer;
import com.jjx.sales.domain.entity.SalesCustomerAddress;
import com.jjx.sales.domain.entity.SalesCustomerContact;
import com.jjx.sales.mapper.CustomerMapper;
import com.jjx.sales.mapper.SalesCustomerAddressMapper;
import com.jjx.sales.mapper.SalesCustomerContactMapper;
import com.jjx.sales.service.impl.SalesCustomerAddressServiceImpl;
import com.jjx.sales.service.impl.SalesCustomerContactServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CustomerContactServiceTest {
    private final CustomerMapper customers = mock(CustomerMapper.class);
    private final SalesCustomerAddressMapper addresses = mock(SalesCustomerAddressMapper.class);
    private final SalesCustomerContactMapper contacts = mock(SalesCustomerContactMapper.class);
    private final SalesCustomerContactServiceImpl service = new SalesCustomerContactServiceImpl(contacts, customers, addresses);
    private final SalesCustomerAddressServiceImpl addressService =
            new SalesCustomerAddressServiceImpl(addresses, customers, new ObjectMapper(), service);
    private SalesCustomerContact contact;

    @BeforeEach
    void setup() {
        SalesCustomer customer = new SalesCustomer(); customer.setCustomerId(7L);
        when(customers.selectOne(any())).thenReturn(customer);
        contact = new SalesCustomerContact(); contact.setContactId(3L); contact.setCustomerId(7L);
        contact.setContactPerson("王经理"); contact.setContactPhone("+86 1234");
        when(contacts.selectById(3L)).thenReturn(contact);
    }

    private SalesCustomerAddressDTO dto() {
        SalesCustomerAddressDTO dto = new SalesCustomerAddressDTO();
        dto.setContactId(3L); dto.setAddress("科技路1号");
        dto.setContactPerson("客户端伪造姓名"); dto.setContactPhone("9999");
        return dto;
    }

    @Test
    void selectingContactUsesServerValues() {
        addressService.add(7L, dto());
        ArgumentCaptor<SalesCustomerAddress> saved = ArgumentCaptor.forClass(SalesCustomerAddress.class);
        verify(addresses).insert(saved.capture());
        assertEquals("王经理", saved.getValue().getContactPerson());
        assertEquals("+86 1234", saved.getValue().getContactPhone());
        assertEquals(3L, saved.getValue().getContactId());
    }

    @Test
    void crossCustomerContactCannotBeUsedByAddressOrEdited() {
        contact.setCustomerId(8L);
        assertThrows(BusinessException.class, () -> addressService.add(7L, dto()));
        assertThrows(BusinessException.class, () -> service.update(7L, 3L, new SalesCustomerContactDTO()));
        assertThrows(BusinessException.class, () -> service.delete(7L, 3L));
        verify(addresses, never()).insert(any(SalesCustomerAddress.class));
        verify(contacts, never()).updateById(any(SalesCustomerContact.class));
    }

    @Test
    void streetOnlyEditPreservesSnapshotAndRefreshIsExplicit() {
        SalesCustomerAddress address = new SalesCustomerAddress(); address.setAddressId(4L);
        address.setCustomerId(7L); address.setContactId(3L);
        address.setContactPerson("旧姓名"); address.setContactPhone("1111");
        when(addresses.selectById(4L)).thenReturn(address);
        SalesCustomerAddressDTO dto = dto(); dto.setAddressId(4L);
        addressService.update(7L, dto);
        assertEquals("旧姓名", address.getContactPerson());
        assertEquals("1111", address.getContactPhone());
        dto.setRefreshContact(true);
        addressService.update(7L, dto);
        assertEquals("王经理", address.getContactPerson());
        assertEquals("+86 1234", address.getContactPhone());
    }

    @Test
    void referencedContactCannotBeDeletedAndUnreferencedContactCan() {
        when(addresses.selectCount(any())).thenReturn(1L);
        assertThrows(BusinessException.class, () -> service.delete(7L, 3L));
        verify(contacts, never()).deleteById(3L);
        when(addresses.selectCount(any())).thenReturn(0L);
        service.delete(7L, 3L);
        verify(contacts).deleteById(3L);
    }

    @Test
    void contactEditDoesNotRewriteAddressesAndDuplicateIsRejected() {
        SalesCustomerContactDTO dto = new SalesCustomerContactDTO(); dto.setContactPerson("李经理");
        dto.setContactPhone("2222");
        service.update(7L, 3L, dto);
        verify(contacts).updateById(contact);
        verifyNoInteractions(addresses);
        when(contacts.selectCount(any())).thenReturn(1L);
        assertThrows(BusinessException.class, () -> service.add(7L, dto));
    }

    @Test
    void deletedContactCannotBeSelectedAndBlankPrimaryDoesNotCreateContact() {
        when(contacts.selectById(3L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> addressService.add(7L, dto()));
        SalesCustomer blank = new SalesCustomer(); blank.setCustomerId(7L);
        assertNull(service.ensurePrimaryContact(blank));
        verify(contacts, never()).insert(any(SalesCustomerContact.class));
    }
}
