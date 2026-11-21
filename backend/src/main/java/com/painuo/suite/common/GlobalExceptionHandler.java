package com.painuo.suite.common;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理。
 *
 * <p>目标：任何异常都以统一 {@link Result} 结构返回，且 HTTP 状态码语义正确，
 * 前端拦截器只需一套逻辑即可（401 跳登录、403 提示无权限、其它 toast message）。</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务异常：可预期，不打堆栈，只打 warn。 */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<Void>> handleBiz(BizException ex) {
        log.warn("[biz] code={} msg={}", ex.resultCode().code(), ex.getMessage());
        return ResponseEntity.status(ex.resultCode().httpStatus())
                .body(Result.fail(ex.resultCode(), ex.getMessage()));
    }

    /** @Valid 校验失败：把字段级错误拼成可读文案。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleInvalid(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(this::describeFieldError)
                .collect(Collectors.joining("; "));
        return badRequest(detail);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<Void>> handleBind(BindException ex) {
        String detail = ex.getFieldErrors().stream()
                .map(this::describeFieldError)
                .collect(Collectors.joining("; "));
        return badRequest(detail);
    }

    /** 方法参数上的 @NotBlank/@Min 等（@Validated 在类上时走这条）。 */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraint(ConstraintViolationException ex) {
        String detail = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        return badRequest(detail);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<Void>> handleMissingParam(MissingServletRequestParameterException ex) {
        return badRequest("缺少必要参数：" + ex.getParameterName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return badRequest("参数类型不正确：" + ex.getName());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleUnreadable(HttpMessageNotReadableException ex) {
        return badRequest("请求体格式不正确，需为合法 JSON");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethod(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(ResultCode.METHOD_NOT_ALLOWED.httpStatus())
                .body(Result.fail(ResultCode.METHOD_NOT_ALLOWED, "不支持的请求方法：" + ex.getMethod()));
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<Result<Void>> handleNotFound(Exception ex) {
        return ResponseEntity.status(ResultCode.NOT_FOUND.httpStatus())
                .body(Result.fail(ResultCode.NOT_FOUND, "接口或资源不存在"));
    }

    /** 唯一索引冲突：并发场景（如重复报名）兜底。 */
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Result<Void>> handleDuplicate(DuplicateKeyException ex) {
        log.warn("[duplicate-key] {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(ResultCode.CONFLICT.httpStatus())
                .body(Result.fail(ResultCode.CONFLICT, "数据已存在，请勿重复提交"));
    }

    /** 兜底：非预期异常，打完整堆栈，但绝不把堆栈吐给前端。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnknown(Exception ex) {
        log.error("[unhandled] traceId={}", TraceContext.currentTraceId(), ex);
        return ResponseEntity.status(ResultCode.INTERNAL_ERROR.httpStatus())
                .body(Result.fail(ResultCode.INTERNAL_ERROR));
    }

    private ResponseEntity<Result<Void>> badRequest(String detail) {
        return ResponseEntity.status(ResultCode.PARAM_INVALID.httpStatus())
                .body(Result.fail(ResultCode.PARAM_INVALID, detail));
    }

    private String describeFieldError(FieldError error) {
        return error.getField() + " " + error.getDefaultMessage();
    }
}
