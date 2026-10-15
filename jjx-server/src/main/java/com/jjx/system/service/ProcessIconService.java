package com.jjx.system.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.json.JSONUtil;
import com.jjx.common.exception.BusinessException;
import com.jjx.common.enums.YesNoEnum;
import com.jjx.system.domain.dto.SysDictItemDTO;
import com.jjx.system.domain.entity.SysAttachment;
import com.jjx.system.enums.AttachmentUploadState;
import com.jjx.system.mapper.SysAttachmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;
import java.util.Set;

/** 图标是全局共享资产；登记成功即永久保留，不随工序换图或字典停用而删除。 */
@Service
@RequiredArgsConstructor
public class ProcessIconService {
    public static final String DICTIONARY = "engineering_jjx_icon";
    private static final Set<String> CATEGORIES = Set.of("mianban", "shangxian", "xiaxian", "tanpian", "others", "nouse");
    private final AttachmentLifecycleService lifecycle;
    private final SysAttachmentMapper files;
    private final SysDictService dictionaries;
    private final JdbcTemplate jdbc;

    public static boolean isIcon(SysAttachment file) {
        return file != null && file.getUploadState() == AttachmentUploadState.ASSET
            && DICTIONARY.equals(file.getBizType());
    }

    @Transactional(rollbackFor = Exception.class)
    public Long upload(MultipartFile input, String requestKey, String label, String category) {
        StpUtil.checkLogin();
        StpUtil.checkPermissionOr("engineering:standardProcess:add", "engineering:standardProcess:edit");
        if (label == null || label.isBlank() || label.strip().length() > 50)
            throw new BusinessException("图标名称应为1至50个字符");
        if (category == null || !CATEGORIES.contains(category)) throw new BusinessException("图标分类无效");
        if (requestKey == null || !requestKey.matches("[A-Za-z0-9_-]{16,95}"))
            throw new BusinessException("图标上传必须提供16至95位requestKey");
        String mime = ProcessIconValidator.validate(input);
        // 为图标入口区分幂等键；文件内容及上传者仍须通过校验。
        Long id = lifecycle.upload(input, "icon_" + requestKey);
        SysAttachment file = files.selectActiveForUpdate(id);
        if (file == null) throw new BusinessException("图标上传记录不存在");
        String url = "/api/system/attachment/download/" + id;
        String key = "jjx-upload-" + id;
        if (isIcon(file)) {
            // 响应丢失重试只返回原资产；不重复建字典、不重新启用已停用项。
            Integer registered = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_dict_item WHERE dict_code=? AND item_key=? AND item_value=? AND label=? AND deleted=0",
                Integer.class, DICTIONARY, key, url, label.strip());
            if (registered == null || registered != 1) throw new BusinessException("图标登记已变化，请刷新后重新上传");
            return id;
        }
        if (file.getUploadState() != AttachmentUploadState.TEMP
                || !StpUtil.getLoginIdAsString().equals(file.getUploadOwner()))
            throw new BusinessException("仅可登记本次上传的临时图标");
        SysDictItemDTO item = new SysDictItemDTO();
        item.setDictCode(DICTIONARY); item.setItemKey(key); item.setItemValue(url);
        item.setLabel(label.strip()); item.setSortOrder(999); item.setIsActive(YesNoEnum.YES.getCode());
        item.setRemark(JSONUtil.toJsonStr(Map.of("category", category, "source", "UPLOAD", "previewUrl", url)));
        if (dictionaries.insertDictItem(item) != 1) throw new BusinessException("图标登记失败");
        int updated = jdbc.update("UPDATE sys_attachment SET upload_state=?,expires_at=NULL,biz_type=?,file_type=? WHERE id=? AND upload_state=? AND deleted=0",
            AttachmentUploadState.ASSET.name(), DICTIONARY, mime, id, AttachmentUploadState.TEMP.name());
        if (updated != 1) throw new BusinessException("图标永久化失败");
        return id;
    }
}
