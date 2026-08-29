package com.jjx.system.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class SysConfigChangedEvent extends ApplicationEvent {

    private final String configGroup;
    private final String configKey;

    public SysConfigChangedEvent(Object source, String configGroup, String configKey) {
        super(source);
        this.configGroup = configGroup;
        this.configKey = configKey;
    }
}
