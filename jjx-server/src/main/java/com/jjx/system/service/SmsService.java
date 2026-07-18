package com.jjx.system.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Random;
import java.util.concurrent.TimeUnit;


@Slf4j
@Service
@RequiredArgsConstructor
public class SmsService {

    private final StringRedisTemplate redisTemplate;
    private static final String SMS_CODE_PREFIX = "sms:code:";

    public void sendCode(String phone) {
        String code = String.format("%06d", new Random().nextInt(1000000));
        redisTemplate.opsForValue().set(SMS_CODE_PREFIX + phone, code, 5, TimeUnit.MINUTES);
        log.info("发送短信验证码: phone={}, code={}", phone, code);
    }

    public boolean validateCode(String phone, String code) {
        String savedCode = redisTemplate.opsForValue().get(SMS_CODE_PREFIX + phone);
        if (savedCode != null && savedCode.equals(code)) {
            redisTemplate.delete(SMS_CODE_PREFIX + phone);
            return true;
        }
        return false;
    }
}