package com.zzz123q.genieojsandbox;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.zzz123q.genieojsandbox.model.ExecuteCodeRequest;
import com.zzz123q.genieojsandbox.model.ExecuteCodeResponse;
import com.zzz123q.genieojsandbox.model.ExecuteMessage;
import com.zzz123q.genieojsandbox.model.JudgeInfo;
import com.zzz123q.genieojsandbox.utils.ProcessUtil;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * Java代码沙箱模板类
 */
@Slf4j
public abstract class JavaCodeSandboxTemplate implements CodeSandbox {

    private static final String GLOBAL_CODE_DIE_NAME = "tmpCode";

    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";

    private static final Long TIME_OUT = 10000L;

    // private static final String SECURITY_MANAGER_CLASS_NAME =
    // "DefaultSecurityManager";

    // private static final String SECURITY_MANAGER_PATH =
    // "D:/oj/genie-oj-sandbox/src/main/resources/security";

    /**
     * 执行代码
     * 
     * @param executeCodeRequest
     * @return
     */
    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        List<String> inputList = executeCodeRequest.getInputList();
        String code = executeCodeRequest.getCode();

        File userCodeFile = saveUserCode(code);

        ExecuteMessage compileMessage = compileUserCode(userCodeFile);
        log.info(compileMessage.toString());

        List<ExecuteMessage> executeMessageList = runUserCode(inputList, userCodeFile);

        ExecuteCodeResponse executeCodeResponse = getOutputResponse(executeMessageList);

        Boolean isDeleted = deleteUserCode(userCodeFile);
        if (!isDeleted) {
            log.error("删除用户代码失败, userCodeParentPath = {}", userCodeFile.getAbsolutePath());
        }

        return executeCodeResponse;
    }

    /**
     * 1. 把用户代码保存为文件(隔离存放)
     * 
     * @param userCode
     * @return
     */
    public File saveUserCode(String userCode) {
        String userDir = System.getProperty("user.dir");
        String globalCodePath = userDir + File.separator + GLOBAL_CODE_DIE_NAME;
        if (!FileUtil.exist(globalCodePath)) {
            FileUtil.mkdir(globalCodePath);
        }

        String userCodeParentPath = globalCodePath + File.separator + UUID.randomUUID();
        String userCodePath = userCodeParentPath + File.separator + GLOBAL_JAVA_CLASS_NAME;
        File userCodeFile = FileUtil.writeString(userCode, userCodePath, StandardCharsets.UTF_8);

        return userCodeFile;
    }

    /**
     * 2. 编译用户代码,得到class文件
     * 
     * @param userCodeFile
     * @return
     */
    public ExecuteMessage compileUserCode(File userCodeFile) {
        String compileCmd = String.format("javac -encoding utf-8 %s", userCodeFile.getAbsolutePath());
        try {
            Process compileProcess = Runtime.getRuntime().exec(compileCmd);
            ExecuteMessage processMessage = ProcessUtil.getProcessMessage(compileProcess, "编译");
            if (processMessage.getExitValue() != 0) {
                throw new RuntimeException("编译错误");
            }
            return processMessage;
        } catch (Exception e) {
            throw new RuntimeException("编译过程中出现异常", e);
        }
    }

    /**
     * 3. 执行代码,得到输出结果
     * 
     * @param inputList
     * @param userCodeFile
     * @return
     */
    public List<ExecuteMessage> runUserCode(List<String> inputList, File userCodeFile) {
        String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();

        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        for (String input : inputList) {
            // String runCmd = String.format(
            // "java -Xmx256m -cp %s;%s -Djava.security.manager=%s Main %s",
            // userCodeParentPath, SECURITY_MANAGER_PATH, SECURITY_MANAGER_CLASS_NAME,
            // input);
            String runCmd = String.format("java -Xmx256m -cp %s Main %s", userCodeParentPath, input);
            try {
                Process runProcess = Runtime.getRuntime().exec(runCmd);

                new Thread(() -> {
                    try {
                        Thread.sleep(TIME_OUT);
                        runProcess.destroy();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }).start();

                ExecuteMessage processMessage = ProcessUtil.getProcessMessage(runProcess, "运行");
                // ExecuteMessage processMessage =
                // ProcessUtil.getInteractiveProcessMessage(runProcess, "运行", input);
                executeMessageList.add(processMessage);
                log.info(processMessage.toString());
            } catch (Exception e) {
                throw new RuntimeException("程序执行过程中出现异常", e);
            }
        }

        log.info("程序运行结果为: {}", executeMessageList);
        return executeMessageList;
    }

    /**
     * 4. 整理输出
     * 
     * @param executeMessageList
     * @return
     */
    public ExecuteCodeResponse getOutputResponse(List<ExecuteMessage> executeMessageList) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        List<String> outputList = new ArrayList<>();
        Long maxTime = 0L;
        for (ExecuteMessage message : executeMessageList) {
            String errorMessage = message.getErrorMessage();
            Long time = message.getTime();
            if (StrUtil.isNotBlank(errorMessage)) {
                executeCodeResponse.setMessage(errorMessage);
                break;
            }
            outputList.add(message.getMessage());
            if (time != null) {
                maxTime = Math.max(maxTime, time);
            }
        }
        if (outputList.size() == executeMessageList.size()) {
            executeCodeResponse.setStatus(2);
        } else {
            executeCodeResponse.setStatus(3);
        }
        executeCodeResponse.setOutputList(outputList);
        JudgeInfo judgeInfo = new JudgeInfo();
        judgeInfo.setTime(maxTime);
        // TODO: 借助第三方库获取内存占用
        // judgeInfo.setMemory();
        executeCodeResponse.setJudgeInfo(judgeInfo);

        log.info("沙箱最终输出: {}", executeCodeResponse);
        return executeCodeResponse;
    }

    /**
     * 5. 文件清理
     * 
     * @param userCodeFile
     * @return
     */
    public Boolean deleteUserCode(File userCodeFile) {
        if (userCodeFile.getParentFile() != null) {
            String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();
            boolean del = FileUtil.del(userCodeParentPath);
            log.info("程序文件夹删除" + (del ? "成功" : "失败"));
            return del;
        }
        return true;
    }

    /**
     * 6. 获取错误响应
     * 
     * @param e
     * @return
     */
    public ExecuteCodeResponse getErrorResponse(Throwable e) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        executeCodeResponse.setOutputList(new ArrayList<>());
        executeCodeResponse.setStatus(2);
        executeCodeResponse.setJudgeInfo(new JudgeInfo());
        executeCodeResponse.setMessage(e.getMessage());
        return executeCodeResponse;
    }

}
