package com.jjx.system.enums;

/** LEGACY remains quarantined until historical references have been reviewed. */
public enum AttachmentUploadState {
    /** 未确认来源的历史行，待人工复核。 */
    LEGACY,
    /** 上传后尚未绑定业务来源（保留期 7 天）。 */
    TEMP,
    /** 已登记的永久共享资产，不参与业务引用和临时清理。 */
    ASSET,
    /** 已绑定业务来源。 */
    BOUND,
    /** 无归属的孤儿行。 */
    ORPHAN,
    /** 留痕式释放：磁盘文件已删，数据库记录与元数据保留（不可下载/预览）。 */
    PURGED
}
