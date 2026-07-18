package com.jjx.purchase.domain.dto;

import com.jjx.common.annotation.ValidationGroups;
import jakarta.validation.groups.Default;

public interface OrderGroup extends ValidationGroups {
    /**
     * 税额分组
     */
    interface Tax extends Default {}
}
