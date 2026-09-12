package com.jjx.engineering.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.jjx.common.core.result.Result;
import com.jjx.engineering.service.EngineeringResourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/engineering/resources")
@RequiredArgsConstructor
public class EngineeringResourceController {
    private final EngineeringResourceService service;

    @GetMapping("/screen-frames") @SaCheckPermission("engineering:resource:view")
    public Result<List<Map<String,Object>>> frames(@RequestParam(required=false) String keyword,@RequestParam(required=false) String status){return Result.success(service.listFrames(keyword,status));}
    @PostMapping("/screen-frames") @SaCheckPermission("engineering:resource:edit")
    public Result<Long> saveFrame(@RequestBody Map<String,Object> body){return Result.success(service.saveFrame(body));}
    @PostMapping("/screen-plates") @SaCheckPermission("engineering:resource:edit")
    public Result<Long> plate(@RequestBody Map<String,Object> body){return Result.success(service.createPlate(body));}
    @PostMapping("/screen-frames/{id}/wash") @SaCheckPermission("engineering:resource:maintain")
    public Result<Void> wash(@PathVariable Long id,@RequestBody(required=false) Map<String,Object> body){service.washFrame(id,body==null?null:String.valueOf(body.getOrDefault("description","")));return Result.success();}
    @PostMapping("/screen-frames/{id}/actions") @SaCheckPermission("engineering:resource:maintain")
    public Result<Void> frameAction(@PathVariable Long id,@RequestBody Map<String,Object> body){service.actOnFrame(id,body);return Result.success();}

    @GetMapping("/dies") @SaCheckPermission("engineering:resource:view")
    public Result<List<Map<String,Object>>> dies(@RequestParam(required=false) String keyword,@RequestParam(required=false) String status){return Result.success(service.listDies(keyword,status));}
    @PostMapping("/dies") @SaCheckPermission("engineering:resource:edit")
    public Result<Long> saveDie(@RequestBody Map<String,Object> body){return Result.success(service.saveDie(body));}
    @PostMapping("/dies/{id}/actions") @SaCheckPermission("engineering:resource:maintain")
    public Result<Long> dieAction(@PathVariable Long id,@RequestBody Map<String,Object> body){return Result.success(service.actOnDie(id,body));}

    @GetMapping("/{type}/{id}/products") @SaCheckPermission("engineering:resource:view")
    public Result<List<Map<String,Object>>> products(@PathVariable String type,@PathVariable Long id){return Result.success(service.products(type,id));}
    @PutMapping("/{type}/{id}/products") @SaCheckPermission("engineering:resource:edit")
    public Result<Void> replaceProducts(@PathVariable String type,@PathVariable Long id,@RequestBody Map<String,Object> body){service.replaceProducts(type,id,body);return Result.success();}
    @GetMapping("/{type}/{id}/maintenance") @SaCheckPermission("engineering:resource:view")
    public Result<List<Map<String,Object>>> maintenance(@PathVariable String type,@PathVariable Long id){return Result.success(service.maintenance(type,id));}
}
