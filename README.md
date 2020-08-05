# 基于Spring原生注解的Nacos配置动态刷新

```
@Value("${test.id:1}")
@NacosRereshScope
private String id;
```

```
@ConfigurationProperties(prefix = "test.conf")
@NacosRereshScope
public class TestProperties(){}
```
