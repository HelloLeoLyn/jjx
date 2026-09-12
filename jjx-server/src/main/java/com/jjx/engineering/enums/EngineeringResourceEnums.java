package com.jjx.engineering.enums;

public final class EngineeringResourceEnums {
    private EngineeringResourceEnums() {}

    public enum ResourceType { FILM, SCREEN_FRAME, SCREEN_PLATE, DIE }
    public enum ScreenFrameStatus { EMPTY, PLATED, MAINTENANCE, SCRAPPED }
    public enum ScreenPlateStatus { ACTIVE, WASHED, VOID }
    public enum DieStatus { AVAILABLE, MAINTENANCE, STOPPED, REPLACED, SCRAPPED }
    public enum ActionType { PLATE, WASH, REPAIR, REMAKE, STOP, ENABLE, SCRAP }
}
