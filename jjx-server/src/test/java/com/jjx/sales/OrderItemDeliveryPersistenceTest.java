package com.jjx.sales;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.jjx.sales.domain.converter.SalesOrderProductConverter;
import com.jjx.sales.domain.dto.SalesOrderProductDTO;
import com.jjx.sales.mapper.SalesOrderProductMapper;
import org.apache.ibatis.mapping.Environment;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

/** 真实MapStruct与MyBatis/H2字段读写，不连接业务库。 */
class OrderItemDeliveryPersistenceTest {
    @Test void quotationDeliveryFieldsReachPhysicalColumnsAndReturnInDetails() {
        var ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:order-delivery;MODE=MySQL;DB_CLOSE_DELAY=-1");
        var jdbc=new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE sales_order_product(id BIGINT AUTO_INCREMENT PRIMARY KEY,quantity INT,amount DECIMAL(18,2),"
                +"order_id BIGINT,product_id BIGINT,work_spec_version_id BIGINT,unit VARCHAR,unit_price DECIMAL(18,2),"
                +"product_code VARCHAR,product_name VARCHAR,remark VARCHAR,specification VARCHAR,customer_material_no VARCHAR,"
                +"line_remark VARCHAR,delivery_days INT,estimated_delivery_date DATE)");
        var config=new MybatisConfiguration();config.setMapUnderscoreToCamelCase(true);
        config.setEnvironment(new Environment("delivery",new SpringManagedTransactionFactory(),ds));
        config.addMapper(SalesOrderProductMapper.class);
        var session=new SqlSessionTemplate(new MybatisSqlSessionFactoryBuilder().build(config));
        var mapper=session.getMapper(SalesOrderProductMapper.class);
        var converter=Mappers.getMapper(SalesOrderProductConverter.class);
        var dto=new SalesOrderProductDTO();dto.setOrderId(1L);dto.setProductId(9L);
        dto.setDeliveryDays(12);dto.setEstimatedDeliveryDate(LocalDate.of(2026,12,2));
        var entity=converter.toEntity(dto);assertEquals(1,mapper.insert(entity));
        var stored=converter.toVO(mapper.selectById(entity.getId()));
        assertEquals(12,stored.getDeliveryDays());assertEquals(LocalDate.of(2026,12,2),stored.getEstimatedDeliveryDate());
        assertEquals(12,jdbc.queryForObject("SELECT delivery_days FROM sales_order_product WHERE id=?",Integer.class,entity.getId()));
        assertEquals(LocalDate.of(2026,12,2),jdbc.queryForObject("SELECT estimated_delivery_date FROM sales_order_product WHERE id=?",LocalDate.class,entity.getId()));
        dto.setDeliveryDays(20);dto.setEstimatedDeliveryDate(LocalDate.of(2026,12,10));
        converter.updateEntity(entity,dto);assertEquals(1,mapper.updateById(entity));
        stored=converter.toVO(mapper.selectById(entity.getId()));
        assertEquals(20,stored.getDeliveryDays());assertEquals(LocalDate.of(2026,12,10),stored.getEstimatedDeliveryDate());
    }
}
