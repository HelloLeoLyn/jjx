package com.jjx.inventory.controller;

import com.jjx.common.core.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestInventoryController {

    @GetMapping("/api/test/inventory")
    public Result<Void> a() {
        return Result.success();
    }

}
