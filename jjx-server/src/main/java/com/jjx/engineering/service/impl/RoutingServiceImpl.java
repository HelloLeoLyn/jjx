package com.jjx.engineering.service.impl;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.common.core.page.PageResult;
import com.jjx.engineering.domain.entity.Routing;
import com.jjx.engineering.mapper.RoutingMapper;
import com.jjx.engineering.service.IRoutingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Collections;
@Service @RequiredArgsConstructor
public class RoutingServiceImpl extends ServiceImpl<RoutingMapper, Routing> implements IRoutingService {
    @Override
    public PageResult<?> listPage(Object query) { return PageResult.build(Collections.emptyList(), 0); }
}
