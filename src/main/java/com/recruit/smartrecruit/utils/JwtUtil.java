package com.recruit.smartrecruit.utils;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import java.util.Date;
import java.util.Map;
public class JwtUtil {
    private static final String KEY="smartrecruitjwt";

    // 生成 Token
    public static String generateToken(  Map<String, Object> claims){
        //生成jwt
        //链式编程调用方法
        return  JWT.create()
                .withClaim("claims",claims)//添加载荷
                .withExpiresAt(new Date(System.currentTimeMillis()+1000*60*60*12))//添加过期时间
                .sign(Algorithm.HMAC256(KEY));//指定算法，配置密钥

    }


    // 解析校验 Token（是否有效）,并返回业务数据
    public static Map<String, Object> parseToken(String token){
        return JWT.require(Algorithm.HMAC256(KEY))
                   .build()
                   .verify(token)//验证token,生成一个解析后的JWT对象
                   .getClaim("claims")
                   .asMap();
    }




}
