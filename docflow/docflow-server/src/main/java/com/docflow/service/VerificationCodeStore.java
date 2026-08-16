package com.docflow.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.docflow.entity.VerificationCode;
import com.docflow.mapper.VerificationCodeMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

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
