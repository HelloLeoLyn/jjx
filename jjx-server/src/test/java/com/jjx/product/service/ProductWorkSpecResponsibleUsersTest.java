package com.jjx.product.service;

import com.jjx.common.constant.UserConstants;
import com.jjx.product.mapper.ProductWorkSpecMapper;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 执行真实候选 SQL，使用隔离 H2；不代表生产库或页面点验。 */
class ProductWorkSpecResponsibleUsersTest {
    @Test
    void onlyActiveEngineeringUsersAppearOnceAndEmptyCandidatesAreSafe() {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false");
        var config = new Configuration(new Environment("responsible-users", new JdbcTransactionFactory(), ds));
        config.addMapper(ProductWorkSpecMapper.class);
        try (var session = new SqlSessionFactoryBuilder().build(config).openSession(true)) {
            session.getConnection();
            var db = new JdbcTemplate(ds);
            db.execute("CREATE TABLE sys_user(user_id BIGINT PRIMARY KEY,user_name VARCHAR(50),nick_name VARCHAR(50),status CHAR(1),del_flag CHAR(1))");
            db.execute("CREATE TABLE sys_role(role_id BIGINT PRIMARY KEY,role_key VARCHAR(100),status CHAR(1),del_flag CHAR(1))");
            db.execute("CREATE TABLE sys_user_role(user_id BIGINT,role_id BIGINT,PRIMARY KEY(user_id,role_id))");
            db.execute("INSERT INTO sys_role VALUES (1,'engineering:ops','0','0'),(2,'engineering:all','0','0'),(3,'admin','0','0'),(4,'sales:all','0','0'),(5,'engineering:review','1','0'),(6,'engineering:review','0','1')");
            db.execute("INSERT INTO sys_user VALUES (1,'eng1','A工程','0','0'),(2,'admin','管理员','0','0'),(3,'sales','销售','0','0'),(4,'disabled','停用工程','1','0'),(5,'deleted','删除工程','0','1'),(6,'eng6','','0','0'),(7,'disabled-role','停用角色','0','0'),(8,'deleted-role','删除角色','0','0'),(9,'no-role','无角色','0','0')");
            db.execute("INSERT INTO sys_user_role VALUES (1,1),(1,2),(1,3),(2,3),(3,4),(4,1),(5,1),(6,2),(7,5),(8,6)");
            var mapper = session.getMapper(ProductWorkSpecMapper.class);
            var rows = mapper.responsibleUsers(UserConstants.USER_STATUS_NORMAL);
            assertEquals(List.of(6L, 1L), rows.stream().map(row -> ((Number) row.get("id")).longValue()).toList());
            assertEquals(List.of("eng6", "A工程"), rows.stream().map(row -> row.get("name")).toList());
            db.execute("DELETE FROM sys_user_role");
            session.clearCache();
            assertTrue(mapper.responsibleUsers(UserConstants.USER_STATUS_NORMAL).isEmpty());
        }
    }
}
