package com.agricient.backend.Service;

public interface SmsSender {

    /** The result of one send attempt. */
    record Result(boolean delivered, String status, String error) {
    }

    /** Sends one text. Must never throw; report problems in the Result. */
    Result send(String phone, String message);
}
