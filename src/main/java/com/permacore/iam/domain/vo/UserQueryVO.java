package com.permacore.iam.domain.vo;

import lombok.Data;

@Data
public class UserQueryVO {
    private Integer pageNo = 1;
    private Integer pageSize = 10;
    private String username;
    private String nickname;
    private Integer status;
}

