package com.permacore.iam.domain.vo;

import lombok.Data;

@Data
public class UserQueryVO {
    private Integer pageNo = 1;
    private Integer pageSize = 10;
    private String username;
    private String nickname;
    private Integer status;
    private Long deptId;
}

/*
 * 非 Lombok 版本示例：
 * public class UserQueryVO {
 *     private Integer pageNo = 1;
 *     private Integer pageSize = 10;
 *     private String username;
 *     private String nickname;
 *     private Integer status;
 *
 *     public Integer getPageNo() { return pageNo; }
 *     public void setPageNo(Integer pageNo) { this.pageNo = pageNo; }
 *     public Integer getPageSize() { return pageSize; }
 *     public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }
 *     public String getUsername() { return username; }
 *     public void setUsername(String username) { this.username = username; }
 *     public String getNickname() { return nickname; }
 *     public void setNickname(String nickname) { this.nickname = nickname; }
 *     public Integer getStatus() { return status; }
 *     public void setStatus(Integer status) { this.status = status; }
 *     @Override
 *     public String toString() {
 *         return "UserQueryVO{" +
 *             "pageNo=" + pageNo +
 *             ", pageSize=" + pageSize +
 *             ", username='" + username + '\'' +
 *             ", nickname='" + nickname + '\'' +
 *             ", status=" + status +
 *             '}';
 *     }
 * }
 */
