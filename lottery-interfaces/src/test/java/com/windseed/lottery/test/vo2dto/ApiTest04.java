package com.windseed.lottery.test.vo2dto;

import com.windseed.lottery.test.vo2dto.ccc.User;

/**
 * @description: 继承对象转换
 */
public class ApiTest04 {

    public void test_vo2dto(User user) {

        UserDTO userDTO = new UserDTO();
        userDTO.setUserId(user.getUserId());
        userDTO.setUserNickName(user.getUserNickName());
        userDTO.setUserHead(user.getUserHead());
        userDTO.setPage(user.getPage());
        userDTO.setRows(user.getRows());


    }

}
