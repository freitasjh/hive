package br.com.systec.hive.commons.api.tools.exceptions;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serial;
import java.io.Serializable;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class StandardError implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String type = "about:blank";
    private String title;
    private Integer status;
    private String detail;
    private String instance;
    private String code;
    private Long timeStamp;

    public StandardError() {
    }

    public StandardError(Integer status, String detail, Long timeStamp) {
        this.status = status;
        this.detail = detail;
        this.title = mapTitle(status);
        this.timeStamp = timeStamp;
    }

    public StandardError(String type, String title, Integer status, String detail, String instance, String code) {
        this.type = type;
        this.title = title;
        this.status = status;
        this.detail = detail;
        this.instance = instance;
        this.code = code;
        this.timeStamp = System.currentTimeMillis();
    }

    private String mapTitle(Integer status) {
        if (status == null) return "Error";
        return switch (status) {
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 409 -> "Conflict";
            case 422 -> "Unprocessable Entity";
            case 500 -> "Internal Server Error";
            default -> "Error";
        };
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public String getInstance() {
        return instance;
    }

    public void setInstance(String instance) {
        this.instance = instance;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public Long getTimeStamp() {
        return timeStamp;
    }

    public void setTimeStamp(Long timeStamp) {
        this.timeStamp = timeStamp;
    }

    // Legacy compatibility: msg maps to title/detail
    public String getMsg() {
        return title != null ? title : detail;
    }

    public void setMsg(String msg) {
        this.title = msg;
        this.detail = msg;
    }

    public String getDetailMessage() {
        return detail;
    }

    public void setDetailMessage(String detailMessage) {
        this.detail = detailMessage;
    }
}
