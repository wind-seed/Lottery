package com.windseed.lottery.interfaces.assembler;

import com.windseed.lottery.domain.activity.model.vo.ActivityVO;
import com.windseed.lottery.domain.strategy.model.vo.DrawAwardVO;
import com.windseed.lottery.rpc.activity.booth.dto.AwardDTO;
import com.windseed.lottery.rpc.activity.deploy.dto.ActivityDTO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 * @description: 活动对象转换配置
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE, unmappedSourcePolicy = ReportingPolicy.IGNORE)
public interface ActivityMapping extends IMapping<ActivityVO, ActivityDTO>{

    @Override
    List<ActivityDTO> sourceToTarget(List<ActivityVO> var1);

}
