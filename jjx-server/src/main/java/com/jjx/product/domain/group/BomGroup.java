package com.jjx.product.domain.group;

import com.jjx.common.annotation.ValidationGroups;
import jakarta.validation.groups.Default;

public interface BomGroup extends ValidationGroups {
    /**
     * 税额分组
     */
    interface Version extends Default {}
}
