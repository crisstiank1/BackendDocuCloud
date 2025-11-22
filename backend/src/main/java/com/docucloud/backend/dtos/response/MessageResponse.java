package com.docucloud.backend.dtos.response;

public class MessageResponse {
    private String message;

    public MessageResponse(String message) { this.message = message; }
    public String getMessage() { return message; }
}
