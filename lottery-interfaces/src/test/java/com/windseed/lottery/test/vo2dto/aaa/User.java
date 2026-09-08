package com.windseed.lottery.test.vo2dto.aaa;

import javax.xml.crypto.Data;

@lombok.Data
public class User {

    private Long id;
    private String userId;
    private String userNickName;
    private String userHead;
    private String userPassword;
    private Data createTime;
    private Data updateTime;

}
