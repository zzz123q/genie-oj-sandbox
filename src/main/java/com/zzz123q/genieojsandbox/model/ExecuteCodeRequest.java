package com.zzz123q.genieojsandbox.model;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ExecuteCodeRequest {

    /**
     * 测试用例列表
     */
    private List<String> inputList;

    /**
     * 用户提交的代码
     */
    private String code;

    /**
     * 用户提交所用编程语言
     */
    private String language;
}
