package com.jjx.engineering.printing.artwork;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.common.exception.BusinessException;
import com.jjx.system.utils.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;

/** 旧申请即实物入口必须拒绝，历史幂等处置仍可重放。 */
class FilmDemandTest {
    @Test void oldPhysicalPlaceholderActionsCannotWriteNewRequests() {
        var source=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");
        var db=new JdbcTemplate(source);var tx=new TransactionTemplate(new DataSourceTransactionManager(source));
        db.execute("CREATE TABLE engineering_resource_maintenance(maintenance_id BIGINT AUTO_INCREMENT PRIMARY KEY,resource_type VARCHAR(20),resource_id BIGINT,action_type VARCHAR(20),operator VARCHAR(64),request_id VARCHAR(64) UNIQUE,request_hash VARCHAR(64),result_snapshot VARCHAR(10000),description VARCHAR(500),after_status VARCHAR(20))");
        var json=new ObjectMapper().findAndRegisterModules();var service=new ArtworkService(db,json,null);
        try(var user=mockStatic(SecurityUtils.class)) {
            user.when(SecurityUtils::getUsername).thenReturn("film-test");
            for(String action:List.of("requestFilms","sendFilm","receiveFilm")) {
                var command=json.convertValue(Map.of("requestId",action,"expectedRevision",0),ArtworkCommands.class);
                var error=assertThrows(BusinessException.class,()->tx.execute(status->service.execute(action,1,command)));
                assertEquals(410,error.getCode());
            }
            assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM engineering_resource_maintenance",Integer.class));
        }
    }
    @Test void historicalArchiveRequestBeforeOptionalDemandFieldsStillReplays() throws Exception {
        var source=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");
        var db=new JdbcTemplate(source);
        db.execute("CREATE TABLE engineering_resource_maintenance(maintenance_id BIGINT AUTO_INCREMENT PRIMARY KEY,resource_type VARCHAR(20),resource_id BIGINT,action_type VARCHAR(20),operator VARCHAR(64),request_id VARCHAR(64) UNIQUE,request_hash VARCHAR(64),result_snapshot VARCHAR(10000))");
        var json=new ObjectMapper();var command=json.convertValue(Map.of("requestId","old","expectedRevision",0,"reason","封存"),ArtworkCommands.class);
        var old=(com.fasterxml.jackson.databind.node.ObjectNode)json.valueToTree(command);old.remove("quantity");old.remove("dueDate");
        var hash=com.jjx.common.utils.Sha256Utils.ofText(json.writeValueAsString(List.of("FILM_COPY","archive",42L,"film-test",old)));
        db.update("INSERT INTO engineering_resource_maintenance(resource_type,resource_id,action_type,operator,request_id,request_hash,result_snapshot) VALUES('FILM_COPY',42,'archive','film-test','old',?,?)",hash,"{\"filmId\":42,\"status\":\"ARCHIVED\"}");
        try(var user=mockStatic(SecurityUtils.class)) {
            user.when(SecurityUtils::getUsername).thenReturn("film-test");
            assertEquals("ARCHIVED",new ArtworkService(db,json,null).execute("archive",42,command).get("status"));
        }
    }
}
