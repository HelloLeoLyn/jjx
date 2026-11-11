package com.jjx.production.service;

import com.jjx.production.domain.vo.ManufacturingSnapshotVO;

/** 工单制造历史快照（dev-20261111-009 G1）：只读，按工单持久化 ID 组装。 */
public interface ManufacturingHistorySnapshotService {

    ManufacturingSnapshotVO snapshot(Long orderId, Integer depth);
}
