package com.zzz123q.genieojsandbox.controller;

import javax.annotation.Resource;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.zzz123q.genieojsandbox.CodeSandbox;
import com.zzz123q.genieojsandbox.model.ExecuteCodeRequest;
import com.zzz123q.genieojsandbox.model.ExecuteCodeResponse;

import lombok.extern.slf4j.Slf4j;

@RestController("/")
@Slf4j
public class MainController {

    @Resource(name = "javaNativeCodeSandbox")
    private CodeSandbox codeSandbox;

    @GetMapping("/health")
    public String healthCheck() {
        return "ok";
    }

    /**
     * 执行代码
     * 
     * @param executeCodeRequest
     * @return
     */
    @PostMapping("/executeCode")
    public ExecuteCodeResponse executeCode(@RequestBody ExecuteCodeRequest executeCodeRequest) {
        if (executeCodeRequest == null) {
            throw new RuntimeException("请求参数为空");
        }
        return codeSandbox.executeCode(executeCodeRequest);
    }
}
