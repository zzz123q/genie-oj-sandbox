package com.zzz123q.genieojsandbox;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.command.StatsCmd;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PullResponseItem;
import com.github.dockerjava.api.model.Statistics;
import com.github.dockerjava.api.model.StreamType;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.DockerClientBuilder;
import com.zzz123q.genieojsandbox.model.ExecuteCodeResponse;
import com.zzz123q.genieojsandbox.model.ExecuteMessage;
import com.zzz123q.genieojsandbox.model.JudgeInfo;

import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * Java代码沙箱Docker实现
 */
@Component
@Slf4j
public class JavaDockerCodeSandbox extends JavaCodeSandboxTemplate {

    private static final Long TIME_OUT = 5000L;

    private static final Boolean FIRST_INIT = false;

    @Override
    public List<ExecuteMessage> runUserCode(List<String> inputList, File userCodeFile) {
        String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();

        // 创建容器,把文件上传到容器内
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();

        String image = "openjdk:8-alpine";
        // 拉取镜像
        if (FIRST_INIT) {
            try {
                dockerClient.pullImageCmd(image)
                        .exec(new PullImageResultCallback() {
                            @Override
                            public void onNext(PullResponseItem item) {
                                System.out.println("下载镜像中:" + item.getStatus());
                                super.onNext(item);
                            }
                        })
                        .awaitCompletion();
            } catch (InterruptedException e) {
                System.out.println("拉取镜像异常");
                e.printStackTrace();
            }
            System.out.println("镜像下载完成");
        }

        // 创建容器
        HostConfig hostConfig = new HostConfig();
        hostConfig.setBinds(new Bind(userCodeParentPath, new Volume("/app"))); // 文件路径映射
        hostConfig.withMemory(100 * 1024 * 1024L); // 内存限制
        hostConfig.withMemorySwap(0L);
        hostConfig.withCpuCount(1L);
        String profileConfig = ResourceUtil.readUtf8Str("security/profile.json");
        hostConfig.withSecurityOpts(Arrays.asList("seccomp=" + profileConfig));
        hostConfig.withPrivileged(true);

        CreateContainerResponse createContainerResponse = dockerClient.createContainerCmd(image)
                .withHostConfig(hostConfig)
                .withNetworkDisabled(true)
                .withAttachStdin(true)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withTty(true)
                .exec();
        String containerId = createContainerResponse.getId();

        // 启动容器,执行代码
        dockerClient.startContainerCmd(containerId).exec();

        // docker exec #{containerId} java -cp /app Main #{params}
        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        for (String input : inputList) {
            StopWatch stopWatch = new StopWatch();

            String[] inputArr = input.split(" ");
            String[] cmdArray = ArrayUtil.append(new String[] { "java", "-cp", "/app", "Main" }, inputArr);
            ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId)
                    .withCmd(cmdArray)
                    .withAttachStdin(true)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .exec();

            String execId = execCreateCmdResponse.getId();
            final String[] message = { null };
            final String[] errorMessage = { null };
            long time = 0L;
            final long[] memory = { 0L };
            final boolean[] ifTimeout = { true };
            try {
                // 获取占用内存
                ResultCallback<Statistics> statisticsResultCallback = new ResultCallback.Adapter<Statistics>() {

                    @Override
                    public void onNext(Statistics statistics) {
                        Long usage = statistics.getMemoryStats().getUsage();
                        // System.out.println("内存占用: " + usage);
                        memory[0] = Math.max(memory[0], usage);
                        super.onNext(statistics);
                    }

                };
                StatsCmd statsCmd = dockerClient.statsCmd(containerId);
                statsCmd.exec(statisticsResultCallback);

                // 获取运行时间
                stopWatch.start();

                dockerClient.execStartCmd(execId).exec(new ResultCallback.Adapter<Frame>() {

                    // TODO: 有时会分多段输出
                    @Override
                    public void onNext(Frame frame) {
                        StreamType streamType = frame.getStreamType();
                        if (StreamType.STDERR.equals(streamType)) {
                            String payload = new String(frame.getPayload());
                            payload = payload.substring(0, payload.length() - 1);
                            System.out.println("错误结果输出:" + payload);
                            errorMessage[0] = payload;
                        } else {
                            String payload = new String(frame.getPayload());
                            payload = payload.substring(0, payload.length() - 1);
                            System.out.println("结果输出:" + payload);
                            message[0] = payload;
                        }
                        super.onNext(frame);
                    }

                    @Override
                    public void onComplete() {
                        ifTimeout[0] = false;
                        super.onComplete();
                    }

                }).awaitCompletion(TIME_OUT, TimeUnit.MILLISECONDS);

                stopWatch.stop();
                time = stopWatch.getLastTaskTimeMillis();

                Thread.sleep(1000);
                statsCmd.close();

            } catch (InterruptedException e) {
                System.out.println("程序执行过程中出现异常");
                e.printStackTrace();
            }
            ExecuteMessage executeMessage = new ExecuteMessage();
            executeMessage.setMessage(message[0]);
            executeMessage.setErrorMessage(errorMessage[0]);
            executeMessage.setTime(time);
            executeMessage.setMemory(memory[0]);
            executeMessageList.add(executeMessage);
        }

        dockerClient.stopContainerCmd(containerId).exec();
        dockerClient.removeContainerCmd(containerId).exec();
        try {
            dockerClient.close();
        } catch (IOException e) {
            log.error("关闭客户端时出现异常", e);
            e.printStackTrace();
        }

        log.info("程序运行结果为: {}", executeMessageList);
        return executeMessageList;
    }

    @Override
    public ExecuteCodeResponse getOutputResponse(List<ExecuteMessage> executeMessageList) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        List<String> outputList = new ArrayList<>();
        Long maxTime = 0L;
        Long maxMemory = 0L;
        for (ExecuteMessage message : executeMessageList) {
            String errorMessage = message.getErrorMessage();
            Long time = message.getTime();
            Long memory = message.getMemory();
            if (StrUtil.isNotBlank(errorMessage)) {
                executeCodeResponse.setMessage(errorMessage);
                break;
            }
            outputList.add(message.getMessage());
            if (time != null) {
                maxTime = Math.max(maxTime, time);
            }
            if (memory != null) {
                maxMemory = Math.max(maxMemory, memory);
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
        judgeInfo.setMemory(maxMemory);
        executeCodeResponse.setJudgeInfo(judgeInfo);

        log.info("沙箱最终输出: {}", executeCodeResponse);
        return executeCodeResponse;
    }
}
