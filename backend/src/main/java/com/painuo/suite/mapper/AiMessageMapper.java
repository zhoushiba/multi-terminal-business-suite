package com.painuo.suite.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.painuo.suite.entity.AiMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AiMessageMapper extends BaseMapper<AiMessage> {

    /** 某会话最近 N 条消息（倒序取再翻转，由调用方处理）。 */
    @Select("""
            SELECT * FROM ai_message
             WHERE conversation_id = #{conversationId}
             ORDER BY id DESC
             LIMIT #{limit}
            """)
    List<AiMessage> selectRecent(Long conversationId, int limit);
}
