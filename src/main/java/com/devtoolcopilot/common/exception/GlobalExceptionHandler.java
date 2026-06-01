package com.devtoolcopilot.common.exception;

import com.devtoolcopilot.common.R;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.validation.BindException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ApiException.class)
    public R<Void> handleApiException(ApiException e, HttpServletResponse response) {
        response.setStatus(e.getCode());
        return R.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(UnauthorizedException.class)
    public R<Void> handleUnauthorized(UnauthorizedException e, HttpServletResponse response) {
        response.setStatus(401);
        return R.fail(401, e.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public R<Void> handleValidation(Exception e, HttpServletResponse response) {
        response.setStatus(400);
        return R.fail(400, "请求参数校验失败");
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public R<Void> handleBadRequest(Exception e, HttpServletResponse response) {
        response.setStatus(400);
        return R.fail(400, "请求参数错误");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public R<Void> handleIllegalArgument(IllegalArgumentException e, HttpServletResponse response) {
        response.setStatus(400);
        return R.fail(400, "请求参数错误");
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public R<Void> handleNotFound(Exception e, HttpServletResponse response) {
        response.setStatus(404);
        return R.fail(404, "接口不存在");
    }

    @ExceptionHandler(Exception.class)
    public R<Void> handleException(Exception e, HttpServletResponse response) {
        response.setStatus(500);
        return R.fail(500, "服务器错误");
    }
}
