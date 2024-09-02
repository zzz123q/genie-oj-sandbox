package com.zzz123q.genieojsandbox.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.util.StopWatch;

import com.zzz123q.genieojsandbox.model.ExecuteMessage;

public class ProcessUtil {

    /**
     * 执行进程并获取信息
     * 
     * @param process
     * @param opName
     * @return
     */
    public static ExecuteMessage getProcessMessage(Process process, String opName) {
        ExecuteMessage executeMessage = new ExecuteMessage();

        try {
            StopWatch stopWatch = new StopWatch();
            stopWatch.start();

            // 等待命令行执行完毕,获取退出码
            int exitValue = process.waitFor();
            executeMessage.setExitValue(exitValue);
            if (exitValue == 0) {
                System.out.println(opName + "成功");
                BufferedReader bufferedReader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()));
                List<String> outputStrList = new ArrayList<>();
                String resultLine;
                while ((resultLine = bufferedReader.readLine()) != null) {
                    outputStrList.add(resultLine);
                }
                executeMessage.setMessage(String.join("\n", outputStrList));
            } else {
                System.out.println(opName + "失败,错误码:" + exitValue);
                // 获取正常输出
                BufferedReader bufferedReader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()));
                List<String> outputStrList = new ArrayList<>();
                String resultLine;
                while ((resultLine = bufferedReader.readLine()) != null) {
                    outputStrList.add(resultLine);
                }
                executeMessage.setMessage(String.join("\n", outputStrList));

                // 获取错误输出
                BufferedReader errorBufferedReader = new BufferedReader(
                        new InputStreamReader(process.getErrorStream()));
                List<String> errorOutputStrList = new ArrayList<>();
                String errorResultLine;
                while ((errorResultLine = errorBufferedReader.readLine()) != null) {
                    errorOutputStrList.add(errorResultLine);
                }
                executeMessage.setErrorMessage(String.join("\n", errorOutputStrList));
            }
            stopWatch.stop();
            executeMessage.setTime(stopWatch.getLastTaskTimeMillis());
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }

        return executeMessage;
    }

    /**
     * 执行交互式进程并获取信息
     * TODO: 仅供测试
     * 
     * @param process
     * @param opName
     * @param args
     * @return
     */
    public static ExecuteMessage getInteractiveProcessMessage(Process process, String opName, String args) {
        ExecuteMessage executeMessage = new ExecuteMessage();

        try {
            OutputStream outputStream = process.getOutputStream();
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream);
            String[] strs = args.split(" ");
            args = String.join("\n", strs) + "\n";
            outputStreamWriter.write(args);
            // 完成输入,执行
            outputStreamWriter.flush();

            // 等待命令行执行完毕,获取退出码
            System.out.println(opName + "成功");
            BufferedReader bufferedReader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String resultLine;
            while ((resultLine = bufferedReader.readLine()) != null) {
                sb.append(resultLine).append("\n");
            }
            executeMessage.setMessage(sb.toString());
            // 释放资源
            outputStreamWriter.close();
            outputStream.close();
            bufferedReader.close();
            process.destroy();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return executeMessage;
    }
}
