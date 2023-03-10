# SimpleBeans
Spring-like beans with **proxy** and **simple aspects**.

# I. Create application
To create application, you just need annotate class with `@Application` and call `SimpleApplication.run` method:

```java
@Application
public class TestApplication {

    public static void main(String[] args) {
        ApplicationContext context = SimpleApplication.run(TestApplication.class);
    }
}
```

# II. Create beans

Beans can be created in two ways:
* Components

```java
@Component
public class SomeConnection {

    private final SomeBean someBean;
    private final SomeBean someBean2;
    @Autowired(id = "some-id")
    private SomeBean someBean3;
    private SomeBean someBean4;

    public SomeConnection(SomeBean someBean, @Bean(id = "another-id") SomeBean someBean2) {
        this.someBean = someBean;
        this.someBean2 = someBean2;
    }

    @Autowired(id = "id-4")
    public void setSomeBeans(SomeBean someBean4) {
        this.someBean4 = someBean4;
    }
}
```
Also, there is `@Service` and `@Repository` components like in Spring.

* Configurations

```java
@Configuration
public class SomeConfiguration {

    @Bean
    public String someString(SomeBean someBean) {
        return "string<" + someBean + ">";
    }

    @Bean
    public FooBean fooBean() {
        return new FooBean() {};
    }

    @Bean(id = "foo")
    public String fooBean(FooBean fooBean) {
        return "foo-string<" + fooBean + ">";
    }
}
```

# III. Create aspects
All beans and return values of their methods are proxied *(even the objects)*, so there exists aspects.
```java
@Aspect
public class ConnectionAspect {

    @Before("java.sql.Connection.executeUpdate")
    public void aspect(Method method, Object[] args) {
        args[0] = ((String) args[0]).repeat(2);
    }

    @After("java.sql.Statement.executeUpdate")
    public int aspect(Object result) {
        return ((Integer) result) * 4;
    }

}
```

In aspect there are several types of arguments can be used:
* `Method` — method that was called
* `Class` — non-proxied class
* `Object` — result *(can be used only in `@After` point cut)*
* `Object[]` — arguments *(if they are changed in `@Before`)*, then they will be changed before method call

Masks are really simple now, because they work only with exactly class and method.

# TODO list:
* Smarter masks
* Add ability to run code in IntelliJ IDEA without compiling
