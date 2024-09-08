package com.zzz123q.genieojsandbox.controller;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
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

    // 定义鉴权请求头和密钥
    private static final String AUTH_REQUEST_HEADER = "auth";

    private static final String AUTH_REQUEST_SECRET = "secret";

    private CodeSandbox codeSandbox;

    @Autowired
    public void setCodeSandbox(ApplicationContext context, @Value("${codesandbox.name}") String name) {
        this.codeSandbox = (CodeSandbox) context.getBean(name);
    }

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
    public ExecuteCodeResponse executeCode(@RequestBody ExecuteCodeRequest executeCodeRequest,
            HttpServletRequest request, HttpServletResponse response) {
        // 进行基本的认证
        String authHeader = request.getHeader(AUTH_REQUEST_HEADER);
        if (!AUTH_REQUEST_SECRET.equals(authHeader)) {
            response.setStatus(403);
            return null;
        }
        if (executeCodeRequest == null) {
            throw new RuntimeException("请求参数为空");
        }
        return codeSandbox.executeCode(executeCodeRequest);
    }
}
