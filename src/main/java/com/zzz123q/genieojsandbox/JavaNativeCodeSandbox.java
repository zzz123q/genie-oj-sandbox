package com.zzz123q.genieojsandbox;

import org.springframework.stereotype.Component;

import com.zzz123q.genieojsandbox.model.ExecuteCodeRequest;
import com.zzz123q.genieojsandbox.model.ExecuteCodeResponse;

/**
 * Java原生代码沙箱
 */
@Component
public class JavaNativeCodeSandbox extends JavaCodeSandboxTemplate {

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        return super.executeCode(executeCodeRequest);
    }

}
