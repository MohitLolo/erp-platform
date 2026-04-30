package com.erp.common.job.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

/**
 * XXL-JOB Executor 自动配置
 *
 * <p>仅在配置了 {@code xxl.job.admin.addresses} 时生效（{@code @ConditionalOnProperty} 保护），
 * 未配置该属性的服务引入此模块后不会注册 Executor Bean，正常启动不报错。
 *
 * <p>各业务服务只需在 {@code application.yml} 中配置：
 * <pre>
 * xxl:
 *   job:
 *     admin:
 *       addresses: http://xxl-job-admin.xxl-job.svc.cluster.local:8080/xxl-job-admin
 * </pre>
 * 其余配置项均有默认值，无需重复声明。
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "xxl.job", name = "admin.addresses")
public class XxlJobConfig {

    @Value("${xxl.job.admin.addresses}")
    private String adminAddresses;

    @Value("${xxl.job.accessToken:}")
    private String accessToken;

    /**
     * 默认取 spring.application.name，各服务无需手动配置 appname。
     */
    @Value("${xxl.job.executor.appname:${spring.application.name}}")
    private String appname;

    /**
     * 留空：框架自动取 Pod eth0 IP 上报给 Admin，适配 K8s 容器环境。
     */
    @Value("${xxl.job.executor.address:}")
    private String address;

    @Value("${xxl.job.executor.ip:}")
    private String ip;

    /**
     * 统一端口 9100，K8s 容器部署 Pod IP 不同，端口统一无冲突。
     */
    @Value("${xxl.job.executor.port:9100}")
    private int port;

    @Value("${xxl.job.executor.logpath:/data/applogs/xxl-job}")
    private String logPath;

    @Value("${xxl.job.executor.logretentiondays:30}")
    private int logRetentionDays;

    @Bean
    public XxlJobSpringExecutor xxlJobSpringExecutor() {
        XxlJobSpringExecutor executor = new XxlJobSpringExecutor();
        executor.setAdminAddresses(adminAddresses);
        executor.setAccessToken(accessToken);
        executor.setAppname(appname);
        if (StringUtils.hasText(address)) {
            executor.setAddress(address);
        }

        String resolvedIp = resolveExecutorIp();
        if (StringUtils.hasText(resolvedIp)) {
            executor.setIp(resolvedIp);
        }
        executor.setPort(port);
        executor.setLogPath(logPath);
        executor.setLogRetentionDays(logRetentionDays);

        log.info("XXL-JOB Executor initialized: appname={}, ip={}, port={}, admin={}",
                appname, resolvedIp, port, adminAddresses);
        return executor;
    }

    private String resolveExecutorIp() {
        if (StringUtils.hasText(ip)) {
            return ip.trim();
        }

        String podIp = System.getenv("POD_IP");
        if (isUsableIp(podIp)) {
            return podIp.trim();
        }

        String hostIp = System.getenv("HOST_IP");
        if (isUsableIp(hostIp)) {
            return hostIp.trim();
        }

        String localIp = findFirstSiteLocalIpv4();
        if (StringUtils.hasText(localIp)) {
            return localIp;
        }

        return null;
    }

    private boolean isUsableIp(String candidate) {
        if (!StringUtils.hasText(candidate)) {
            return false;
        }
        String ipValue = candidate.trim();
        return !"127.0.0.1".equals(ipValue) && !"0.0.0.0".equals(ipValue);
    }

    private String findFirstSiteLocalIpv4() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (!networkInterface.isUp() || networkInterface.isLoopback() || networkInterface.isVirtual()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress() && addr.isSiteLocalAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("Resolve XXL-JOB executor ip failed, fallback to framework default behavior", ex);
        }
        return null;
    }
}
