package com.jjx.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.jjx.common.core.result.Result;
import com.jjx.framework.common.controller.BaseController;
import com.jjx.system.domain.entity.SysTag;
import com.jjx.system.service.ISysTagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 系统标签（通用标签体系，dev-20260911-007）
 *
 * <p>任何业务模块都能用：标签主数据按 tagGroup 分组管理；业务关联走
 * bizType + bizId 通用多对多（sys_tag_rel），例如供应商 = purchase_supplier。</p>
 */
@Tag(name = "系统标签")
@RestController
@RequestMapping("/system/tag")
@RequiredArgsConstructor
public class SysTagController extends BaseController {

    private final ISysTagService tagService;

    /**
     * 标签列表（可按分组/关键字/状态筛选）
     */
    @Operation(summary = "标签列表")
    @SaCheckPermission("system:tag:view")
    @GetMapping("/list")
    public Result<List<SysTag>> list(@RequestParam(required = false) String tagGroup,
                                     @RequestParam(required = false) String keyword,
                                     @RequestParam(required = false) Integer status) {
        return Result.success(tagService.listTags(tagGroup, keyword, status));
    }

    /**
     * 新增标签
     */
    @Operation(summary = "新增标签")
    @SaCheckPermission("system:tag:add")
    @PostMapping
    public Result<SysTag> add(@RequestBody SysTag tag) {
        return Result.success(tagService.createTag(tag, getUsername()));
    }

    /**
     * 修改标签
     */
    @Operation(summary = "修改标签")
    @SaCheckPermission("system:tag:edit")
    @PutMapping("/{tagId}")
    public Result<Void> update(@PathVariable Long tagId, @RequestBody SysTag tag) {
        tag.setTagId(tagId);
        tagService.updateTag(tag, getUsername());
        return Result.success();
    }

    /**
     * 删除标签（支持批量）
     */
    @Operation(summary = "删除标签")
    @SaCheckPermission("system:tag:delete")
    @DeleteMapping("/{tagIds}")
    public Result<Void> remove(@PathVariable Long[] tagIds) {
        for (Long tagId : tagIds) {
            tagService.deleteTag(tagId, getUsername());
        }
        return Result.success();
    }

    /**
     * 查询某业务对象已挂标签
     */
    @Operation(summary = "查询业务对象标签")
    @SaCheckPermission("system:tag:view")
    @GetMapping("/rel")
    public Result<List<SysTag>> bizTags(@RequestParam String bizType, @RequestParam Long bizId) {
        return Result.success(tagService.getBizTags(bizType, bizId));
    }

    /**
     * 重设某业务对象标签（全量替换，空集合=清空）
     */
    @Operation(summary = "设置业务对象标签")
    @SaCheckPermission("system:tag:edit")
    @PostMapping("/rel")
    public Result<Void> setBizTags(@RequestParam String bizType,
                                   @RequestParam Long bizId,
                                   @RequestBody(required = false) List<Long> tagIds) {
        tagService.setBizTags(bizType, bizId, tagIds, getUsername());
        return Result.success();
    }

    /**
     * 按标签反查业务ID（列表按标签筛选用）
     */
    @Operation(summary = "按标签反查业务ID")
    @SaCheckPermission("system:tag:view")
    @GetMapping("/biz-ids")
    public Result<List<Long>> bizIdsByTag(@RequestParam String bizType, @RequestParam Long tagId) {
        return Result.success(tagService.getBizIdsByTagIds(bizType, List.of(tagId)));
    }
}
