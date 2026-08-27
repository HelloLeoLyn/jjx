package com.jjx.production.service;
import com.jjx.common.core.page.PageResult;
import com.jjx.production.domain.dto.EquipmentQueryDTO;
import com.jjx.production.domain.entity.ProductionEquipment;
import java.util.List;
public interface EquipmentService {
    PageResult<ProductionEquipment> page(EquipmentQueryDTO query);
    List<ProductionEquipment> list(EquipmentQueryDTO query);
    ProductionEquipment getById(Long id);
    Long create(ProductionEquipment entity);
    void update(ProductionEquipment entity);
    void delete(Long id);
}
