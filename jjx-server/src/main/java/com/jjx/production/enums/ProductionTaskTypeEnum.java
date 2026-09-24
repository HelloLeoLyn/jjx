package com.jjx.production.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 生产任务业务类型（production_task.task_type）。
 *
 * <p>dev-20260924-002：普通任务 = STANDARD；报废补产 = SUPPLEMENT（挂老工单末道工序的独立任务，
 * 带补料单/不良单来源，可独立派工与报工）。</p>
 */
@Getter
@RequiredArgsConstructor
public enum ProductionTaskTypeEnum {

    STANDARD("STANDARD", "普通任务"),

    SUPPLEMENT("SUPPLEMENT", "补产任务");

    private final String code;

    private final String label;
}
