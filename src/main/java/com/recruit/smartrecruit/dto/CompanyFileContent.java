package com.recruit.smartrecruit.dto;

/** Service 到 Controller 的内部文件响应，不作为 JSON 返回。 */
public record CompanyFileContent(byte[] bytes, String contentType, String filename) {
}
