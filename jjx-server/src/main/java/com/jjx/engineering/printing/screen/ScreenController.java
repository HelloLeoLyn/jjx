package com.jjx.engineering.printing.screen;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.jjx.common.core.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/engineering/printing")
@RequiredArgsConstructor
public class ScreenController {
    private final ScreenService service;
    private final ScreenView view;
    private final ScreenRepository repository;

    @ExceptionHandler(ScreenService.RevisionConflict.class)
    public Result<Map<String,Object>> revisionConflict(ScreenService.RevisionConflict error) {
        Result<Map<String,Object>> result = Result.error(error.getCode(),error.getMessage());
        result.setData(Map.of("reasonCode","REVISION_CHANGED","revision",error.revision));
        return result;
    }

    @GetMapping("/frames")
    @SaCheckPermission("engineering:screen-resource:view")
    public Result<Map<String, Object>> listFrames(@RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) String status,
                                                  @RequestParam(defaultValue = "1") int pageNum,
                                                  @RequestParam(defaultValue = "20") int pageSize) {
        int pn = Math.max(pageNum, 1);
        int ps = Math.min(Math.max(pageSize, 1), 200);
        return Result.success(repository.listFrames(keyword, status, pn, ps));
    }

    @GetMapping("/frames/{id}")
    @SaCheckPermission("engineering:screen-resource:view")
    public Result<Map<String,Object>> getFrame(@PathVariable long id) {
        return Result.success(view.get(true,id));
    }

    @PostMapping("/frames")
    @SaCheckPermission("engineering:resource:edit")
    public Result<Map<String,Object>> createFrame(@RequestBody ScreenCommands command) {
        return Result.success(service.execute(true,0,"create",command));
    }

    @PutMapping("/frames/{id}")
    @SaCheckPermission("engineering:resource:edit")
    public Result<Map<String,Object>> editFrame(@PathVariable long id, @RequestBody ScreenCommands command) {
        return Result.success(service.execute(true,id,"edit",command));
    }

    @PostMapping("/frames/{id}/stretch")
    @SaCheckPermission("engineering:resource:maintain")
    public Result<Map<String,Object>> stretchFrame(@PathVariable long id, @RequestBody ScreenCommands command) {
        return Result.success(service.execute(true,id,"stretch",command));
    }

    @PostMapping("/frames/{id}/maintain")
    @SaCheckPermission("engineering:resource:maintain")
    public Result<Map<String,Object>> maintainFrame(@PathVariable long id, @RequestBody ScreenCommands command) {
        return Result.success(service.execute(true,id,"maintain",command));
    }

    @PostMapping("/frames/{id}/resume")
    @SaCheckPermission("engineering:resource:maintain")
    public Result<Map<String,Object>> resumeFrame(@PathVariable long id, @RequestBody ScreenCommands command) {
        return Result.success(service.execute(true,id,"resume",command));
    }

    @PostMapping("/frames/{id}/scrap")
    @SaCheckPermission("engineering:resource:maintain")
    public Result<Map<String,Object>> scrapFrame(@PathVariable long id, @RequestBody ScreenCommands command) {
        return Result.success(service.execute(true,id,"scrap",command));
    }

    @PostMapping("/frames/{id}/clean")
    @SaCheckPermission("engineering:resource:maintain")
    public Result<Map<String,Object>> cleanFrame(@PathVariable long id, @RequestBody ScreenCommands command) {
        return Result.success(service.execute(true,id,"clean",command));
    }

    @GetMapping("/plates/{id}")
    @SaCheckPermission("engineering:screen-resource:view")
    public Result<Map<String,Object>> getPlate(@PathVariable long id) {
        return Result.success(view.get(false,id));
    }

    @PostMapping("/plates")
    @SaCheckPermission("engineering:resource:edit")
    public Result<Map<String,Object>> createPlate(@RequestBody ScreenCommands command) {
        return Result.success(service.execute(false,0,"create",command));
    }

    @PostMapping("/plates/{id}/release")
    @SaCheckPermission("engineering:screen-resource:release")
    public Result<Map<String,Object>> releasePlate(@PathVariable long id, @RequestBody ScreenCommands command) {
        return Result.success(service.execute(false,id,"release",command));
    }

    @PostMapping("/plates/{id}/fail")
    @SaCheckPermission("engineering:screen-resource:release")
    public Result<Map<String,Object>> failPlate(@PathVariable long id, @RequestBody ScreenCommands command) {
        return Result.success(service.execute(false,id,"fail",command));
    }

    @PostMapping("/plates/{id}/wash")
    @SaCheckPermission("engineering:screen-resource:wash")
    public Result<Map<String,Object>> washPlate(@PathVariable long id, @RequestBody ScreenCommands command) {
        return Result.success(service.execute(false,id,"wash",command));
    }

    @PostMapping("/plates/{id}/scrap")
    @SaCheckPermission("engineering:resource:maintain")
    public Result<Map<String,Object>> scrapPlate(@PathVariable long id, @RequestBody ScreenCommands command) {
        return Result.success(service.execute(false,id,"scrap",command));
    }
}
