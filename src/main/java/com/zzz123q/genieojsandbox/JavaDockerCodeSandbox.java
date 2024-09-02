package com.zzz123q.genieojsandbox;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

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
import com.zzz123q.genieojsandbox.model.ExecuteCodeRequest;
import com.zzz123q.genieojsandbox.model.ExecuteCodeResponse;
import com.zzz123q.genieojsandbox.model.ExecuteMessage;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.ArrayUtil;

/**
 * Java代码沙箱Docker实现
 */
public class JavaDockerCodeSandbox extends JavaCodeSandboxTemplate {

    private static final Long TIME_OUT = 5000L;

    private static final Boolean FIRST_INIT = false;

    public static void main(String[] args) {
        JavaDockerCodeSandbox javaDockerCodeSandbox = new JavaDockerCodeSandbox();

        List<String> inpuList = Arrays.asList("1 2", "3 4");
        String code = ResourceUtil.readStr("testCode/simpleComputeArgs/Main.java", StandardCharsets.UTF_8);
        // String code = ResourceUtil.readStr("testCode/simpleCompute/Main.java",
        // StandardCharsets.UTF_8);
        String language = "java";

        ExecuteCodeRequest executeCodeRequest = ExecuteCodeRequest.builder()
                .code(code)
                .language(language)
                .inputList(inpuList)
                .build();
        ExecuteCodeResponse executeCodeResponse = javaDockerCodeSandbox.executeCode(executeCodeRequest);
        System.out.println(executeCodeResponse);
    }

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
                        System.out.println("内存占用: " + usage);
                        memory[0] = Math.max(memory[0], usage);
                        super.onNext(statistics);
                    }

                };
                StatsCmd statsCmd = dockerClient.statsCmd(containerId);
                statsCmd.exec(statisticsResultCallback);

                // 获取运行时间
                stopWatch.start();

                dockerClient.execStartCmd(execId).exec(new ResultCallback.Adapter<Frame>() {

                    @Override
                    public void onNext(Frame frame) {
                        StreamType streamType = frame.getStreamType();
                        if (StreamType.STDERR.equals(streamType)) {
                            errorMessage[0] = new String(frame.getPayload());
                            System.out.println("错误结果输出:" + errorMessage[0]);
                        } else {
                            message[0] = new String(frame.getPayload());
                            System.out.println("结果输出:" + message[0]);
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

        return executeMessageList;
    }
}
