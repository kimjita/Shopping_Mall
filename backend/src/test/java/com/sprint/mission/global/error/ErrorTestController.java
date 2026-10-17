package com.sprint.mission.global.error;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test/errors")
class ErrorTestController {

    @GetMapping("/business")
    void business() {
        throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @PostMapping("/validation")
    void validation(@Valid @RequestBody Req req) {
    }

    @GetMapping("/param")
    void param(@RequestParam @Min(1) int page) {
    }

    @GetMapping("/unexpected")
    void unexpected() {
        throw new IllegalStateException("db password=secret");
    }

    record Req(@NotBlank String name, @Min(1) int quantity) {
    }
}
