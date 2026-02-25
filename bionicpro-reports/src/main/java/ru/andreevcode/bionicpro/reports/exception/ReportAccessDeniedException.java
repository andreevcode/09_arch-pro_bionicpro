package ru.andreevcode.bionicpro.reports.exception;

import lombok.Getter;

@Getter
public class ReportAccessDeniedException extends RuntimeException{
    private final String tokenUserId;
    private final String requestedUserId;

    public ReportAccessDeniedException(String tokenUserId, String requestedUserId) {
        this.tokenUserId = tokenUserId;
        this.requestedUserId = requestedUserId;
    }
}
