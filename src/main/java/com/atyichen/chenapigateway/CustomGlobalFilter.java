package com.atyichen.chenapigateway;

import com.yichen.chenapiclientsdk.Utils.SignUtils;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**
 * 全局过滤
 */
@Slf4j
@Component
public class CustomGlobalFilter implements GlobalFilter, Ordered {
    /**
     *
     * @param exchange 路由交换机 所有的请求信息 响应信息都从这里拿
     * @param chain 责任链模式 所有过滤器从上到下依此执行 这个责任链放行就去找下一个责任链
     * @return
     */

    private static final List<String> IP_WHITE_LIST = Arrays.asList("127.0.0.1");
    @Override
    // Mono可以理解成前端的Promise
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 1. 用户发送请求到API网关 done

        // 2. 请求日志
        ServerHttpRequest request = exchange.getRequest();
        log.info("请求唯一标识：" + request.getId());
        log.info("请求路径： " + request.getPath().value());
        log.info("请求方法： " + request.getMethod());
        log.info("请求参数: "+ request.getQueryParams());
        log.info("请求来源地址" + request.getLocalAddress().getHostString());
        log.info("请求来源地址" + request.getRemoteAddress());

        ServerHttpResponse response = exchange.getResponse();
        // 3.黑白名单
        // 封IP
        if (!IP_WHITE_LIST.contains(request.getLocalAddress().getHostString())) {
            // 拒绝
            // 设置一下返回状态码 然后直接返回
            response.setStatusCode(HttpStatus.FORBIDDEN);
            // 直接返回 setComplete直接完成这次请求
            return response.setComplete();
        }

        // 4. 用户鉴权（判断ak sk是否合法）
//        String accessKey = request.getHeader("accessKey");
//        String body = request.getHeader("body");
//        String sign = request.getHeader("sign");
//        String nonce = request.getHeader("nonce");
//        String timestamp = request.getHeader("timestamp");
////        if (!accessKey.equals("kj") || !secretKey.equals("abcdefgh")) {
////            throw new RuntimeException("无权限");
////        }
//        // todo 实际情况应该是去数据库中查 是否已经分配给用户
//        if (!accessKey.equals("kj")) throw new RuntimeException("无权限");
//        // todo 这里的随机数应该是去数据库里面查的（主要是防重放）服务端要保存用过的随机数 这个随机数是新的 就能用？？
//        // todo 时间和当前时间不能超过5分钟
//
//        // 拼sign getSign方法 公用
//        String serverSign = SignUtils.getSign(body, "abcdefgh");
//        if (!serverSign.equals(sign)) throw new RuntimeException("无权限 签名错误");
//        String result = "POST2 你的名字是:"+ user.getName();
//        // 调用成功后 次数加1
//        // 1. 之前没调用，创建 2.之前调用了，调用次数加1
//
//        return result;
        HttpHeaders headers = request.getHeaders();
        String accessKey = headers.getFirst("accessKey");
        String body = headers.getFirst("body");
        String sign = headers.getFirst("sign");
        String nonce = headers.getFirst("nonce");
        String timestamp = headers.getFirst("timestamp");

        // todo 从数据库里面取出来sk
        String secretKey = "abcdefgh";
        if (!accessKey.equals("kj")) {
            return handleNoAuth(response);
        }

        String serverSign = SignUtils.getSign(body, secretKey);
        if (!serverSign.equals(sign)) return handleNoAuth(response);

        // 5. 判断请求的模拟接口是否存在
        // 模拟接口信息存在数据库中 判断数据库中是否有符合要求的接口

        // todo 从数据库中查询模拟接口是否存在
        // 方法1 ： 引入mapper mybatis 自己去查数据库
        // 方法2 ： 远程调用可以操作数据库的项目提供的接口 有点像微服务 注册中心？
        // 方法2可以用 ： 1.HTTP请求（用HTTPClient RestTemplate Feign） 2. RPC （Dubbo）

//        // 6. 请求转发 调用模拟接口 **这是一个异步操作**
//        Mono<Void> filter = chain.filter(exchange);


