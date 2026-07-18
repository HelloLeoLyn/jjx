package com.jjx.sales.domain.dto;

import lombok.Data;

import java.util.Set;

@Data
public class ODRSendToCustomerDTO {
    private long orderId;
    private String context;
    private Set<String> emails;
    private Set<String> inners;
    private Set<String> ding;
    private Set<String> sms;
    private Set<String> wechat;
}
