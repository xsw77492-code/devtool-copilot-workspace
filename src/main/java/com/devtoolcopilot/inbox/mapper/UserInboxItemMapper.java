package com.devtoolcopilot.inbox.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.devtoolcopilot.inbox.entity.UserInboxItem;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserInboxItemMapper extends BaseMapper<UserInboxItem> {
}

