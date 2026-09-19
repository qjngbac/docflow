package com.docflow.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.docflow.entity.VerificationCode;
import com.docflow.mapper.VerificationCodeMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** 保存一次性验证码的摘要、用途和过期时间，验证成功后立即消费。 */
@Service
public class VerificationCodeStore {

    @Autowired private VerificationCodeMapper verificationCodeMapper;

    @Transactional
    public void replaceActiveCode(VerificationCode code) {
        LocalDateTime now = LocalDateTime.now();
        verificationCodeMapper.update(null, new LambdaUpdateWrapper<VerificationCode>()
                .eq(VerificationCode::getTarget, code.getTarget())
                .eq(VerificationCode::getPurpose, code.getPurpose())
                .isNull(VerificationCode::getUsedAt)
                .set(VerificationCode::getUsedAt, now));
        verificationCodeMapper.insert(code);
    }
}
