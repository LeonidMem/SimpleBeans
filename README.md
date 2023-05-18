# SimpleBeans
Spring-like beans with **proxy** and **simple aspects**.


# Importing

* Maven:
```xml
<repositories>
  <repository>
    <id>smashup-repository</id>
    <name>SmashUp Repository</name>
    <url>https://mvn.smashup.ru/releases</url>
  </repository>
</repositories>

<dependencies>
  <dependency>
    <groupId>ru.leonidm</groupId>
    <artifactId>SimpleBeans</artifactId>
    <version>1.0.1</version>
  </dependency>
</dependencies>
```

* Gradle:
```groovy
repositories {
  maven { url 'https://mvn.smashup.ru/releases' }
}

dependencies {
  implementation 'ru.leonidm:SimpleBeans:1.0.1'
}
```


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

    @Before("java.sql.Connection.execute**")
    public void aspect(@Origin Method method, @Args Object[] args) {
        args[0] = ((String) args[0]).repeat(2);
    }

    @After("java.sql.Statement.execute**")
    public int aspect(@This Object instance, @Result Object result) {
        return ((Integer) result) * 4;
    }

}
```

In aspect there are several types of parameters that can be used:
* `@Origin Method` — non-proxied method that was called
* `@Args Object[]` — arguments *(if they are changed in `@Before`)*, then they will be changed before method call
* `@Result Object` — result *(can be used only in `@After` point cut)*
* `@Instance Object` — proxied instance

### More about pointcuts' masks
Masks are divided in three parts:
* Class
* Method
* Arguments

So, mask `ru.leonidm.foo.Bar(java.lang.String)` will be divided to:
* Class `ru.leonidm.foo`
* Method `Bar`
* Arguments `java.lang.String`

---

Also, there are some wildcards:
* For classes and classes of arguments:
  * `**` — any amount of packages _(even zero)_
  * `*` — any amount of symbols except dots _(even zero)_
* For methods:
  * `*` — any amount of symbols except dots _(even zero)_
* For full section of arguments:
  * `(*)` or `(...)` — any amount of arguments


### Example of masks:
* `**.*(*)` — any class, any method, any amount of arguments
* `**.*` — any class, any method, any amount of arguments
* `**.*()` — any class, any method, zero arguments
* `**.*(java.lang.String, java.util.List)` — any class, any method, one `String` and one `List` arguments
* `**.*(**String*)` — any class, any method, one argument with class with name `/any amount of packages/.String/any symbols except dot/`
* `net.**.foo.*(java.lang.String)` — any class with name `net./any amount of packages/.foo`, any method, one `String` argument
* `net.**bar.foo.*(java.lang.String)` — any class with name `net./any amount of packages, but last ends with 'bar'/.foo`, any method, one `String` argument
* `net.*.foo.*(java.lang.String)` — any class with name `net./one any package/.foo`, any method, one `String` argument
* `net.*bar.foo.*(java.lang.String)` — any class with name `net./one any package, but ends with bar/.foo`, any method, one `String` argument
* `net.foo.bar.get*` — class with name `net.foo.bar`, any method with name `get/any symbols/`, any amount of arguments
