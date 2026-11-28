package com.jjx.engineering.printing.search;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jjx.common.core.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 印刷资源统一搜索（dev-20261125-011）。 */
@Tag(name = "印刷资源搜索")
@RestController
@RequestMapping("/engineering/printing")
@RequiredArgsConstructor
public class PrintSearchController {

    private final PrintSearchService service;

    @GetMapping("/search")
    @Operation(summary = "按编号/名称多角度搜索图案版本、菲林、网版、网框")
    @SaCheckPermission("engineering:screen-resource:view")
    public Result<IPage<PrintSearchRow>> search(PrintSearchOption option) {
        return Result.success(service.search(option));
    }
    @GetMapping("/search/plates/{plateId}")
    @Operation(summary = "查询指定网版的关联资料（不改变资源状态）")
    @SaCheckPermission("engineering:screen-resource:view")
    public Result<PrintSearchRow> plate(@PathVariable long plateId) {
        return Result.success(service.plate(plateId));
    }
}
