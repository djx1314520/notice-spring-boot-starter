package club.bigtian.notice.aop;

import club.bigtian.notice.anno.NoticeMessage;
import club.bigtian.notice.config.ExceptionNoticeConfig;
import club.bigtian.notice.constant.SystemConstant;
import club.bigtian.notice.domain.TExceptionInfo;
import club.bigtian.notice.service.ExceptionInfoService;
import club.bigtian.notice.service.INoticeService;
import club.bigtian.notice.service.ISystemCacheService;
import club.bigtian.notice.utils.ExceptionUtils;
import club.bigtian.notice.utils.RequestUtils;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.*;

/**
 * @author bigtian
 */
@Aspect
@Component
@Slf4j
public class ExceptionHandlerAdvice {
    @Autowired
    private ExceptionInfoService exceptionInfoService;

    @Autowired
    private INoticeService noticeService;
    @Autowired
    private ExceptionNoticeConfig config;

    @Autowired
    private ISystemCacheService isystemCacheService;

    /**
     * 配置切点
     */
    @Pointcut(SystemConstant.PACKET)
    public void expCheckPoint() {

    }

    /**
     * 抛出异常后
     *
     * @param point
     * @param ex
     */
    @AfterThrowing(pointcut = "expCheckPoint()", throwing = "ex")
    public void doAfterThrowing(JoinPoint point, Exception ex) {
        //排除特定的异常/特定秒数内，同种异常不允许多次发送
        if (excludeException(ex) || isystemCacheService.expirationTime()) {
            return;
        }

        MethodSignature signature = (MethodSignature) point.getSignature();
        saveExceptionInfo(ex, point, signature.getMethod());
    }

    /**
     * 排除特定异常/包名异常
     *
     * @param ex
     * @return
     * @author bigtian
     * @since 6.0
     */
    private boolean excludeException(Exception ex) {
        String packageName = ex.getClass().getPackage().getName();
        Set<String> packageSet = Optional.ofNullable(config.getExcludePacket()).map(el -> new HashSet<>(Arrays.asList(el.split(",")))).orElse(new HashSet<>());
        //验证是否是排除的异常
        if (packageSet.contains(packageName)) {
            return true;
        }
        //排除特定异常
        String exceptionFullPath = ex.getClass().getName();

        Set<String> exceptionFullPathSet = Optional.ofNullable(config.getExcludeException()).map(el -> new HashSet<>(Arrays.asList(el.split(",")))).orElse(new HashSet<>());
        return exceptionFullPathSet.contains(exceptionFullPath);
    }

    /**
     * 保存错误信息到数据库
     *
     * @author bigtian
     * @since 6.0
     */
    private void saveExceptionInfo(Throwable e, JoinPoint point, Method method) {
        HttpServletRequest request = RequestUtils.getRequest();

        TExceptionInfo info = TExceptionInfo.builder()
                .url(request.getRequestURI())
                .createTime(new Date())
                .handled(SystemConstant.STATUS_N)
                .content(ExceptionUtils.getExceptionMsg(e))
                .params(JSON.toJSONString(point.getArgs()))
                .headers(getHeadersString())
                .build();

        int flag = exceptionInfoService.insert(info);
        //发送钉钉/企业微信群消息
        if (flag > 0) {
            noticeService.sendMessage(info, getAuthorList(method));
            //加入到计时器
            isystemCacheService.putVal();
        }
    }

    /**
     * 获取请求头
     *
     * @return
     * @author bigtian
     * @since 6.0
     */

    public String getHeadersString() {
        List<String> headersList = config.getHeaders();
        if (CollUtil.isEmpty(headersList)) {
            return "";
        }
        Map<String, Object> headers = new HashMap<>();
        HttpServletRequest request = RequestUtils.getRequest();
        for (String key : headersList) {
            String header = request.getHeader(key);
            if (StrUtil.isNotBlank(header)) {
                headers.put(key, header);
            }
        }
        return JSON.toJSONString(headers);
    }

    /**
     * 获取作者列表
     *
     * @param method
     * @return List<String>
     * @author bigtian
     * @since 6.0
     */
    public static List<String> getAuthorList(Method method) {
        List<String> list = new ArrayList<>();
        NoticeMessage noticeMessage = method.getAnnotation(NoticeMessage.class);
        if (Objects.nonNull(noticeMessage)) {
            list.addAll(Arrays.asList(noticeMessage.author()));
        }
        return list;
    }


}