        // 注意 6执行完才去执行7 但是6是一个异步的方法
        // 7. 响应日志
//        log.info("响应， "+response.getStatusCode());
        return testResponseLog(exchange, chain);
//
//
//        // 调用失败 返回一个规范的错误码
//        if (response.getStatusCode() == HttpStatus.OK) {
//
//        }else {
//            return handleInvokeError(response);
//        }
//
//        // ServerWebExchange 里面有 request
//        log.info("custom global filter");
//        return filter;
    }

    /**
     * 处理响应
     * @param exchange
     * @param chain
     * @return
     */
    public Mono<Void> testResponseLog(ServerWebExchange exchange, GatewayFilterChain chain) {
        try {
            // 从交换机里拿到原本的response对象
            ServerHttpResponse originalResponse = exchange.getResponse();
            // 从response里拿到一个 缓冲区工厂
            DataBufferFactory bufferFactory = originalResponse.bufferFactory();
            // 拿响应码
            HttpStatus statusCode = originalResponse.getStatusCode();
            if (statusCode != HttpStatus.OK) {
                return chain.filter(exchange);//降级处理返回数据
            }
            // 拿到被装饰过的response对象（增强了能力）
            ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
                // 等调用完转发的接口后才会执行
                @Override
                public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                    if (body instanceof Flux) {
                        Flux<? extends DataBuffer> fluxBody = Flux.from(body);

                        // 往返回值里面写数据
                        return super.writeWith(
                                fluxBody.buffer().map(dataBuffers -> {
                                    // 调用成功 接口调用次数+1

                                    // 合并多个流集合，解决返回体分段传输
                                    DataBufferFactory dataBufferFactory = new DefaultDataBufferFactory();
                                    DataBuffer buff = dataBufferFactory.join(dataBuffers);
                                    byte[] content = new byte[buff.readableByteCount()];
                                    buff.read(content);
                                    DataBufferUtils.release(buff);//释放掉内存

                                    String data = new String(content, StandardCharsets.UTF_8);

                                    // 调用成功 接口次数+1 todo invokeCount
                                    // 怎么知道调用成功？

//                                    //排除Excel导出，不是application/json不打印。若请求是上传图片则在最上面判断。
//                                    MediaType contentType = originalResponse.getHeaders().getContentType();
//                                    if (!MediaType.APPLICATION_JSON.isCompatibleWith(contentType)) {
//                                        return bufferFactory.wrap(content);
//                                    }

                                    // 构建返回日志
                                    String joinData = new String(content);
//                            String result = modifyBody(joinData);
                                    String result = "zkjtest";
                                    List<Object> rspArgs = new ArrayList<>();
                                    rspArgs.add(originalResponse.getStatusCode().value());
                                    rspArgs.add(exchange.getRequest().getURI());
                                    rspArgs.add(result);

                                    // 打印日志
                                    log.info("响应结果： ", rspArgs.toArray());
                                    getDelegate().getHeaders().setContentLength(result.getBytes().length);
//                                    return bufferFactory.wrap(data.getBytes());
                                    return bufferFactory.wrap(content);
                                })

                        );
                    } else {
                        // 调用失败 返回一个规范的错误码
                        log.error("<-- {} 响应code异常", getStatusCode());
                    }
                    return super.writeWith(body);
                }
            };
            // 设置response对象为装饰过的
            // 转发调用模拟接口
            return chain.filter(exchange.mutate().response(decoratedResponse).build());

        } catch (Exception e) {
            log.error("gateway log exception.\n" + e);
            return chain.filter(exchange);
        }
    }
//    private String modifyBody(String jsonStr){
//        JSONObject json = JSON.parseObject(jsonStr, Feature.AllowISO8601DateFormat);
//        JSONObject.DEFFAULT_DATE_FORMAT = "yyyy-MM-dd HH:mm";
//        return JSONObject.toJSONString(json, (ValueFilter) (object, name, value) -> value == null ? "" : value, SerializerFeature.WriteDateUseDateFormat);
//    }

    public Mono<Void> handleNoAuth(ServerHttpResponse response) {
        response.setStatusCode(HttpStatus.FORBIDDEN);
        // 直接返回 setComplete直接完成这次请求
        return response.setComplete();
    }

    public Mono<Void> handleInvokeError(ServerHttpResponse response) {
        response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
        return response.setComplete();
    }
    @Override
    public int getOrder() {
        return -1;
    }
}