package com.zzz123q.genieojsandbox;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.zzz123q.genieojsandbox.model.ExecuteCodeRequest;
import com.zzz123q.genieojsandbox.model.ExecuteCodeResponse;
import com.zzz123q.genieojsandbox.model.ExecuteMessage;
import com.zzz123q.genieojsandbox.model.JudgeInfo;
import com.zzz123q.genieojsandbox.utils.ProcessUtil;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.dfa.FoundWord;
import cn.hutool.dfa.WordTree;

public class JavaNativeCodeSandbox implements CodeSandbox {

    private static final String GLOBAL_CODE_DIE_NAME = "tmpCode";

    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";

    private static final Long TIME_OUT = 10000L;

    private static final List<String> blackList = Arrays.asList("Files", "exec");

    private static final WordTree WORD_TREE = new WordTree();

    static {
        WORD_TREE.addWords(blackList);
    }

    public static void main(String[] args) {
        JavaNativeCodeSandbox javaNativeCodeSandbox = new JavaNativeCodeSandbox();

        List<String> inpuList = Arrays.asList("1 2", "3 4");
        String code = ResourceUtil.readStr("testCode/runFileError/Main.java", StandardCharsets.UTF_8);
        // String code = ResourceUtil.readStr("testCode/simpleCompute/Main.java",
        // StandardCharsets.UTF_8);
        Long timeLimit = 1000L;
        String language = "java";

        ExecuteCodeRequest executeCodeRequest = ExecuteCodeRequest.builder()
                .code(code)
                .language(language)
                .inputList(inpuList)
                .timeLimit(timeLimit)
                .build();
        ExecuteCodeResponse executeCodeResponse = javaNativeCodeSandbox.executeCode(executeCodeRequest);
        System.out.println(executeCodeResponse);
    }

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
        String language = executeCodeRequest.getLanguage();
        Long timeLimit = executeCodeRequest.getTimeLimit();

        // 检验代码中是否存在敏感操作
        FoundWord matchWord = WORD_TREE.matchWord(code);
        if (matchWord != null) {
            System.out.println("发现敏感词: " + matchWord.getFoundWord());
            return null;
        }

        String userDir = System.getProperty("user.dir");
        String globalCodePath = userDir + File.separator + GLOBAL_CODE_DIE_NAME;
        if (!FileUtil.exist(globalCodePath)) {
            FileUtil.mkdir(globalCodePath);
        }

        // 1. 把用户代码保存为文件(隔离存放)
        String userCodeParentPath = globalCodePath + File.separator + UUID.randomUUID();
        String userCodePath = userCodeParentPath + File.separator + GLOBAL_JAVA_CLASS_NAME;
        File userCodeFile = FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);

        // 2. 编译用户代码,得到class文件
        String compileCmd = String.format("javac -encoding utf-8 %s", userCodeFile.getAbsolutePath());
        try {
            Process compileProcess = Runtime.getRuntime().exec(compileCmd);
            ExecuteMessage processMessage = ProcessUtil.getProcessMessage(compileProcess, "编译");
            System.out.println(processMessage);
        } catch (Exception e) {
            return getErrorResponse(e);
        }

        // 3. 执行代码,得到输出结果
        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        for (String input : inputList) {
            String runCmd = String.format("java -Xmx256m -cp %s Main %s", userCodeParentPath, input);
            try {
                Process runProcess = Runtime.getRuntime().exec(runCmd);

                new Thread(() -> {
                    try {
                        Thread.sleep(TIME_OUT);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    runProcess.destroy();
                }).start();

                ExecuteMessage processMessage = ProcessUtil.getProcessMessage(runProcess, "运行");
                // ExecuteMessage processMessage =
                // ProcessUtil.getInteractiveProcessMessage(runProcess, "运行", input);
                executeMessageList.add(processMessage);
                System.out.println(processMessage);
            } catch (Exception e) {
                return getErrorResponse(e);
            }
        }

        // 4. 整理输出
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
            if (maxTime <= timeLimit) {
                executeCodeResponse.setStatus(2);
            } else {
                executeCodeResponse.setStatus(3);
            }
        } else {
            executeCodeResponse.setStatus(3);
        }
        executeCodeResponse.setOutputList(outputList);
        JudgeInfo judgeInfo = new JudgeInfo();
        judgeInfo.setTime(maxTime);
        // TODO: 借助第三方库获取内存占用
        // judgeInfo.setMemory();
        executeCodeResponse.setJudgeInfo(judgeInfo);

        // 5. 文件清理
        if (userCodeFile.getParentFile() != null) {
            boolean del = FileUtil.del(userCodeParentPath);
            System.out.println("程序文件夹删除" + (del ? "成功" : "失败"));
        }

        return executeCodeResponse;
    }

    /**
     * 获取错误响应
     * 
     * @param e
     * @return
     */
    private ExecuteCodeResponse getErrorResponse(Throwable e) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        executeCodeResponse.setOutputList(new ArrayList<>());
        executeCodeResponse.setStatus(2);
        executeCodeResponse.setJudgeInfo(new JudgeInfo());
        executeCodeResponse.setMessage(e.getMessage());
        return executeCodeResponse;
    }
}
