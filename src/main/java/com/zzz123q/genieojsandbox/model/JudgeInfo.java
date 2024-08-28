package com.zzz123q.genieojsandbox.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 判题信息
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class JudgeInfo {

    /**
     * 程序执行信息
     */
    private String message;

    /**
     * 执行内存(KB)
     */
    private Long memory;

    /**
     * 执行时间(ms)
     */
    private Long time;

}
