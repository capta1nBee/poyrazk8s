package com.k8s.platform.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class ExecRequest {
    private String container;
    private List<String> command;
    private Boolean tty = true;
    private Boolean stdin = true;
}

