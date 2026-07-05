package com.quju.platform.exception;

import com.quju.platform.dto.common.ApiResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 常见字段名 → 中文映射 */
    private static final Map<String, String> FIELD_ZH = Map.ofEntries(
            Map.entry("title", "活动名称"),
            Map.entry("description", "活动简介"),
            Map.entry("startTime", "开始时间"),
            Map.entry("endTime", "结束时间"),
            Map.entry("registrationDeadline", "报名截止时间"),
            Map.entry("maxParticipants", "人数上限"),
            Map.entry("minAge", "最低年龄"),
            Map.entry("feeType", "费用类型"),
            Map.entry("feeAmount", "费用金额")
    );

    /** Spring 校验消息 → 中文 */
    private static String translateMessage(String template, String field) {
        if (template == null) return "输入不合法";
        if (template.contains("NotBlank") || template.contains("must not be blank")) return "请填写" + field;
        if (template.contains("NotNull") || template.contains("must not be null")) return "请填写" + field;
        if (template.contains("Future") || template.contains("must be a future")) return field + "必须是将来时间";
        if (template.contains("Min") || template.contains("must be greater")) return field + "必须大于0";
        if (template.contains("Size") || template.contains("size must be")) return field + "长度超出限制";
        // 兜底：返回模板本身（对于 Spring 默认消息通常很长，截取关键部分）
        String shortMsg = template;
        if (shortMsg.length() > 60) shortMsg = shortMsg.substring(0, 60) + "...";
        return field + "：" + shortMsg;
    }

    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> handleBusiness(BusinessException ex) {
        return ApiResponse.fail(ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleMethodArgument(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> {
                    String fieldZh = FIELD_ZH.getOrDefault(e.getField(), e.getField());
                    return translateMessage(e.getDefaultMessage(), fieldZh);
                })
                .collect(Collectors.joining("；"));
        return ApiResponse.fail(40000, msg.isEmpty() ? "请求参数不合法" : msg);
    }

    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleBind(BindException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> {
                    String fieldZh = FIELD_ZH.getOrDefault(e.getField(), e.getField());
                    return translateMessage(e.getDefaultMessage(), fieldZh);
                })
                .collect(Collectors.joining("；"));
        return ApiResponse.fail(40000, msg.isEmpty() ? "请求参数不合法" : msg);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleConstraint(ConstraintViolationException ex) {
        String msg = ex.getConstraintViolations().stream()
                .map(v -> {
                    String path = v.getPropertyPath().toString();
                    String fieldZh = FIELD_ZH.getOrDefault(path, path);
                    return translateMessage(v.getMessage(), fieldZh);
                })
                .collect(Collectors.joining("；"));
        return ApiResponse.fail(40000, msg.isEmpty() ? "请求参数不合法" : msg);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleUnknown(Exception ex) {
        log.error("Unexpected error", ex);
        return ApiResponse.fail(50000, "服务器内部错误，请稍后重试");
    }
}
